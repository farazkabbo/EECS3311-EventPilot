package com.eventpilot.agent.tools;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Speaker;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** speaker_availability: each speaker's availability windows and current assignments. */
public class SpeakerAvailabilityTool implements AgentTool {

    private static final JsonNode SCHEMA = Schemas.of("""
            {"type": "object",
             "properties": {"speaker_id": {"type": "string", "description": "Optional: only this speaker"}},
             "additionalProperties": false}
            """);

    @Override
    public String name() {
        return "speaker_availability";
    }

    @Override
    public String description() {
        return "List speakers with their topics, availability windows (empty = available all day) "
                + "and the agenda items already assigned to them.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode a, ToolContext context) {
        Event event = context.event();
        String only = a.path("speaker_id").asText(null);
        ObjectNode result = Json.object();
        ArrayNode list = result.putArray("speakers");
        for (Speaker s : event.speakers()) {
            if (only != null && !s.id().equalsIgnoreCase(only)) {
                continue;
            }
            ObjectNode n = list.addObject().put("id", s.id()).put("name", s.name()).put("topic", s.topic());
            ArrayNode windows = n.putArray("available");
            s.availability().forEach(w -> windows.addObject().put("start", w.start().toString()).put("end", w.end().toString()));
            ArrayNode assigned = n.putArray("assigned_items");
            for (AgendaItem item : event.agenda().items()) {
                if (s.id().equals(item.speakerId())) {
                    assigned.addObject().put("item_id", item.id()).put("title", item.title())
                            .put("start", item.start().toString()).put("end", item.end().toString());
                }
            }
        }
        if (only != null && list.isEmpty()) {
            throw new IllegalArgumentException("unknown speaker_id " + only);
        }
        return Json.write(result);
    }
}
