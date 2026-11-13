package com.eventpilot.application.command;

import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Command pattern - the "Invoker". Executes commands and keeps one undo and one
 * redo stack per event. A new change after an undo clears the redo history.
 */
public class CommandHistory {

    public static final int MAX_DEPTH = 50;

    /** Serializable copy of one event's stacks (top of stack first). */
    public record Snapshot(List<EventCommand> undo, List<EventCommand> redo) {
        public static Snapshot empty() {
            return new Snapshot(List.of(), List.of());
        }
    }

    private final Map<String, Deque<EventCommand>> undoStacks = new HashMap<>();
    private final Map<String, Deque<EventCommand>> redoStacks = new HashMap<>();

    public void execute(Event event, EventCommand command) {
        event.ensureEditable();
        command.execute(event);
        Deque<EventCommand> undo = undoStack(event.id());
        undo.push(command);
        while (undo.size() > MAX_DEPTH) {
            undo.removeLast();
        }
        redoStack(event.id()).clear();
    }

    /** Undoes the latest change; returns its description, or empty when there is nothing to undo. */
    public Optional<String> undo(Event event) {
        Deque<EventCommand> undo = undoStack(event.id());
        if (undo.isEmpty()) {
            return Optional.empty();
        }
        event.ensureEditable();
        EventCommand command = undo.pop();
        try {
            command.undo(event);
        } catch (RuntimeException e) {
            undo.push(command);
            throw new DomainException("Could not undo \"" + command.describe() + "\": " + e.getMessage());
        }
        redoStack(event.id()).push(command);
        return Optional.of(command.describe());
    }

    public Optional<String> redo(Event event) {
        Deque<EventCommand> redo = redoStack(event.id());
        if (redo.isEmpty()) {
            return Optional.empty();
        }
        event.ensureEditable();
        EventCommand command = redo.pop();
        try {
            command.execute(event);
        } catch (RuntimeException e) {
            redo.push(command);
            throw new DomainException("Could not redo \"" + command.describe() + "\": " + e.getMessage());
        }
        undoStack(event.id()).push(command);
        return Optional.of(command.describe());
    }

    public boolean canUndo(String eventId) {
        return !undoStack(eventId).isEmpty();
    }

    public boolean canRedo(String eventId) {
        return !redoStack(eventId).isEmpty();
    }

    public List<String> undoDescriptions(String eventId) {
        return undoStack(eventId).stream().map(EventCommand::describe).toList();
    }

    public Snapshot snapshot(String eventId) {
        return new Snapshot(new ArrayList<>(undoStack(eventId)), new ArrayList<>(redoStack(eventId)));
    }

    public void restore(String eventId, Snapshot snapshot) {
        undoStacks.put(eventId, new ArrayDeque<>(snapshot.undo()));
        redoStacks.put(eventId, new ArrayDeque<>(snapshot.redo()));
    }

    public void forget(String eventId) {
        undoStacks.remove(eventId);
        redoStacks.remove(eventId);
    }

    private Deque<EventCommand> undoStack(String eventId) {
        return undoStacks.computeIfAbsent(eventId, k -> new ArrayDeque<>());
    }

    private Deque<EventCommand> redoStack(String eventId) {
        return redoStacks.computeIfAbsent(eventId, k -> new ArrayDeque<>());
    }
}
