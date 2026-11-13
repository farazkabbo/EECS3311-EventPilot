package com.eventpilot.agent.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.agent.llm.LLMResponse;
import com.eventpilot.agent.llm.MockLLMProvider;
import com.eventpilot.application.command.SetBudgetLineCommand;
import com.eventpilot.application.command.SetCateringPlanCommand;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Speaker;
import com.eventpilot.domain.TimeWindow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Workflow tests for F05, F06, F07 and F08 with scripted LLM replies. */
class ResourceFeaturesTest {

    static final LocalDateTime NINE = LocalDateTime.of(2026, 12, 5, 9, 0);

    private Event event() {
        Event e = new Event("Conf", "conference", NINE, NINE.plusHours(8), 40, 10000, "Toronto");
        e.agenda().add(new AgendaItem(null, "AI talk", AgendaItemType.SESSION, NINE, NINE.plusHours(1), "Main Hall", null, 40));
        e.agenda().add(new AgendaItem(null, "Lunch", AgendaItemType.MEAL, NINE.plusHours(3), NINE.plusHours(4), "Main Hall", null, 0));
        return e;
    }

    @Test
    void speakerSchedulingRequiresSpeakersBeforeCallingAi() {
        MockLLMProvider mock = new MockLLMProvider();
        assertThrows(WorkflowException.class, () -> new SpeakerSchedulingWorkflow(TestServices.with(mock), event()));
        assertTrue(mock.received().isEmpty());
    }

    @Test
    void speakerSchedulingFlagsUnavailableSpeaker() {
        Event e = event();
        e.addSpeaker(new Speaker(null, "Ada", "AI", null, List.of(new TimeWindow(NINE.plusHours(5), NINE.plusHours(7)))));
        MockLLMProvider mock = new MockLLMProvider(r -> LLMResponse.text(
                "{\"changes\": [{\"op\": \"assign_speaker\", \"item_id\": \"a1\", \"speaker_id\": \"s1\"}]}"));
        WorkflowResult<PlanProposal> r = new SpeakerSchedulingWorkflow(TestServices.with(mock), e).execute();
        assertTrue(r.hasBlocking(), "Ada is not available at 9:00");
    }

    @Test
    void conflictResolutionChecksFixOnCopy() {
        Event e = event();
        e.agenda().add(new AgendaItem(null, "Clash", AgendaItemType.SESSION, NINE, NINE.plusHours(1), "Main Hall", null, 30));
        MockLLMProvider mock = new MockLLMProvider().then(LLMResponse.text(
                "{\"summary\": \"move it\", \"changes\": [{\"op\": \"move\", \"item_id\": \"a3\", \"start\": \"10:00\"}]}"));
        WorkflowResult<PlanProposal> r = new ConflictResolutionWorkflow(TestServices.with(mock), e).execute();
        assertFalse(r.hasBlocking());
        assertEquals(NINE, e.agenda().require("a3").start(), "real event untouched");
    }

    @Test
    void conflictResolutionWithoutConflictsDoesNotCallAi() {
        assertThrows(WorkflowException.class,
                () -> new ConflictResolutionWorkflow(TestServices.with(new MockLLMProvider()), event()));
    }

    @Test
    void budgetAmountsAreComputedByCodeNotTheLlm() {
        MockLLMProvider mock = new MockLLMProvider().then(LLMResponse.text("""
                {"summary": "split", "allocation": [
                  {"category": "venue", "percent": 40, "rationale": "space"},
                  {"category": "catering", "percent": 50, "rationale": "food"},
                  {"category": "contingency", "percent": 10, "rationale": "safety"}]}"""));
        PlanProposal p = new BudgetPlanningWorkflow(TestServices.with(mock), event()).execute().value();
        SetBudgetLineCommand first = (SetBudgetLineCommand) p.changes().get(0).command();
        assertEquals(4000, first.line().amount(), 0.001);
    }

    @Test
    void budgetOver100PercentIsRepaired() {
        MockLLMProvider mock = new MockLLMProvider()
                .then(LLMResponse.text("{\"allocation\": [{\"category\": \"venue\", \"percent\": 80}, {\"category\": \"av\", \"percent\": 40}]}"))
                .then(LLMResponse.text("{\"allocation\": [{\"category\": \"venue\", \"percent\": 60}, {\"category\": \"av\", \"percent\": 40}]}"));
        PlanProposal p = new BudgetPlanningWorkflow(TestServices.with(mock), event()).execute().value();
        assertEquals(2, p.changes().size());
        assertTrue(mock.received().get(1).messages().get(2).text().contains("more than 100%"));
    }

    @Test
    void cateringMustCoverEveryDiet() {
        Event e = event();
        e.addAttendee(new Attendee(null, "A", "a@x.com", null, List.of("vegan"), null, Attendee.Rsvp.ACCEPTED));
        e.addAttendee(new Attendee(null, "B", "b@x.com", null, List.of("halal"), null, Attendee.Rsvp.ACCEPTED));
        String missingHalal = "{\"meals\": [{\"name\": \"Lunch\", \"meal_type\": \"lunch\", \"time\": \"12:00\", "
                + "\"menu\": [{\"name\": \"Salad\", \"suitable_for\": [\"vegan\"]}]}]}";
        String covered = "{\"meals\": [{\"name\": \"Lunch\", \"meal_type\": \"lunch\", \"time\": \"12:00\", "
                + "\"menu\": [{\"name\": \"Salad\", \"suitable_for\": [\"vegan\"]}, {\"name\": \"Chicken\", \"suitable_for\": [\"halal\"]}]}]}";
        MockLLMProvider mock = new MockLLMProvider().then(LLMResponse.text(missingHalal)).then(LLMResponse.text(covered));
        WorkflowResult<PlanProposal> r = new CateringPlanningWorkflow(TestServices.with(mock), e).execute();
        assertFalse(r.hasBlocking());
        SetCateringPlanCommand cmd = (SetCateringPlanCommand) r.value().changes().get(0).command();
        assertEquals(3, cmd.plan().meals().get(0).portions(), "2 people + 5% buffer, rounded up");
        assertTrue(mock.received().get(1).messages().get(2).text().contains("halal"));
    }
}
