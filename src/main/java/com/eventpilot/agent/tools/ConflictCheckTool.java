package com.eventpilot.agent.tools;

import com.eventpilot.agent.CommandMapper;
import com.eventpilot.domain.Conflict;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.service.ConflictDetector;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * conflict_check: applies hypothetical agenda operations to a fresh copy of the
 * event and runs the deterministic ConflictDetector, so the agent can test a
 * draft before proposing it.
 */
public class ConflictCheckTool implements AgentTool {

    private static final JsonNode SCHEMA = Schemas.of("""
            {"type": "object",
             "properties": {
               "changes": {"type": "array", "description": "Agenda operations to test (same format as the final answer). Empty = check the current agenda.",
                           "items": {"type": "object", "properties": {
                              "op": {"type": "string", "enum": ["add", "move", "remove", "rename", "assign_speaker"]},
                              "item_id": {"type": "string"}, "title": {"type": "string"}, "type": {"type": "string"},
                              "start": {"type": "string"}, "end": {"type": "string"}, "room": {"type": "string"},
                              "speaker_id": {"type": "string"}, "expected_audience": {"type": "integer"}},
                            "required": ["op"]}}
             },
             "required": ["changes"],
             "additionalProperties": false}
            """);

    private final ConflictDetector detector;
    private final CommandMapper mapper;

    public ConflictCheckTool(ConflictDetector detector, CommandMapper mapper) {
        this.detector = detector;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "conflict_check";
    }

    @Override
    public String description() {
        return "Test agenda operations on a copy of the event and list the scheduling conflicts that would result "
                + "(room overlaps, speaker double-bookings or unavailability, small rooms, items outside event hours).";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode a, ToolContext context) {
        Event trial = context.event().deepCopy();
        ObjectNode result = Json.object();
        ArrayNode rejected = result.putArray("rejected_changes");
        for (JsonNode op : a.get("changes")) {
            try {
                mapper.toCommand(op, trial).execute(trial);
            } catch (RuntimeException e) {
                rejected.addObject().put("change", op.toString()).put("reason", e.getMessage());
            }
        }
        ArrayNode list = result.putArray("conflicts");
        for (Conflict c : detector.detect(trial)) {
            ObjectNode n = list.addObject();
            n.put("type", c.type().name()).put("severity", c.severity().name()).put("message", c.message());
            ArrayNode ids = n.putArray("item_ids");
            c.itemIds().forEach(ids::add);
        }
        result.put("blocking", detector.hasBlocking(trial));
        return Json.write(result);
    }
}
