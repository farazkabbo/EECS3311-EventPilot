package com.eventpilot.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.EventPilotApp;
import com.eventpilot.agent.llm.MockLLMProvider;
import com.eventpilot.agent.offline.OfflinePlanner;
import com.eventpilot.agent.workflow.FaqAnswer;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventTask;
import com.eventpilot.domain.state.EventStatus;
import com.eventpilot.domain.state.LifecycleAction;
import com.eventpilot.infra.AppConfig;
import com.eventpilot.infra.SqliteEventRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventPlannerFacadeTest {

    @TempDir
    Path tmp;

    /** A fresh application on the same database = a new CLI process. */
    private EventPlannerFacade newProcess() {
        AppConfig config = AppConfig.of(Map.of("EVENTPILOT_DATA_DIR", tmp.toString(), "LLM_PROVIDER", "mock"));
        Clock clock = Clock.fixed(LocalDate.of(2026, 11, 12).atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        return EventPilotApp.bootstrap(config, new SqliteEventRepository(tmp.resolve("eventpilot.db")),
                new MockLLMProvider(new OfflinePlanner()), clock).facade();
    }

    @Test
    void endToEndThroughTheFacadeWithUndoAcrossProcesses() {
        EventPlannerFacade f = newProcess();
        EventPlannerFacade.CreateEventResult created = f.createEventFromDescription("Plan a robotics workshop for 60 people");
        assertTrue(created.needsAnswers());
        created = f.answerClarification("It is on December 12 from 10am to 4pm, budget $5000, in Toronto");
        Event event = created.event();
        assertNotNull(event, created.message() + " " + created.questions());
        String id = event.id();

        // attendees via CSV (F02)
        f.importAttendees(id, Path.of("samples/attendees.csv"));
        assertEquals(12, f.getEvent(id).attendees().size());

        // venue (F03): pick option 1
        PlanProposal venues = f.recommendVenues(id, "");
        f.applyProposal(venues, List.of(0));
        assertNotNull(f.getEvent(id).venue());

        // agenda (F04) applied as ONE undoable step
        f.applyProposal(f.generateAgenda(id, null), null);
        int items = f.getEvent(id).agenda().size();
        assertTrue(items > 3);

        // a new process can undo it (history persisted)
        EventPlannerFacade second = newProcess();
        assertTrue(second.undo(id).orElseThrow().startsWith("Generate agenda"));
        assertTrue(second.getEvent(id).agenda().isEmpty());
        assertTrue(newProcess().redo(id).isPresent());
        assertEquals(items, newProcess().getEvent(id).agenda().size());

        // lifecycle: invitations need PLANNING (State pattern)
        assertThrows(DomainException.class, () -> f.draftInvitations(id, "friendly"));
        f.transitionEvent(id, LifecycleAction.START_PLANNING);
        assertEquals(11, f.draftInvitations(id, "friendly").size(), "declined attendee skipped");
        EventPlannerFacade.DeliveryReport report = f.sendInvitations(id);
        assertEquals(1, report.failed().size(), "the .invalid address fails, the rest are sent");
        assertTrue(Files.exists(tmp.resolve("outbox")));

        // FAQ (F11): answered from sources, or escalated with an alert
        FaqAnswer answer = f.askFaq(id, "Where is the venue?");
        assertFalse(answer.escalated());
        assertTrue(f.askFaq(id, "Can I bring my dog?").escalated());
        assertTrue(f.alerts(id).stream().anyMatch(a -> a.category().equals("FAQ")));

        // budget (F07) and tasks (F09)
        f.applyProposal(f.generateBudget(id), null);
        f.recordExpense(id, "venue", 100, null, "deposit");
        assertEquals(100, f.budgetStatus(id).totalSpent(), 0.001);
        f.applyProposal(f.generateTasks(id, List.of("Sam")), null);
        EventTask blocked = f.getEvent(id).tasks().stream().filter(t -> !t.dependsOn().isEmpty()).findFirst().orElseThrow();
        assertThrows(DomainException.class, () -> f.updateTaskStatus(id, blocked.id(), EventTask.Status.DONE));

        // confirm (UC15)
        f.transitionEvent(id, LifecycleAction.CONFIRM);
        assertEquals(EventStatus.CONFIRMED, f.getEvent(id).status());
    }

    @Test
    void manualCreateRsvpAndReadOnlyAfterCancel() {
        EventPlannerFacade f = newProcess();
        Event e = f.createEvent("Gala", "gala", LocalDate.of(2026, 12, 20).atTime(18, 0),
                LocalDate.of(2026, 12, 20).atTime(23, 0), 100, 20000, "Toronto");
        f.addAttendee(e.id(), "Ann", "ann@x.com", Attendee.Role.VIP, List.of("vegan"), "", Attendee.Rsvp.INVITED);
        f.updateRsvp(e.id(), "ann@x.com", Attendee.Rsvp.ACCEPTED);
        assertEquals(1, f.cateringSummary(e.id()).headcount().count());
        f.transitionEvent(e.id(), LifecycleAction.CANCEL);
        assertThrows(DomainException.class, () -> f.updateRsvp(e.id(), "ann@x.com", Attendee.Rsvp.DECLINED));
        assertThrows(DomainException.class, () -> f.recommendVenues(e.id(), ""));
    }

    @Test
    void nonEventRequestIsDeclined() {
        EventPlannerFacade.CreateEventResult r = newProcess().createEventFromDescription("what's the weather?");
        assertTrue(r.event() == null && !r.needsAnswers());
    }
}
