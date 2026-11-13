package com.eventpilot.agent.offline;

import com.eventpilot.agent.TimeParser;
import com.eventpilot.agent.llm.LLMResponse;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Offline F04, F05, F06: greedy agenda construction, speaker assignment and conflict repair. */
class OfflineScheduling {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    // ---------------- F04 agenda generation ----------------

    LLMResponse agenda(OfflinePlanner.Turn turn) {
        AgendaModel m = new AgendaModel(turn.context());
        JsonNode prefs = turn.context().path("preferences");
        ArrayNode changes = buildAgenda(m, prefs);
        if (turn.toolRounds() == 0 && turn.offered("conflict_check")) {
            ObjectNode args = Json.object();
            args.set("changes", changes);
            return OfflinePlanner.call("conflict_check", args);
        }
        ObjectNode out = Json.object();
        int requested = prefs.path("tracks").asInt(1);
        int tracks = Math.max(1, Math.min(requested, m.rooms.size()));
        out.put("summary", "A " + (tracks > 1 ? tracks + "-track " : "")
                + (requested > tracks ? "(only " + m.rooms.size() + " room(s) available, so " + tracks + " track) " : "")
                + "agenda with " + changes.size() + " items: opening keynote, sessions of "
                + prefs.path("sessionMinutes").asInt(45) + " minutes, " + prefs.path("breakMinutes").asInt(15)
                + "-minute breaks and lunch. Checked with conflict_check.");
        out.set("changes", changes);
        return OfflinePlanner.text(Json.write(out));
    }

    private ArrayNode buildAgenda(AgendaModel m, JsonNode prefs) {
        int tracks = Math.max(1, Math.min(prefs.path("tracks").asInt(1), m.rooms.size()));
        int session = prefs.path("sessionMinutes").asInt(45);
        int brk = prefs.path("breakMinutes").asInt(15);
        LocalTime lunchAt = TimeParser.parseTime(prefs.path("lunchTime").asText("12:00"));
        List<String> topics = new ArrayList<>();
        prefs.path("topics").forEach(t -> topics.add(t.asText()));
        if (topics.isEmpty()) {
            m.speakers.stream().map(AgendaModel.SpeakerInfo::topic).filter(t -> !t.isBlank()).forEach(topics::add);
        }

        ArrayNode out = Json.array();
        Set<String> usedSpeakers = new HashSet<>();
        LocalDateTime t = m.eventStart;
        if (!m.items.isEmpty()) { // extend mode: continue after the last existing item
            t = m.items.stream().map(i -> i.end).max(LocalDateTime::compareTo).get();
        }
        LocalDateTime end = m.eventEnd;
        long totalHours = Duration.between(m.eventStart, end).toHours();
        String main = m.rooms.get(0).name();

        if (m.items.isEmpty()) {
            LocalDateTime kEnd = min(t.plusMinutes(45), end);
            out.add(add("Opening keynote", "keynote", t, kEnd, main, pickSpeaker(m, t, kEnd, usedSpeakers, "keynote"), m.attendance,
                    "Opens the event for everyone"));
            t = kEnd;
        }
        int counter = 1;
        int blocksSinceBreak = 0;
        boolean lunchDone = totalHours < 4;
        while (Duration.between(t, end).toMinutes() >= session) {
            LocalDateTime lunchStart = t.toLocalDate().atTime(lunchAt);
            if (!lunchDone && !t.isBefore(lunchStart.minusMinutes(session / 2))) {
                LocalDateTime lEnd = min(t.plusMinutes(60), end);
                out.add(add("Lunch", "meal", t, lEnd, main, null, 0, "Lunch for all attendees"));
                t = lEnd;
                lunchDone = true;
                blocksSinceBreak = 0;
                continue;
            }
            if (blocksSinceBreak == 2 && Duration.between(t, end).toMinutes() >= session + brk) {
                out.add(add("Coffee break", "break", t, t.plusMinutes(brk), main, null, 0, "Rest and networking"));
                t = t.plusMinutes(brk);
                blocksSinceBreak = 0;
                continue;
            }
            LocalDateTime sEnd = t.plusMinutes(session);
            int perTrack = Math.max(1, m.attendance / tracks);
            for (int k = 0; k < tracks; k++) {
                String topic = topics.isEmpty() ? "Session " + counter : topics.get((counter - 1) % topics.size());
                String title = tracks > 1 ? topic + " (Track " + (char) ('A' + k) + ")" : topic;
                String room = m.rooms.get(k).name();
                int audience = Math.min(perTrack, m.capacity(room));
                out.add(add(title, "session", t, sEnd, room, pickSpeaker(m, t, sEnd, usedSpeakers, topic), audience,
                        "Session in " + room));
                counter++;
            }
            t = sEnd;
            blocksSinceBreak++;
        }
        if (Duration.between(t, end).toMinutes() >= 15) {
            out.add(add("Closing & networking", "networking", t, end, main, null, 0, "Wrap-up"));
        }
        return out;
    }

