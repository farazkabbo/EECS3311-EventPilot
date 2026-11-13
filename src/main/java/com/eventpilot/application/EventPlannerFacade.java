package com.eventpilot.application;

import com.eventpilot.agent.GuardrailValidator;
import com.eventpilot.agent.llm.LLMClient;
import com.eventpilot.agent.llm.UsageTracker;
import com.eventpilot.agent.memory.MemoryManager;
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
import com.eventpilot.agent.workflow.PlanningWorkflow;
import com.eventpilot.agent.workflow.SpeakerSchedulingWorkflow;
import com.eventpilot.agent.workflow.TaskDecompositionWorkflow;
import com.eventpilot.agent.workflow.VenueRecommendationWorkflow;
import com.eventpilot.agent.workflow.WorkflowException;
import com.eventpilot.agent.workflow.WorkflowResult;
import com.eventpilot.application.command.AddTaskCommand;
import com.eventpilot.application.command.AssignSpeakerCommand;
import com.eventpilot.application.command.CommandHistory;
import com.eventpilot.application.command.EventCommand;
import com.eventpilot.application.command.MacroCommand;
import com.eventpilot.application.command.SetBudgetLineCommand;
import com.eventpilot.application.command.SetCateringPlanCommand;
import com.eventpilot.application.command.SetVenueCommand;
import com.eventpilot.application.event.Alert;
import com.eventpilot.application.event.AlertService;
import com.eventpilot.application.event.DomainEvent;
import com.eventpilot.application.event.EventBus;
import com.eventpilot.application.event.EventObserver;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Budget;
import com.eventpilot.domain.Conflict;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventDraft;
import com.eventpilot.domain.EventTask;
import com.eventpilot.domain.Invitation;
import com.eventpilot.domain.Room;
import com.eventpilot.domain.Speaker;
import com.eventpilot.domain.TimeWindow;
import com.eventpilot.domain.service.BudgetTracker;
import com.eventpilot.domain.service.CateringCalculator;
import com.eventpilot.domain.state.LifecycleAction;
import com.eventpilot.infra.AttendeeCsvImporter;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Facade pattern - the single entry point for every user goal. The GUI
 * controllers and the CLI commands call only this class, so both interfaces
 * offer the same functions and every change follows the same sequence:
 * load -> state check -> (agent proposal -> review) -> apply as command -> save -> notify.
 */
public class EventPlannerFacade {

    private static final String GLOBAL = "_global";
    private static final String PENDING_DESCRIPTION = "pending_event_description";
    private static final String ESCALATIONS = "faq.escalations";

    /** Result of F01: either a created event, or questions to answer, or a message (not an event request). */
    public record CreateEventResult(Event event, List<String> questions, String message) {
        public boolean needsAnswers() {
            return event == null && !questions.isEmpty();
        }
    }

    public record DeliveryReport(List<String> sent, List<String> failed) {
        public String summary() {
            return sent.size() + " sent, " + failed.size() + " failed";
        }
    }

    public record CateringSummary(CateringCalculator.Headcount headcount, Map<String, Integer> diets) {
    }

    private final EventRepository repository;
    private final VenueCatalog venues;
    private final MessageGateway gateway;
    private final AttendeeCsvImporter importer;
    private final AgentServices agent;
    private final CommandHistory history;
    private final EventBus bus;
    private final AlertService alerts;
    private final LLMClient llm;
    private final Set<String> historyLoaded = new HashSet<>();

    public EventPlannerFacade(EventRepository repository, VenueCatalog venues, MessageGateway gateway,
                              AttendeeCsvImporter importer, AgentServices agent, CommandHistory history,
                              EventBus bus, AlertService alerts, LLMClient llm) {
        this.repository = repository;
        this.venues = venues;
        this.gateway = gateway;
        this.importer = importer;
        this.agent = agent;
        this.history = history;
        this.bus = bus;
        this.alerts = alerts;
        this.llm = llm;
    }

    // =====================================================================
    // Events and lifecycle (F01, UC15)
    // =====================================================================

    public List<Event> listEvents() {
        return repository.findAll();
    }

    public Event getEvent(String id) {
        return repository.findById(id).orElseThrow(() -> new DomainException("No event with id " + id));
    }

