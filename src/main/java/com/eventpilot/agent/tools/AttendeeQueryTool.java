package com.eventpilot.agent.tools;

import com.eventpilot.domain.Attendee;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;

/** attendee_query: filter and count attendees by RSVP, role or dietary need. */
public class AttendeeQueryTool implements AgentTool {

    private static final JsonNode SCHEMA = Schemas.of("""
            {"type": "object",
             "properties": {
               "rsvp": {"type": "string", "enum": ["INVITED", "ACCEPTED", "TENTATIVE", "DECLINED"]},
               "role": {"type": "string", "enum": ["GUEST", "SPEAKER", "VIP", "STAFF"]},
               "dietary": {"type": "string", "description": "e.g. vegan"},
               "count_only": {"type": "boolean"}
             },
             "additionalProperties": false}
            """);

    @Override
    public String name() {
        return "attendee_query";
    }

    @Override
    public String description() {
        return "Find attendees by RSVP status, role or dietary need. Returns the count and (unless count_only) "
                + "names, roles, groups and dietary needs. E-mail addresses are not returned.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode a, ToolContext context) {
        String rsvp = a.path("rsvp").asText(null);
        String role = a.path("role").asText(null);
        String diet = a.hasNonNull("dietary") ? a.get("dietary").asText().toLowerCase(Locale.ROOT) : null;
        List<Attendee> matches = context.event().attendees().stream()
                .filter(x -> rsvp == null || x.rsvp().name().equals(rsvp))
                .filter(x -> role == null || x.role().name().equals(role))
                .filter(x -> diet == null || x.dietary().contains(diet))
                .toList();
        ObjectNode result = Json.object();
        result.put("count", matches.size());
        if (!a.path("count_only").asBoolean(false)) {
            ArrayNode list = result.putArray("attendees");
            for (Attendee x : matches) {
                ObjectNode n = list.addObject().put("id", x.id()).put("name", x.name()).put("role", x.role().name())
                        .put("rsvp", x.rsvp().name());
                ArrayNode d = n.putArray("dietary");
                x.dietary().forEach(d::add);
                if (!x.accessibility().isBlank()) {
                    n.put("accessibility", x.accessibility());
                }
            }
        }
        return Json.write(result);
    }
}
