package com.eventpilot.application.proposal;

import com.eventpilot.application.command.EventCommand;
import com.eventpilot.application.command.MacroCommand;
import com.eventpilot.domain.DomainException;
import java.util.ArrayList;
import java.util.List;

/**
 * What the agent proposes: a list of commands with reasons, plus guardrail
 * warnings. Nothing changes until the organizer approves (all, a selection, or -
 * for alternatives such as venues - exactly one).
 */
public class PlanProposal {

    /** One proposed change and why. */
    public record Change(EventCommand command, String reason) {
        public String describe() {
            return command.describe();
        }
    }

    private final String eventId;
    private final String title;
    private final String summary;
    private final List<Change> changes;
    private final boolean alternatives;
    private final List<Warning> warnings = new ArrayList<>();
    private final List<String> trace = new ArrayList<>();
    private final String question;

    public PlanProposal(String eventId, String title, String summary, List<Change> changes, boolean alternatives) {
        this(eventId, title, summary, changes, alternatives, null);
    }

    private PlanProposal(String eventId, String title, String summary, List<Change> changes, boolean alternatives,
                         String question) {
        this.eventId = eventId;
        this.title = title;
        this.summary = summary == null ? "" : summary;
        this.changes = List.copyOf(changes);
        this.alternatives = alternatives;
        this.question = question;
    }

    /** The agent needs more information instead of proposing changes. */
    public static PlanProposal question(String eventId, String title, String question) {
        return new PlanProposal(eventId, title, "", List.of(), false, question);
    }

    public MacroCommand selectAll() {
        return select(rangeAll());
    }

    /** Builds one undoable unit from the chosen changes (0-based indexes). */
    public MacroCommand select(List<Integer> indexes) {
        if (isQuestion()) {
            throw new DomainException("This proposal is a question, not a change: " + question);
        }
        if (indexes == null || indexes.isEmpty()) {
            throw new DomainException("No changes selected.");
        }
        if (alternatives && indexes.size() != 1) {
            throw new DomainException("Choose exactly one of the " + changes.size() + " alternatives.");
        }
        List<EventCommand> chosen = new ArrayList<>();
        for (int i : indexes) {
            if (i < 0 || i >= changes.size()) {
                throw new DomainException("No change number " + (i + 1) + ".");
            }
            chosen.add(changes.get(i).command());
        }
        return new MacroCommand(title, chosen);
    }

    private List<Integer> rangeAll() {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            all.add(i);
        }
        return all;
    }

    public void addWarnings(List<Warning> more) {
        warnings.addAll(more);
    }

    public void setWarnings(List<Warning> newWarnings) {
        warnings.clear();
        warnings.addAll(newWarnings);
    }

    public void addTrace(List<String> lines) {
        trace.addAll(lines);
    }

    public boolean hasBlockingWarnings() {
        return warnings.stream().anyMatch(Warning::isBlocking);
    }

    public boolean isQuestion() {
        return question != null;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public String eventId() { return eventId; }
    public String title() { return title; }
    public String summary() { return summary; }
    public List<Change> changes() { return changes; }
    public boolean alternatives() { return alternatives; }
    public List<Warning> warnings() { return List.copyOf(warnings); }
    public List<String> trace() { return List.copyOf(trace); }
    public String question() { return question; }
}
