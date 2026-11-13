package com.eventpilot.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.application.command.AddAgendaItemCommand;
import com.eventpilot.application.command.CommandHistory;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.state.EventStatus;
import com.eventpilot.domain.state.LifecycleAction;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteEventRepositoryTest {

    @TempDir
    Path tmp;

    @Test
    void savesAndLoadsEventHistoryAndValues() {
        SqliteEventRepository repo = new SqliteEventRepository(tmp.resolve("test.db"));
        LocalDateTime nine = LocalDateTime.of(2026, 12, 5, 9, 0);
        Event event = new Event("Gala", "gala", nine, nine.plusHours(5), 80, 7000, "Toronto");
        event.transition(LifecycleAction.START_PLANNING);
        event.addAttendee(new Attendee(null, "Ann", "ann@x.com", Attendee.Role.VIP, List.of("vegan"), "wheelchair", null));
        CommandHistory history = new CommandHistory();
        history.execute(event, new AddAgendaItemCommand(new AgendaItem(null, "Dinner", AgendaItemType.MEAL,
                nine.plusHours(1), nine.plusHours(2), "Main Hall", null, 80)));
        repo.save(event);
        repo.saveHistory(event.id(), history.snapshot(event.id()));
        repo.putValue(event.id(), "pref.break", "15");

        Event loaded = repo.findById(event.id()).orElseThrow();
        assertEquals(EventStatus.PLANNING, loaded.state().status());
        assertEquals("wheelchair", loaded.attendees().get(0).accessibility());
        assertEquals("Dinner", loaded.agenda().require("a1").title());
        assertEquals(1, repo.findAll().size());
        assertEquals("15", repo.getValue(event.id(), "pref.break").orElseThrow());

        CommandHistory reloaded = new CommandHistory();
        reloaded.restore(loaded.id(), repo.loadHistory(loaded.id()));
        reloaded.undo(loaded);
        assertTrue(loaded.agenda().isEmpty());

        repo.delete(event.id());
        assertTrue(repo.findById(event.id()).isEmpty());
    }
}
