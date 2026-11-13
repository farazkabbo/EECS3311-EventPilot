package com.eventpilot.agent;

import com.eventpilot.application.command.AddAgendaItemCommand;
import com.eventpilot.application.command.AssignSpeakerCommand;
import com.eventpilot.application.command.EventCommand;
import com.eventpilot.application.command.MoveSessionCommand;
import com.eventpilot.application.command.RemoveAgendaItemCommand;
import com.eventpilot.application.command.RenameAgendaItemCommand;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Speaker;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Turns one agenda operation written by the LLM into a typed {@link EventCommand}.
 * Only the operations below exist; anything else (or a reference to an item or
 * speaker that does not exist) is rejected with a message the LLM can act on.
 *
 * <pre>
 * {"op":"add", "title":..., "type":"session|keynote|break|meal|...", "start":"14:00", "end":"15:00", "room":..., "speaker_id":..., "expected_audience":80}
 * {"op":"move", "item_id":"a3", "start":"14:00", "end":"15:00" (optional, keeps duration), "room": (optional)}
 * {"op":"remove", "item_id":"a3"}
 * {"op":"rename", "item_id":"a3", "title":"New title"}
 * {"op":"assign_speaker", "item_id":"a3", "speaker_id":"s1"}
 * </pre>
 */
public class CommandMapper {

    public static class MappingException extends RuntimeException {
        public MappingException(String message) {
            super(message);
        }
    }

    public EventCommand toCommand(JsonNode op, Event event) {
        String kind = op.path("op").asText("").toLowerCase(Locale.ROOT);
        try {
            return switch (kind) {
                case "add" -> add(op, event);
                case "move", "reschedule" -> move(op, event);
                case "remove", "delete" -> {
                    AgendaItem item = item(op, event);
                    yield new RemoveAgendaItemCommand(item.id(), item.title());
                }
                case "rename" -> new RenameAgendaItemCommand(item(op, event).id(), text(op, "title"));
                case "assign_speaker" -> {
                    AgendaItem item = item(op, event);
                    Speaker speaker = event.findSpeaker(text(op, "speaker_id"))
                            .orElseThrow(() -> new MappingException("Unknown speaker_id \"" + op.path("speaker_id").asText()
                                    + "\". Known speakers: " + event.speakers().stream().map(Speaker::id).toList()));
                    yield new AssignSpeakerCommand(item.id(), item.title(), speaker.id(), speaker.name());
                }
                default -> throw new MappingException("Unsupported operation \"" + kind
                        + "\". Allowed: add, move, remove, rename, assign_speaker.");
            };
        } catch (IllegalArgumentException e) {
            throw new MappingException("Invalid " + kind + " operation: " + e.getMessage());
        }
    }

    private EventCommand add(JsonNode op, Event event) {
        LocalDateTime start = TimeParser.parse(text(op, "start"), event.start().toLocalDate());
        LocalDateTime end = op.hasNonNull("end")
                ? TimeParser.parse(op.get("end").asText(), event.start().toLocalDate())
                : start.plusMinutes(op.path("duration_minutes").asInt(60));
        String speakerId = op.hasNonNull("speaker_id") && !op.get("speaker_id").asText().isBlank()
                ? op.get("speaker_id").asText() : null;
        if (speakerId != null && event.findSpeaker(speakerId).isEmpty()) {
            throw new MappingException("Unknown speaker_id \"" + speakerId + "\"");
        }
        String room = op.hasNonNull("room") ? op.get("room").asText() : event.rooms().get(0).name();
        AgendaItemType type = AgendaItemType.parse(op.path("type").asText(null) == null ? text(op, "title")
                : op.get("type").asText());
        int audience = op.path("expected_audience").asInt(type.isSession() ? event.expectedAttendance() : 0);
        return new AddAgendaItemCommand(new AgendaItem(null, text(op, "title"), type, start, end, room, speakerId, audience));
    }

    private EventCommand move(JsonNode op, Event event) {
        AgendaItem item = item(op, event);
        LocalDateTime start = TimeParser.parse(text(op, "start"), item.start().toLocalDate());
        LocalDateTime end = op.hasNonNull("end")
                ? TimeParser.parse(op.get("end").asText(), item.start().toLocalDate())
                : start.plus(Duration.between(item.start(), item.end()));
        String room = op.hasNonNull("room") && !op.get("room").asText().isBlank() ? op.get("room").asText() : null;
        if (room != null && event.room(room).isEmpty()) {
            throw new MappingException("Unknown room \"" + room + "\". Rooms: "
                    + event.rooms().stream().map(r -> r.name()).toList());
        }
        return new MoveSessionCommand(item.id(), item.title(), start, end, room);
    }

    private AgendaItem item(JsonNode op, Event event) {
        String id = text(op, "item_id");
        return event.agenda().find(id).orElseThrow(() -> new MappingException("Unknown item_id \"" + id
                + "\". Existing ids: " + event.agenda().items().stream().map(AgendaItem::id).toList()));
    }

    private static String text(JsonNode op, String field) {
        JsonNode n = op.get(field);
        if (n == null || n.isNull() || n.asText().isBlank()) {
            throw new MappingException("Missing field \"" + field + "\" in " + op);
        }
        return n.asText();
    }
}
