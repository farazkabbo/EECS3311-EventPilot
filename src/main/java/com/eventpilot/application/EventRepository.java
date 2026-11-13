package com.eventpilot.application;

import com.eventpilot.application.command.CommandHistory;
import com.eventpilot.domain.Event;
import java.util.List;
import java.util.Optional;

/**
 * Storage port used by the facade (dependency inversion: the application layer
 * owns the interface; {@code SqliteEventRepository} implements it).
 */
public interface EventRepository {

    void save(Event event);

    Optional<Event> findById(String id);

    List<Event> findAll();

    void delete(String id);

    CommandHistory.Snapshot loadHistory(String eventId);

    void saveHistory(String eventId, CommandHistory.Snapshot snapshot);

    /** Small key-value store for memory, preferences and CLI settings. */
    Optional<String> getValue(String scope, String key);

    void putValue(String scope, String key, String value);

    void removeValue(String scope, String key);
}
