package com.eventpilot.infra;

import com.eventpilot.application.EventRepository;
import com.eventpilot.application.command.CommandHistory;
import com.eventpilot.domain.Event;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Repository kept in memory (stores JSON copies, like the real one). Used by tests. */
public class InMemoryEventRepository implements EventRepository {

    private final Map<String, String> events = new LinkedHashMap<>();
    private final Map<String, String> histories = new LinkedHashMap<>();
    private final Map<String, String> values = new LinkedHashMap<>();

    @Override
    public void save(Event event) {
        events.put(event.id(), Json.write(event));
    }

    @Override
    public Optional<Event> findById(String id) {
        return Optional.ofNullable(events.get(id)).map(j -> Json.read(j, Event.class));
    }

    @Override
    public List<Event> findAll() {
        List<Event> list = new ArrayList<>();
        events.values().forEach(j -> list.add(Json.read(j, Event.class)));
        return list;
    }

    @Override
    public void delete(String id) {
        events.remove(id);
        histories.remove(id);
    }

    @Override
    public CommandHistory.Snapshot loadHistory(String eventId) {
        String json = histories.get(eventId);
        return json == null ? CommandHistory.Snapshot.empty() : Json.read(json, CommandHistory.Snapshot.class);
    }

    @Override
    public void saveHistory(String eventId, CommandHistory.Snapshot snapshot) {
        histories.put(eventId, Json.write(snapshot));
    }

    @Override
    public Optional<String> getValue(String scope, String key) {
        return Optional.ofNullable(values.get(scope + "\u0000" + key));
    }

    @Override
    public void putValue(String scope, String key, String value) {
        values.put(scope + "\u0000" + key, value);
    }

    @Override
    public void removeValue(String scope, String key) {
        values.remove(scope + "\u0000" + key);
    }
}
