package com.eventpilot.agent.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.agent.llm.MockLLMProvider;
import com.eventpilot.agent.workflow.AgendaGenerationWorkflow;
import com.eventpilot.agent.workflow.AgendaPreferences;
import com.eventpilot.agent.workflow.AgentServices;
import com.eventpilot.agent.workflow.BudgetPlanningWorkflow;
import com.eventpilot.agent.workflow.CateringPlanningWorkflow;
import com.eventpilot.agent.workflow.ConflictResolutionWorkflow;
import com.eventpilot.agent.workflow.EventExtractionWorkflow;
import com.eventpilot.agent.workflow.FaqAnswer;
import com.eventpilot.agent.workflow.FaqAnswerWorkflow;
import com.eventpilot.agent.workflow.InvitationDraftingWorkflow;
import com.eventpilot.agent.workflow.PlanModificationWorkflow;
import com.eventpilot.agent.workflow.SpeakerSchedulingWorkflow;
import com.eventpilot.agent.workflow.TaskDecompositionWorkflow;
import com.eventpilot.agent.workflow.TestServices;
import com.eventpilot.agent.workflow.VenueRecommendationWorkflow;
import com.eventpilot.agent.workflow.WorkflowResult;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventDraft;
import com.eventpilot.domain.Invitation;
import com.eventpilot.domain.Speaker;
import com.eventpilot.domain.TimeWindow;
import com.eventpilot.domain.state.LifecycleAction;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Every AI feature end-to-end through the agent loop, with the offline planner standing in for the LLM. */
class OfflinePlannerTest {

    static final LocalDateTime NINE = LocalDateTime.of(2026, 12, 5, 9, 0);
    private AgentServices services;
    private Event event;

    @BeforeEach
    void setUp() {
        services = TestServices.with(new MockLLMProvider(new OfflinePlanner()));
        event = new Event("AI Meetup", "meetup", NINE, NINE.plusHours(8), 120, 12000, "Toronto");
        event.addSpeaker(new Speaker(null, "Ada Park", "machine learning", null, List.of(new TimeWindow(NINE, NINE.plusHours(4)))));
        event.addSpeaker(new Speaker(null, "Ben Osei", "robotics", null, List.of()));
        event.addAttendee(new Attendee(null, "Ann", "ann@x.com", Attendee.Role.VIP, List.of("vegan"), null, Attendee.Rsvp.ACCEPTED));
        event.addAttendee(new Attendee(null, "Bo", "bo@x.com", Attendee.Role.GUEST, List.of("halal", "nut allergy"), null, Attendee.Rsvp.ACCEPTED));
    }

    private void apply(WorkflowResult<PlanProposal> r) {
        assertFalse(r.hasBlocking(), () -> "blocking warnings: " + r.warnings());
        r.value().selectAll().execute(event);
    }

    @Test
    void extractsEventFromDescription() {
        EventExtractionWorkflow.Extraction x = new EventExtractionWorkflow(services,
                "Plan an AI meetup for 120 people on December 5 from 9am to 5pm in Toronto, budget $12k. I like 10-minute breaks.")
                .execute().value();
        EventDraft d = x.draft();
        assertTrue(d.isComplete(), () -> "missing " + d.missingFields());
        assertEquals(LocalDateTime.of(2026, 12, 5, 9, 0), d.start);
        assertEquals(LocalDateTime.of(2026, 12, 5, 17, 0), d.end);
        assertEquals(12000, d.budget, 0.01);
        assertEquals("10", x.preferences().get("break_minutes"));
        assertTrue(d.name.contains("Meetup"));
    }

    @Test
    void missingInformationLeadsToQuestions() {
        EventDraft d = new EventExtractionWorkflow(services, "Organize a workshop next Friday").execute().value().draft();
        assertFalse(d.isComplete());
        assertFalse(services.guardrails().validateDraft(d).isEmpty());
    }

    @Test
    void fullPlanningFlowWorksOffline() {
        // F03 venue - tool loop with venue_search and budget_calculator
        WorkflowResult<PlanProposal> venue = new VenueRecommendationWorkflow(services, event, "").execute();
        assertEquals(2, venue.trace().size());
        assertTrue(venue.value().alternatives());
        assertFalse(venue.hasBlocking(), () -> venue.warnings().toString());
        venue.value().select(List.of(0)).execute(event);

        // F04 agenda (2 tracks) - checked with conflict_check
        WorkflowResult<PlanProposal> agenda = new AgendaGenerationWorkflow(services, event,
                new AgendaPreferences(2, 45, 15, "12:00", List.of("AI ethics", "LLM apps"), true)).execute();
        apply(agenda);
        assertTrue(event.agenda().size() > 6);
        assertTrue(services.conflicts().detect(event).stream().noneMatch(c -> c.isBlocking()),
                () -> services.conflicts().detect(event).toString());

        // F05 speakers
        apply(new SpeakerSchedulingWorkflow(services, event).execute());

        // F07 budget, F08 catering, F09 tasks
        apply(new BudgetPlanningWorkflow(services, event).execute());
        assertEquals(12000, event.budget().lines().stream().mapToDouble(l -> l.amount()).sum(), 0.5);
        apply(new CateringPlanningWorkflow(services, event).execute());
        assertTrue(event.cateringPlan().coveredDiets().containsAll(List.of("vegan", "halal", "nut allergy")));
        apply(new TaskDecompositionWorkflow(services, event, List.of("Sam", "Lee")).execute());
        assertFalse(event.tasks().isEmpty());

        // F12 plain-language change
        WorkflowResult<PlanProposal> change = new PlanModificationWorkflow(services, event,
                "move the opening keynote to 9:15 and add a 10-minute break after it").execute();
        assertFalse(change.value().isQuestion(), () -> change.value().question());
        assertEquals(2, change.value().changes().size());

        // F10 invitations contain the real facts
        event.transition(LifecycleAction.START_PLANNING);
        WorkflowResult<List<Invitation>> inv = new InvitationDraftingWorkflow(services, event, "playful").execute();
        assertFalse(inv.hasBlocking(), () -> inv.warnings().toString());
        assertTrue(inv.value().get(0).body().contains(event.venue().name()));

        // F11 FAQ from the knowledge base
        services.knowledgeBase().index(event);
        FaqAnswer food = new FaqAnswerWorkflow(services, event, "Is there vegan food at lunch?").execute().value();
        assertFalse(food.escalated());
        assertTrue(food.sources().get(0).startsWith("catering:") || food.sources().get(0).startsWith("agenda:"));
    }

    @Test
    void conflictResolutionMovesClashingItem() {
        event.agenda().add(new AgendaItem(null, "Talk A", AgendaItemType.SESSION, NINE, NINE.plusHours(1), "Main Hall", null, 100));
        event.agenda().add(new AgendaItem(null, "Talk B", AgendaItemType.SESSION, NINE, NINE.plusHours(1), "Main Hall", null, 100));
        apply(new ConflictResolutionWorkflow(services, event).execute());
        assertTrue(services.conflicts().detect(event).isEmpty());
    }

    @Test
    void ambiguousModificationAsksQuestion() {
        event.agenda().add(new AgendaItem(null, "Talk A", AgendaItemType.SESSION, NINE, NINE.plusHours(1), "Main Hall", null, 100));
        event.agenda().add(new AgendaItem(null, "Talk B", AgendaItemType.SESSION, NINE.plusHours(1), NINE.plusHours(2), "Main Hall", null, 100));
        PlanProposal p = new PlanModificationWorkflow(services, event, "move the talk to 3pm").execute().value();
        assertTrue(p.isQuestion());
        assertTrue(p.question().contains("Which one"));
    }
}