    private static String pickSpeaker(AgendaModel m, LocalDateTime s, LocalDateTime e, Set<String> used, String topic) {
        AgendaModel.SpeakerInfo best = null;
        for (AgendaModel.SpeakerInfo sp : m.speakers) {
            if (used.contains(sp.id()) || !sp.available(s, e)) {
                continue;
            }
            if (best == null || matches(sp.topic(), topic)) {
                best = sp;
            }
        }
        if (best != null) {
            used.add(best.id());
            return best.id();
        }
        return null;
    }

    private static boolean matches(String speakerTopic, String sessionTitle) {
        if (speakerTopic == null || speakerTopic.isBlank()) {
            return false;
        }
        for (String w : speakerTopic.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (w.length() > 2 && sessionTitle.toLowerCase(Locale.ROOT).contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static ObjectNode add(String title, String type, LocalDateTime s, LocalDateTime e, String room,
                                  String speaker, int audience, String reason) {
        ObjectNode n = Json.object();
        n.put("op", "add").put("title", title).put("type", type).put("start", s.format(HM)).put("end", e.format(HM))
                .put("room", room).put("expected_audience", audience).put("reason", reason);
        if (speaker != null) {
            n.put("speaker_id", speaker);
        }
        return n;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    // ---------------- F05 speaker scheduling ----------------

    LLMResponse speakers(OfflinePlanner.Turn turn) {
        if (turn.toolRounds() == 0 && turn.offered("speaker_availability")) {
            return OfflinePlanner.call("speaker_availability", Json.object());
        }
        AgendaModel m = new AgendaModel(turn.context());
        ArrayNode changes = Json.array();
        List<String> unplaced = new ArrayList<>();
        Set<String> used = new HashSet<>();
        m.items.stream().filter(i -> i.speaker != null).forEach(i -> used.add(i.speaker));
        for (AgendaModel.Item item : m.items) {
            if (!item.isSession() || item.speaker != null) {
                continue;
            }
            AgendaModel.SpeakerInfo chosen = null;
            for (AgendaModel.SpeakerInfo sp : m.speakers) {
                boolean ok = sp.available(item.start, item.end) && m.speakerFree(sp.id(), item.start, item.end, item);
                if (!ok) {
                    continue;
                }
                if (chosen == null || (!used.contains(sp.id()) && used.contains(chosen.id()))
                        || (matches(sp.topic(), item.title) && !matches(chosen.topic(), item.title))) {
                    chosen = sp;
                }
            }
            if (chosen != null) {
                item.speaker = chosen.id();
                used.add(chosen.id());
                changes.add(Json.object().put("op", "assign_speaker").put("item_id", item.id).put("speaker_id", chosen.id())
                        .put("reason", chosen.name() + " is available " + item.start.format(HM) + "-" + item.end.format(HM)
                                + (matches(chosen.topic(), item.title) ? " and the topic matches" : "")));
            }
        }
        for (AgendaModel.SpeakerInfo sp : m.speakers) {
            if (!used.contains(sp.id())) {
                unplaced.add(sp.name());
            }
        }
        if (turn.toolRounds() == 1 && turn.offered("conflict_check") && !changes.isEmpty()) {
            ObjectNode args = Json.object();
            args.set("changes", changes);
            return OfflinePlanner.call("conflict_check", args);
        }
        ObjectNode out = Json.object();
        out.put("summary", "Assigned " + changes.size() + " session(s) to available speakers"
                + (unplaced.isEmpty() ? "." : ". Could not place: " + String.join(", ", unplaced)
                + " (no free session inside their availability)."));
        out.set("changes", changes);
        return OfflinePlanner.text(Json.write(out));
    }

    // ---------------- F06 conflict resolution ----------------

    LLMResponse resolve(OfflinePlanner.Turn turn) {
        AgendaModel m = new AgendaModel(turn.context());
        ArrayNode changes = Json.array();
        Set<String> handled = new HashSet<>();
        for (JsonNode c : turn.context().path("conflicts")) {
            List<String> ids = new ArrayList<>();
            c.path("itemIds").forEach(i -> ids.add(i.asText()));
            if (ids.isEmpty()) {
                continue;
            }
            String id = ids.get(ids.size() - 1);
            if (!handled.add(id)) {
                continue;
            }
            AgendaModel.Item item = m.items.stream().filter(i -> i.id.equals(id)).findFirst().orElse(null);
            if (item == null) {
                continue;
            }
            String type = c.path("type").asText();
            if (type.equals("ROOM_TOO_SMALL")) {
                m.rooms.stream().filter(r -> r.capacity() >= item.audience && m.roomFree(r.name(), item.start, item.end, item))
                        .findFirst().ifPresent(r -> {
                            item.room = r.name();
                            changes.add(move(item, "moved to a room that seats " + r.capacity()));
                        });
                continue;
            }
            if (relocate(m, item)) {
                changes.add(move(item, "resolves " + type.toLowerCase(Locale.ROOT).replace('_', ' ')));
            }
        }
        if (turn.toolRounds() == 0 && turn.offered("conflict_check")) {
            ObjectNode args = Json.object();
            args.set("changes", changes);
            return OfflinePlanner.call("conflict_check", args);
        }
        ObjectNode out = Json.object();
        out.put("summary", changes.isEmpty() ? "No automatic fix found; please adjust the listed items manually."
                : "Moved " + changes.size() + " item(s) to free slots; nothing was removed.");
        out.set("changes", changes);
        return OfflinePlanner.text(Json.write(out));
    }

    /** Finds the earliest slot (same room first, then other rooms) where the item fits without clashes. */
    private static boolean relocate(AgendaModel m, AgendaModel.Item item) {
        Duration length = Duration.between(item.start, item.end);
        List<String> roomOrder = new ArrayList<>();
        if (item.room != null) {
            roomOrder.add(item.room);
        }
        m.rooms.forEach(r -> {
            if (!roomOrder.contains(r.name()) && r.capacity() >= item.audience) {
                roomOrder.add(r.name());
            }
        });
        for (String room : roomOrder) {
            for (LocalDateTime s = m.eventStart; !s.plus(length).isAfter(m.eventEnd); s = s.plusMinutes(15)) {
                LocalDateTime e = s.plus(length);
                AgendaModel.SpeakerInfo sp = item.speaker == null ? null : m.speaker(item.speaker);
                boolean speakerOk = item.speaker == null
                        || (sp != null && sp.available(s, e) && m.speakerFree(item.speaker, s, e, item));
                if (m.roomFree(room, s, e, item) && speakerOk) {
                    item.start = s;
                    item.end = e;
                    item.room = room;
                    return true;
                }
            }
        }
        return false;
    }

    private static ObjectNode move(AgendaModel.Item item, String reason) {
        return Json.object().put("op", "move").put("item_id", item.id).put("start", item.start.format(HM))
                .put("end", item.end.format(HM)).put("room", item.room).put("reason", reason);
    }
}
