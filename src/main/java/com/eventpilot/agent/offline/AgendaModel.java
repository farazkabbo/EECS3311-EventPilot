package com.eventpilot.agent.offline;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Plain view of the agenda, rooms and speakers from a prompt context, for the offline planner. */
class AgendaModel {

    static final class Item {
        String id;
        String title;
        String type;
        LocalDateTime start;
        LocalDateTime end;
        String room;
        String speaker;
        int audience;

        boolean isSession() {
            return List.of("keynote", "session", "workshop", "panel").contains(type);
        }

        boolean isBreakOrMeal() {
            return type.equals("break") || type.equals("meal");
        }

        boolean overlaps(LocalDateTime s, LocalDateTime e) {
            return start.isBefore(e) && s.isBefore(end);
        }
    }

    record RoomInfo(String name, int capacity) {
    }

    record SpeakerInfo(String id, String name, String topic, List<LocalDateTime[]> windows) {
        boolean available(LocalDateTime s, LocalDateTime e) {
            return windows.isEmpty() || windows.stream().anyMatch(w -> !s.isBefore(w[0]) && !e.isAfter(w[1]));
        }
    }

    final LocalDateTime eventStart;
    final LocalDateTime eventEnd;
    final int attendance;
    final List<Item> items = new ArrayList<>();
    final List<RoomInfo> rooms = new ArrayList<>();
    final List<SpeakerInfo> speakers = new ArrayList<>();

    AgendaModel(JsonNode context) {
        JsonNode e = context.path("event");
        eventStart = LocalDateTime.parse(e.path("start").asText());
        eventEnd = LocalDateTime.parse(e.path("end").asText());
        attendance = e.path("expected_attendance").asInt(50);
        e.path("rooms").forEach(r -> rooms.add(new RoomInfo(r.path("name").asText(), r.path("capacity").asInt())));
        context.path("agenda").forEach(n -> {
            Item i = new Item();
            i.id = n.path("id").asText();
            i.title = n.path("title").asText();
            i.type = n.path("type").asText("session").toLowerCase(Locale.ROOT);
            i.start = LocalDateTime.parse(n.path("start").asText());
            i.end = LocalDateTime.parse(n.path("end").asText());
            i.room = n.path("room").asText(null);
            i.speaker = n.hasNonNull("speaker_id") ? n.get("speaker_id").asText() : null;
            i.audience = n.path("expected_audience").asInt(0);
            items.add(i);
        });
        context.path("speakers").forEach(s -> {
            List<LocalDateTime[]> windows = new ArrayList<>();
            s.path("available").forEach(w -> windows.add(new LocalDateTime[] {
                    LocalDateTime.parse(w.path("start").asText()), LocalDateTime.parse(w.path("end").asText())}));
            speakers.add(new SpeakerInfo(s.path("id").asText(), s.path("name").asText(), s.path("topic").asText(""), windows));
        });
    }

    boolean roomFree(String room, LocalDateTime s, LocalDateTime e, Item ignore) {
        return items.stream().noneMatch(i -> i != ignore && room != null && room.equalsIgnoreCase(i.room)
                && i.overlaps(s, e));
    }

    boolean speakerFree(String speaker, LocalDateTime s, LocalDateTime e, Item ignore) {
        return items.stream().noneMatch(i -> i != ignore && speaker != null && speaker.equals(i.speaker) && i.overlaps(s, e));
    }

    SpeakerInfo speaker(String id) {
        return speakers.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }

    int capacity(String room) {
        return rooms.stream().filter(r -> r.name().equalsIgnoreCase(room)).mapToInt(RoomInfo::capacity).findFirst().orElse(0);
    }
}
