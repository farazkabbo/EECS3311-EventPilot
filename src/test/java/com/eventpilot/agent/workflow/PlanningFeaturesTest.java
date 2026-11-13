package com.eventpilot.agent.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.agent.llm.ChatMessage;
import com.eventpilot.agent.llm.LLMResponse;
import com.eventpilot.agent.llm.MockLLMProvider;
import com.eventpilot.application.command.RemoveAgendaItemCommand;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Event;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Workflow tests for F01, F03 and F04 with scripted LLM replies. */
class PlanningFeaturesTest {

    static final LocalDateTime NINE = LocalDateTime.of(2026, 12, 5, 9, 0);

    private Event event() {
        return new Event("AI Night", "meetup", NINE, NINE.plusHours(8), 120, 10000, "Toronto");
    }

    @Test
    void extractionReturnsDraftAndKeepsMissingFieldsNull() {
        MockLLMProvider mock = new MockLLMProvider().then(LLMResponse.text("""
                {"is_event_request": true, "name": "AI Night", "type": "meetup", "start": "2026-12-05T18:00",
                 "end": null, "expected_attendance": 120, "budget": null, "city": "Toronto",
                 "questions": ["When does it end?"], "preferences": {"break_minutes": "15"}}"""));
        EventExtractionWorkflow.Extraction x = new EventExtractionWorkflow(TestServices.with(mock),
                "AI meetup for 120 people Dec 5 at 6pm").execute().value();
        assertTrue(x.eventRequest());
        assertEquals(LocalDateTime.of(2026, 12, 5, 18, 0), x.draft().start);
        assertNull(x.draft().end);
        assertTrue(x.draft().missingFields().contains("budget"));
        assertEquals("15", x.preferences().get("break_minutes"));
        assertTrue(mock.received().get(0).messages().get(0).text().contains("\"today\" : \"2026-11-12\""));
    }

    @Test
    void extractionRecognisesNonEventRequests() {
        MockLLMProvider mock = new MockLLMProvider().then(LLMResponse.text(
                "{\"is_event_request\": false, \"message\": \"I can only help plan events.\"}"));
        assertFalse(new EventExtractionWorkflow(TestServices.with(mock), "what is 2+2").execute().value().eventRequest());
    }

    @Test
    void venueWorkflowUsesToolsAndBlocksVenuesNotReturnedByTheTool() {
        MockLLMProvider mock = new MockLLMProvider()
                .then(LLMResponse.tools(List.of(new ChatMessage.ToolCall("c1", "venue_search",
                        "{\"city\": \"Toronto\", \"min_capacity\": 120, \"max_price_per_day\": 2500}"))))
                .then(LLMResponse.text("""
                        {"summary": "Two good fits", "recommendations": [
                          {"venue_id": "TO-006", "score": 90, "reason": "great space", "tradeoffs": "pricier"},
                          {"venue_id": "TO-004", "score": 60, "reason": "big", "tradeoffs": "over budget"}]}"""));
        WorkflowResult<PlanProposal> r = new VenueRecommendationWorkflow(TestServices.with(mock), event(), "").execute();
        PlanProposal p = r.value();
        assertTrue(p.alternatives());
        assertEquals(2, p.changes().size());
        assertEquals(1, r.trace().size());
        // TO-004 costs 6500/day: not returned by the price-limited search -> blocking warning for option 2
        assertTrue(r.warnings().stream().anyMatch(w -> w.isBlocking() && w.message().contains("Yonge Street")));
    }

    @Test
    void venueWorkflowRejectsInventedVenueIds() {
        MockLLMProvider mock = new MockLLMProvider()
                .then(LLMResponse.text("{\"recommendations\": [{\"venue_id\": \"FAKE-1\", \"score\": 99}]}"))
                .then(LLMResponse.text("{\"recommendations\": [{\"venue_id\": \"TO-007\", \"score\": 80}]}"));
        WorkflowResult<PlanProposal> r = new VenueRecommendationWorkflow(TestServices.with(mock), event(), "").execute();
        assertEquals(1, r.value().changes().size());
        assertTrue(mock.received().get(1).messages().get(2).text().contains("does not exist"));
    }

    @Test
    void agendaReplaceRemovesOldItemsFirst() {
        Event e = event();
        e.agenda().add(new AgendaItem(null, "Old talk", AgendaItemType.SESSION, NINE, NINE.plusHours(1), "Main Hall", null, 50));
        MockLLMProvider mock = new MockLLMProvider().then(LLMResponse.text("""
                {"summary": "New plan", "changes": [
                  {"op": "add", "title": "Welcome", "type": "keynote", "start": "09:00", "end": "09:45", "room": "Main Hall"},
                  {"op": "add", "title": "Coffee", "type": "break", "start": "09:45", "end": "10:00", "room": "Main Hall"}]}"""));
        WorkflowResult<PlanProposal> r = new AgendaGenerationWorkflow(TestServices.with(mock), e,
                AgendaPreferences.defaults()).execute();
        assertInstanceOf(RemoveAgendaItemCommand.class, r.value().changes().get(0).command());
        assertEquals(3, r.value().changes().size());
        assertTrue(r.warnings().isEmpty(), "new 09:00 keynote does not clash with the removed old talk");
    }
}