    public synchronized Event createEvent(String name, String type, LocalDateTime start, LocalDateTime end,
                                          int attendance, double budget, String city) {
        Event event = new Event(name, type, start, end, attendance, budget, city);
        repository.save(event);
        bus.publish(DomainEvent.of(DomainEvent.Type.EVENT_CREATED, event, "created manually"));
        return event;
    }

    /** F01: plain-language description -> event (or follow-up questions). */
    public CreateEventResult createEventFromDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new DomainException("Describe the event first.");
        }
        repository.putValue(GLOBAL, PENDING_DESCRIPTION, description.trim());
        return runExtraction(description.trim());
    }

    /** F01: answer to the agent's follow-up questions; combines it with the pending description. */
    public CreateEventResult answerClarification(String answer) {
        String pending = repository.getValue(GLOBAL, PENDING_DESCRIPTION)
                .orElseThrow(() -> new DomainException("There is no event description waiting for answers."));
        String combined = pending + "\nAdditional details from the organizer: " + answer.trim();
        repository.putValue(GLOBAL, PENDING_DESCRIPTION, combined);
        return runExtraction(combined);
    }

    public Optional<String> pendingDescription() {
        return repository.getValue(GLOBAL, PENDING_DESCRIPTION);
    }

    public void cancelPendingDescription() {
        repository.removeValue(GLOBAL, PENDING_DESCRIPTION);
    }

    private CreateEventResult runExtraction(String text) {
        EventExtractionWorkflow.Extraction x = new EventExtractionWorkflow(agent, text).execute().value();
        x.preferences().forEach((k, v) -> agent.memory().setPreference(k, v));
        if (!x.eventRequest()) {
            cancelPendingDescription();
            return new CreateEventResult(null, List.of(), x.message().isBlank()
                    ? "That doesn't look like an event to plan." : x.message());
        }
        EventDraft draft = x.draft();
        List<String> questions = new ArrayList<>(agent.guardrails().validateDraft(draft));
        if (!questions.isEmpty()) {
            for (String q : x.questions()) {
                if (!questions.contains(q)) {
                    questions.add(q);
                }
            }
            return new CreateEventResult(null, questions, "I need a few more details.");
        }
        Event event;
        synchronized (this) {
            event = draft.toEvent();
            repository.save(event);
        }
        cancelPendingDescription();
        agent.memory().remember(event.id(), "organizer", text);
        agent.memory().remember(event.id(), "agent", "Created " + event);
        bus.publish(DomainEvent.of(DomainEvent.Type.EVENT_CREATED, event, "created from description"));
        return new CreateEventResult(event, List.of(), "Created \"" + event.name() + "\" (" + event.id() + ") as a draft.");
    }

    public synchronized void deleteEvent(String eventId) {
        getEvent(eventId);
        repository.delete(eventId);
        history.forget(eventId);
        bus.publish(new DomainEvent(DomainEvent.Type.EVENT_DELETED, eventId, null, ""));
    }

    /** UC15: lifecycle change; confirming also requires no serious conflicts and a plan within budget. */
    public synchronized Event transitionEvent(String eventId, LifecycleAction action) {
        Event event = getEvent(eventId);
        if (action == LifecycleAction.CONFIRM) {
            List<String> problems = new ArrayList<>();
            agent.conflicts().detect(event).stream().filter(Conflict::isBlocking)
                    .forEach(c -> problems.add("conflict: " + c.message()));
            BudgetTracker.BudgetStatus status = agent.budget().computeStatus(event);
            if (status.totalPlanned() > status.limit() + 0.01) {
                problems.add(String.format("the budget plan ($%.2f) exceeds the limit ($%.2f)", status.totalPlanned(), status.limit()));
            }
            if (!problems.isEmpty()) {
                throw new DomainException("Cannot confirm: " + String.join("; ", problems));
            }
        }
        event.transition(action);
        saveAndPublish(event, DomainEvent.Type.STATE_CHANGED, action.name());
        return event;
    }

    public synchronized Event updateEventDetails(String eventId, LocalDateTime start, LocalDateTime end, Integer attendance) {
        Event event = editable(eventId);
        if (start != null || end != null) {
            event.reschedule(start == null ? event.start() : start, end == null ? event.end() : end);
        }
        if (attendance != null) {
            event.setExpectedAttendance(attendance);
        }
        saveAndPublish(event, DomainEvent.Type.EVENT_CHANGED, "details");
        return event;
    }

    // =====================================================================
    // Attendees, speakers, rooms, notes (F02)
    // =====================================================================

    public synchronized Attendee addAttendee(String eventId, String name, String email, Attendee.Role role,
                                             List<String> dietary, String accessibility, Attendee.Rsvp rsvp) {
        Event event = editable(eventId);
        Attendee a;
        try {
            a = event.addAttendee(new Attendee(null, name, email, role, dietary, accessibility, rsvp));
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
        saveAndPublish(event, DomainEvent.Type.ATTENDEES_CHANGED, "added " + a.name());
        return a;
    }

    public synchronized AttendeeCsvImporter.ImportReport importAttendees(String eventId, Path csv) {
        Event event = editable(eventId);
        Set<String> existing = event.attendees().stream().map(Attendee::email).collect(Collectors.toSet());
        AttendeeCsvImporter.ImportReport report = importer.parse(csv, existing);
        report.imported().forEach(event::addAttendee);
        if (!report.imported().isEmpty()) {
            saveAndPublish(event, DomainEvent.Type.ATTENDEES_CHANGED, "imported " + report.imported().size());
        }
        return report;
    }

    public synchronized Attendee updateRsvp(String eventId, String attendeeIdOrEmail, Attendee.Rsvp rsvp) {
        Event event = editable(eventId);
        Attendee a = event.findAttendee(attendeeIdOrEmail)
                .orElseThrow(() -> new DomainException("No attendee " + attendeeIdOrEmail));
        a.updateRsvp(rsvp);
        saveAndPublish(event, DomainEvent.Type.ATTENDEES_CHANGED, a.name() + " -> " + rsvp);
        return a;
    }

    public synchronized void removeAttendee(String eventId, String attendeeIdOrEmail) {
        Event event = editable(eventId);
        Attendee a = event.findAttendee(attendeeIdOrEmail)
                .orElseThrow(() -> new DomainException("No attendee " + attendeeIdOrEmail));
        event.removeAttendee(a.id());
        saveAndPublish(event, DomainEvent.Type.ATTENDEES_CHANGED, "removed " + a.name());
    }

    public synchronized Speaker addSpeaker(String eventId, String name, String topic, String email, List<TimeWindow> availability) {
        Event event = editable(eventId);
        Speaker s = event.addSpeaker(new Speaker(null, name, topic, email, availability));
        saveAndPublish(event, DomainEvent.Type.AGENDA_CHANGED, "speaker " + s.name());
        return s;
    }

    public synchronized void setRooms(String eventId, List<Room> rooms) {
        Event event = editable(eventId);
        if (rooms.isEmpty()) {
            throw new DomainException("At least one room is required.");
        }
        event.setRooms(rooms);
        saveAndPublish(event, DomainEvent.Type.EVENT_CHANGED, "rooms");
    }

    public synchronized void addNote(String eventId, String note) {
        Event event = editable(eventId);
        event.addNote(note);
        saveAndPublish(event, DomainEvent.Type.EVENT_CHANGED, "note");
    }

    // =====================================================================
    // AI proposals (F03 - F09, F12) and review & apply (UC13)
    // =====================================================================

    public PlanProposal recommendVenues(String eventId, String extraNeeds) {
        return propose(new VenueRecommendationWorkflow(agent, editable(eventId), extraNeeds), eventId, "Recommend venues");
    }

    public PlanProposal generateAgenda(String eventId, AgendaPreferences preferences) {
        Event event = editable(eventId);
        Map<String, String> prefs = agent.memory().preferences();
        AgendaPreferences p = preferences;
        if (p == null) {
            p = new AgendaPreferences(1, 45, parseInt(prefs.get("break_minutes"), 15), "12:00", List.of(), true);
        }
        return propose(new AgendaGenerationWorkflow(agent, event, p), eventId, "Generate agenda");
    }

    public PlanProposal scheduleSpeakers(String eventId) {
        return propose(new SpeakerSchedulingWorkflow(agent, editable(eventId)), eventId, "Schedule speakers");
    }

    public List<Conflict> detectConflicts(String eventId) {
        return agent.conflicts().detect(getEvent(eventId));
    }

    public PlanProposal resolveConflicts(String eventId) {
        return propose(new ConflictResolutionWorkflow(agent, editable(eventId)), eventId, "Resolve conflicts");
    }

    public PlanProposal generateBudget(String eventId) {
        return propose(new BudgetPlanningWorkflow(agent, editable(eventId)), eventId, "Plan budget");
    }

    public PlanProposal planCatering(String eventId) {
        return propose(new CateringPlanningWorkflow(agent, editable(eventId)), eventId, "Plan catering");
    }

    public PlanProposal generateTasks(String eventId, List<String> team) {
        return propose(new TaskDecompositionWorkflow(agent, editable(eventId), team), eventId, "Generate tasks");
    }

    /** F12: plain-language change. Ambiguous requests come back as a proposal with a question. */
    public PlanProposal modifyPlan(String eventId, String instruction) {
        Event event = editable(eventId);
        PlanProposal p = propose(new PlanModificationWorkflow(agent, event, instruction), eventId, "Modify plan");
        agent.memory().remember(eventId, "organizer", instruction);
        agent.memory().remember(eventId, "agent", p.isQuestion() ? "asked: " + p.question()
                : p.summary() + " " + p.changes().stream().map(PlanProposal.Change::describe).collect(Collectors.joining("; ")));
        return p;
    }

    private PlanProposal propose(PlanningWorkflow<PlanProposal> workflow, String eventId, String what) {
        WorkflowResult<PlanProposal> result = workflow.execute();
        PlanProposal proposal = result.value();
        proposal.setWarnings(result.warnings());
        proposal.addTrace(result.traceLines());
        return proposal;
    }

    /**
     * UC13: applies the chosen changes as ONE undoable step. The proposal is re-checked
     * against the current event first (it may have changed while the organizer was reviewing).
     */
    public synchronized String applyProposal(PlanProposal proposal, List<Integer> selection) {
        Event event = editable(proposal.eventId());
        MacroCommand macro = selection == null ? proposal.selectAll() : proposal.select(selection);
        List<Warning> recheck = agent.guardrails().checkCommands(event, macro.steps());
        List<Warning> blocking = recheck.stream().filter(Warning::isBlocking).toList();
        if (!blocking.isEmpty()) {
            throw new DomainException("The proposal can no longer be applied: "
                    + blocking.stream().map(Warning::message).collect(Collectors.joining("; ")));
        }
        loadHistory(event.id());
        history.execute(event, macro);
        repository.saveHistory(event.id(), history.snapshot(event.id()));
        saveAndPublish(event, typeOf(macro), macro.describe());
        return "Applied: " + macro.describe();
    }

    public synchronized Optional<String> undo(String eventId) {
        Event event = editable(eventId);
        loadHistory(eventId);
        Optional<String> undone = history.undo(event);
        if (undone.isPresent()) {
            repository.saveHistory(eventId, history.snapshot(eventId));
            saveAndPublish(event, DomainEvent.Type.EVENT_CHANGED, "undo");
        }
        return undone;
    }

    public synchronized Optional<String> redo(String eventId) {
        Event event = editable(eventId);
        loadHistory(eventId);
        Optional<String> redone = history.redo(event);
        if (redone.isPresent()) {
            repository.saveHistory(eventId, history.snapshot(eventId));
            saveAndPublish(event, DomainEvent.Type.EVENT_CHANGED, "redo");
        }
        return redone;
    }

    public synchronized List<String> undoHistory(String eventId) {
        loadHistory(eventId);
        return history.undoDescriptions(eventId);
    }

    public synchronized boolean canUndo(String eventId) {
        loadHistory(eventId);
        return history.canUndo(eventId);
    }

    public synchronized boolean canRedo(String eventId) {
        loadHistory(eventId);
        return history.canRedo(eventId);
    }

    private void loadHistory(String eventId) {
        if (historyLoaded.add(eventId)) {
            history.restore(eventId, repository.loadHistory(eventId));
        }
    }

    private static DomainEvent.Type typeOf(MacroCommand macro) {
        EventCommand first = macro.steps().get(0);
        if (first instanceof SetBudgetLineCommand) {
            return DomainEvent.Type.BUDGET_CHANGED;
        }
        if (first instanceof SetCateringPlanCommand) {
            return DomainEvent.Type.CATERING_CHANGED;
        }
        if (first instanceof AddTaskCommand) {
            return DomainEvent.Type.TASKS_CHANGED;
        }
        if (first instanceof SetVenueCommand) {
            return DomainEvent.Type.EVENT_CHANGED;
        }
        if (first instanceof AssignSpeakerCommand) {
            return DomainEvent.Type.AGENDA_CHANGED;
        }
        return DomainEvent.Type.AGENDA_CHANGED;
    }

    // =====================================================================
    // Budget (F07)
    // =====================================================================

    public synchronized void setBudgetLimit(String eventId, double limit) {
        Event event = editable(eventId);
        event.budget().setLimit(limit);
        saveAndPublish(event, DomainEvent.Type.BUDGET_CHANGED, "limit");
    }

    public synchronized Budget.Expense recordExpense(String eventId, String category, double amount, LocalDate date,
                                                     String description) {
        Event event = editable(eventId);
        Budget.Expense expense = agent.budget().recordExpense(event, category, amount, date, description);
        saveAndPublish(event, DomainEvent.Type.BUDGET_CHANGED, "expense " + expense.id());
        return expense;
    }

    public BudgetTracker.BudgetStatus budgetStatus(String eventId) {
        return agent.budget().computeStatus(getEvent(eventId));
    }

    // =====================================================================
    // Catering (F08)
    // =====================================================================

    public CateringSummary cateringSummary(String eventId) {
        Event event = getEvent(eventId);
        return new CateringSummary(agent.catering().computeHeadcount(event), agent.catering().summarizeDiets(event));
    }

    // =====================================================================
    // Tasks (F09)
    // =====================================================================

    public synchronized EventTask addTask(String eventId, String title, String owner, LocalDate deadline, List<String> dependsOn) {
        Event event = editable(eventId);
        for (String d : dependsOn) {
            if (event.findTask(d).isEmpty()) {
                throw new DomainException("Unknown dependency " + d);
            }
        }
        EventTask task = new EventTask(event.nextTaskId(), title, owner, deadline, dependsOn, EventTask.Status.TODO);
        List<EventTask> all = new ArrayList<>(event.tasks());
        all.add(task);
        if (agent.tasks().detectCycles(all).isPresent()) {
            throw new DomainException("That dependency would create a cycle.");
        }
        event.addTask(task);
        saveAndPublish(event, DomainEvent.Type.TASKS_CHANGED, "task " + task.id());
        return task;
    }

    /** A task cannot be marked done while one of its dependencies is still open. */
    public synchronized EventTask updateTaskStatus(String eventId, String taskId, EventTask.Status status) {
        Event event = editable(eventId);
        EventTask task = event.findTask(taskId).orElseThrow(() -> new DomainException("No task " + taskId));
        if (status == EventTask.Status.DONE) {
            List<EventTask> open = agent.tasks().openDependencies(task, event.tasks());
            if (!open.isEmpty()) {
                throw new DomainException("\"" + task.title() + "\" is blocked by: "
                        + open.stream().map(t -> t.id() + " " + t.title()).collect(Collectors.joining(", ")));
            }
        }
        task.setStatus(status);
        saveAndPublish(event, DomainEvent.Type.TASKS_CHANGED, task.id() + " -> " + status);
        return task;
    }

    public List<EventTask> orderedTasks(String eventId) {
        return agent.tasks().orderByDependencies(getEvent(eventId).tasks());
    }

    // =====================================================================
    // Invitations (F10)
    // =====================================================================

    public List<Invitation> draftInvitations(String eventId, String tone) {
        Event event = editable(eventId);
        event.ensureCanInvite();
        WorkflowResult<List<Invitation>> result = new InvitationDraftingWorkflow(agent, event, tone).execute();
        if (result.hasBlocking()) {
            throw new WorkflowException("The drafts still miss required facts after regeneration: "
                    + result.warnings().stream().map(Warning::message).collect(Collectors.joining("; "))
                    + ". Please write the text manually or try again.");
        }
        synchronized (this) {
            Event fresh = editable(eventId);
            fresh.replaceInvitations(result.value());
            saveAndPublish(fresh, DomainEvent.Type.INVITATIONS_CHANGED, "drafted " + result.value().size());
        }
        return result.value();
    }

    public synchronized void editInvitation(String eventId, String email, String subject, String body) {
        Event event = editable(eventId);
        Invitation inv = event.invitations().stream().filter(i -> i.email().equalsIgnoreCase(email)).findFirst()
                .orElseThrow(() -> new DomainException("No invitation for " + email));
        inv.edit(subject, body);
        saveAndPublish(event, DomainEvent.Type.INVITATIONS_CHANGED, "edited " + email);
    }

    /** Sends every drafted (or previously failed) invitation; one failure does not stop the rest. */
    public synchronized DeliveryReport sendInvitations(String eventId) {
        Event event = editable(eventId);
        event.ensureCanInvite();
        List<String> sent = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Invitation inv : event.invitations()) {
            if (inv.status() == Invitation.Status.SENT) {
                continue;
            }
            try {
                gateway.send(inv);
                inv.markSent();
                sent.add(inv.email());
            } catch (MessageGateway.DeliveryException e) {
                inv.markFailed(e.getMessage());
                failed.add(inv.email() + " (" + e.getMessage() + ")");
            }
        }
        if (sent.isEmpty() && failed.isEmpty()) {
            throw new DomainException("There are no drafted invitations to send. Draft them first.");
        }
        saveAndPublish(event, DomainEvent.Type.INVITATIONS_CHANGED, sent.size() + " sent");
        return new DeliveryReport(sent, failed);
    }

    // =====================================================================
    // FAQ (F11)
    // =====================================================================

    public FaqAnswer askFaq(String eventId, String question) {
        Event event = getEvent(eventId);
        agent.knowledgeBase().index(event); // CLI runs are separate processes: make sure the index is current
        FaqAnswer answer;
        try {
            answer = new FaqAnswerWorkflow(agent, event, question).execute().value();
        } catch (WorkflowException e) {
            answer = FaqAnswer.escalated(question); // e.g. the answer kept citing unknown sources
        }
        if (answer.escalated()) {
            List<String> pending = new ArrayList<>(escalatedQuestions(eventId));
            pending.add(question.trim());
            repository.putValue(eventId, ESCALATIONS, Json.write(pending));
            bus.publish(DomainEvent.of(DomainEvent.Type.FAQ_ESCALATED, event, question.trim()));
        }
        return answer;
    }

    public List<String> escalatedQuestions(String eventId) {
        return repository.getValue(eventId, ESCALATIONS).map(json -> {
            try {
                return Json.MAPPER.readValue(json, new TypeReference<List<String>>() { });
            } catch (JsonProcessingException e) {
                return List.<String>of();
            }
        }).orElse(List.of());
    }

    public void clearEscalations(String eventId) {
        repository.removeValue(eventId, ESCALATIONS);
        alerts.clearEscalations(eventId);
        alerts.refresh(getEvent(eventId));
    }

    // =====================================================================
    // Alerts, preferences, AI info
    // =====================================================================

    public List<Alert> alerts(String eventId) {
        Event event = getEvent(eventId);
        alerts.restoreEscalations(eventId, escalatedQuestions(eventId));
        return alerts.refresh(event);
    }

    public Map<String, String> preferences() {
        return agent.memory().preferences();
    }

    public void setPreference(String key, String value) {
        agent.memory().setPreference(key, value);
    }

    public MemoryManager memory() {
        return agent.memory();
    }

    public VenueCatalog venueCatalog() {
        return venues;
    }

    public String aiDescription() {
        return llm.provider().name() + " (" + llm.provider().modelFor(com.eventpilot.agent.llm.LLMRequest.Tier.MAIN)
                + " / " + llm.provider().modelFor(com.eventpilot.agent.llm.LLMRequest.Tier.LIGHT) + ")";
    }

    public Map<String, UsageTracker.ModelUsage> usage() {
        return llm.usage().snapshot();
    }

    public GuardrailValidator guardrails() {
        return agent.guardrails();
    }

    public void subscribe(EventObserver observer) {
        bus.subscribe(observer);
    }

    public void unsubscribe(EventObserver observer) {
        bus.unsubscribe(observer);
    }

    // =====================================================================

    private Event editable(String eventId) {
        Event event = getEvent(eventId);
        event.ensureEditable();
        return event;
    }

    private void saveAndPublish(Event event, DomainEvent.Type type, String detail) {
        repository.save(event);
        bus.publish(DomainEvent.of(type, event, detail));
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
