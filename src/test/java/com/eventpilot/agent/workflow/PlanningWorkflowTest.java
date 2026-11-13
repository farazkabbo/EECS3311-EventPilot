package com.eventpilot.agent.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.agent.llm.LLMResponse;
import com.eventpilot.agent.llm.MockLLMProvider;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Event;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanningWorkflowTest {

    static final LocalDateTime NINE = LocalDateTime.of(2026, 12, 5, 9, 0);

    /** Minimal concrete workflow: only supplies prompt, parser and (via the default hook) guardrails. */
    static class AgendaTestWorkflow extends PlanningWorkflow<PlanProposal> {
        AgendaTestWorkflow(AgentServices s, Event e) {
            super(s, e);
        }

        @Override
        protected AgentTask buildTask(String context) {
            return new AgentTask("test", "system", "plan " + context, List.of("conflict_check"), LLMRequest.Tier.MAIN, 1000);
        }

        @Override
        protected ResponseParser<PlanProposal> createParser() {
            return new ProposalParser(services.mapper(), event, "Test");
        }

        @Override
        protected List<Warning> validate(PlanProposal result, AgentResult run) {
            return services.guardrails().validateProposal(event, result);
        }
    }

    private Event event() {
        Event e = new Event("Conf", "conference", NINE, NINE.plusHours(8), 50, 5000, "Toronto");
        e.agenda().add(new AgendaItem(null, "Keynote", AgendaItemType.KEYNOTE, NINE, NINE.plusHours(1), "Main Hall", null, 50));
        return e;
    }

    private static final String GOOD = "{\"summary\": \"ok\", \"changes\": [{\"op\": \"add\", \"title\": \"Talk\", "
            + "\"type\": \"session\", \"start\": \"10:00\", \"end\": \"11:00\", \"room\": \"Main Hall\"}]}";
    private static final String CLASH = "{\"changes\": [{\"op\": \"add\", \"title\": \"Clash\", "
            + "\"type\": \"session\", \"start\": \"9:00\", \"end\": \"10:00\", \"room\": \"Main Hall\"}]}";

    @Test
    void invalidJsonIsRepairedThenParsed() {
        MockLLMProvider mock = new MockLLMProvider().then(LLMResponse.text("Sure! Here you go")).then(LLMResponse.text(GOOD));
        WorkflowResult<PlanProposal> r = new AgendaTestWorkflow(TestServices.with(mock), event()).execute();
        assertEquals(1, r.value().changes().size());
        assertTrue(r.warnings().isEmpty());
        assertTrue(mock.received().get(1).messages().get(2).text().contains("could not be used"));
    }

    @Test
    void givesUpAfterTwoRepairs() {
        MockLLMProvider mock = new MockLLMProvider(req -> LLMResponse.text("still not json"));
        assertThrows(WorkflowException.class, () -> new AgendaTestWorkflow(TestServices.with(mock), event()).execute());
        assertEquals(3, mock.received().size());
    }

    @Test
    void unknownItemReferenceTriggersRepair() {
        MockLLMProvider mock = new MockLLMProvider()
                .then(LLMResponse.text("{\"changes\": [{\"op\": \"move\", \"item_id\": \"a42\", \"start\": \"14:00\"}]}"))
                .then(LLMResponse.text("{\"changes\": [{\"op\": \"move\", \"item_id\": \"a1\", \"start\": \"14:00\"}]}"));
        WorkflowResult<PlanProposal> r = new AgendaTestWorkflow(TestServices.with(mock), event()).execute();
        assertEquals(1, r.value().changes().size());
        assertTrue(mock.received().get(1).messages().get(2).text().contains("Unknown item_id"));
    }

    @Test
    void blockingConflictIsSentBackOnceForFix() {
        MockLLMProvider mock = new MockLLMProvider().then(LLMResponse.text(CLASH)).then(LLMResponse.text(GOOD));
        WorkflowResult<PlanProposal> r = new AgendaTestWorkflow(TestServices.with(mock), event()).execute();
        assertTrue(r.warnings().isEmpty());
        assertTrue(mock.received().get(1).messages().get(2).text().contains("blocking problems"));
    }

    @Test
    void persistentConflictIsReportedAsBlockingWarning() {
        MockLLMProvider mock = new MockLLMProvider(req -> LLMResponse.text(CLASH));
        WorkflowResult<PlanProposal> r = new AgendaTestWorkflow(TestServices.with(mock), event()).execute();
        assertTrue(r.hasBlocking());
    }

    @Test
    void clarifyingQuestionIsReturnedAsQuestion() {
        MockLLMProvider mock = new MockLLMProvider().then(LLMResponse.text("{\"question\": \"Which keynote?\"}"));
        WorkflowResult<PlanProposal> r = new AgendaTestWorkflow(TestServices.with(mock), event()).execute();
        assertTrue(r.value().isQuestion());
        assertEquals("Which keynote?", r.value().question());
    }
}
