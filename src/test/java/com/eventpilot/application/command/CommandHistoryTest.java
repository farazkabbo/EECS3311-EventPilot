package com.eventpilot.application.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Budget;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Room;
import com.eventpilot.domain.Venue;
import com.eventpilot.domain.state.LifecycleAction;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandHistoryTest {

    static final LocalDateTime NINE = LocalDateTime.of(2026, 12, 5, 9, 0);
    private Event event;
    private CommandHistory history;

    @BeforeEach
    void setUp() {
        event = new Event("Conf", "conference", NINE, NINE.plusHours(8), 100, 5000, "Toronto");
        history = new CommandHistory();
    }

    private AgendaItem item(String title, int h) {
        return new AgendaItem(null, title, AgendaItemType.SESSION, NINE.plusHours(h), NINE.plusHours(h + 1), "Main Hall", null, 50);
    }

    @Test
    void macroIsUndoneAndRedoneAsOneUnit() {
        MacroCommand macro = new MacroCommand("Generate agenda",
                List.of(new AddAgendaItemCommand(item("A", 0)), new AddAgendaItemCommand(item("B", 1))));
        history.execute(event, macro);
        assertEquals(2, event.agenda().size());

        assertTrue(history.undo(event).orElseThrow().startsWith("Generate agenda"));
        assertTrue(event.agenda().isEmpty());

        history.redo(event);
        assertEquals(2, event.agenda().size());
        assertEquals(List.of("a1", "a2"), event.agenda().items().stream().map(AgendaItem::id).toList());
    }

    @Test
    void moveAndRemoveRestoreOnUndo() {
        history.execute(event, new AddAgendaItemCommand(item("Keynote", 0)));
        history.execute(event, new MoveSessionCommand("a1", "Keynote", NINE.plusHours(5), NINE.plusHours(6), "Room 2"));
        assertEquals(NINE.plusHours(5), event.agenda().require("a1").start());
        history.undo(event);
        assertEquals(NINE, event.agenda().require("a1").start());
        assertEquals("Main Hall", event.agenda().require("a1").room());

        history.execute(event, new RemoveAgendaItemCommand("a1", "Keynote"));
        assertTrue(event.agenda().isEmpty());
        history.undo(event);
        assertEquals("Keynote", event.agenda().require("a1").title());
    }

    @Test
    void newChangeClearsRedo() {
        history.execute(event, new AddAgendaItemCommand(item("A", 0)));
        history.undo(event);
        assertTrue(history.canRedo(event.id()));
        history.execute(event, new AddAgendaItemCommand(item("B", 1)));
        assertFalse(history.canRedo(event.id()));
    }

    @Test
    void nothingToUndo() {
        assertTrue(history.undo(event).isEmpty());
    }

    @Test
    void failingMacroRollsBack() {
        MacroCommand macro = new MacroCommand("Bad", List.of(new AddAgendaItemCommand(item("A", 0)),
                new MoveSessionCommand("a99", "Ghost", NINE, NINE.plusHours(1), null)));
        assertThrows(DomainException.class, () -> history.execute(event, macro));
        assertTrue(event.agenda().isEmpty());
        assertFalse(history.canUndo(event.id()));
    }

    @Test
    void venueAndBudgetLineUndo() {
        Venue v = new Venue("v1", "Hall", "Toronto", "Downtown", "", 200, 1000, true, 4, List.of(), List.of(new Room("Big", 200)));
        history.execute(event, new SetVenueCommand(v, "fits"));
        history.execute(event, new SetBudgetLineCommand(new Budget.Line("venue", 2000, "")));
        assertEquals("Big", event.rooms().get(0).name());
        history.undo(event);
        assertTrue(event.budget().line("venue").isEmpty());
        history.undo(event);
        assertNull(event.venue());
        assertEquals("Main Hall", event.rooms().get(0).name());
    }

    @Test
    void readOnlyEventRefusesChanges() {
        event.transition(LifecycleAction.CANCEL);
        assertThrows(DomainException.class, () -> history.execute(event, new AddAgendaItemCommand(item("A", 0))));
    }

    @Test
    void historySurvivesSerialization() throws Exception {
        history.execute(event, new MacroCommand("Agenda", List.of(new AddAgendaItemCommand(item("A", 0)))));
        history.execute(event, new MoveSessionCommand("a1", "A", NINE.plusHours(3), NINE.plusHours(4), null));
        String json = Json.write(history.snapshot(event.id()));

        CommandHistory reloaded = new CommandHistory();
        reloaded.restore(event.id(), Json.MAPPER.readValue(json, new TypeReference<CommandHistory.Snapshot>() { }));
        reloaded.undo(event);
        assertEquals(NINE, event.agenda().require("a1").start());
        reloaded.undo(event);
        assertTrue(event.agenda().isEmpty());
    }
}
