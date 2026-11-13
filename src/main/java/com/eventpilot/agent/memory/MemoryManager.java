package com.eventpilot.agent.memory;

import com.eventpilot.application.EventRepository;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Agent memory. Conversation memory keeps the last turns per event so the agent
 * can resolve "it" or "the keynote"; preference memory keeps the organizer's
 * habits (e.g. break length, default city) across events and runs.
 */
public class MemoryManager {

    public static final int MAX_TURNS = 12;
    private static final String GLOBAL = "_global";
    private static final String CONVERSATION_KEY = "memory.conversation";
    private static final String PREFERENCES_KEY = "memory.preferences";

    public record Turn(String role, String text) {
    }

    private final EventRepository store;

    public MemoryManager(EventRepository store) {
        this.store = store;
    }

    public void remember(String eventId, String role, String text) {
        List<Turn> turns = new ArrayList<>(conversation(eventId));
        String t = text.length() > 600 ? text.substring(0, 597) + "..." : text;
        turns.add(new Turn(role, t));
        while (turns.size() > MAX_TURNS) {
            turns.remove(0);
        }
        store.putValue(scope(eventId), CONVERSATION_KEY, Json.write(turns));
    }

    public List<Turn> conversation(String eventId) {
        return store.getValue(scope(eventId), CONVERSATION_KEY)
                .map(json -> readList(json))
                .orElse(List.of());
    }

    public void clearConversation(String eventId) {
        store.removeValue(scope(eventId), CONVERSATION_KEY);
    }

    public void setPreference(String key, String value) {
        Map<String, String> prefs = new TreeMap<>(preferences());
        String k = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (value == null || value.isBlank()) {
            prefs.remove(k);
        } else {
            prefs.put(k, value.trim());
        }
        store.putValue(GLOBAL, PREFERENCES_KEY, Json.write(prefs));
    }

    public Map<String, String> preferences() {
        return store.getValue(GLOBAL, PREFERENCES_KEY).map(json -> {
            try {
                return Json.MAPPER.readValue(json, new TypeReference<Map<String, String>>() { });
            } catch (JsonProcessingException e) {
                return Map.<String, String>of();
            }
        }).orElse(Map.of());
    }

    /** Text block added to prompts: saved preferences and recent conversation. */
    public String buildMemoryContext(String eventId) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> prefs = preferences();
        if (!prefs.isEmpty()) {
            sb.append("Organizer preferences (apply them unless the request says otherwise):\n");
            prefs.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        }
        List<Turn> turns = conversation(eventId);
        if (!turns.isEmpty()) {
            sb.append("Recent conversation (oldest first):\n");
            turns.forEach(t -> sb.append("- ").append(t.role()).append(": ").append(t.text()).append('\n'));
        }
        return sb.toString();
    }

    private static String scope(String eventId) {
        return eventId == null ? GLOBAL : eventId;
    }

    private static List<Turn> readList(String json) {
        try {
            return Json.MAPPER.readValue(json, new TypeReference<List<Turn>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
