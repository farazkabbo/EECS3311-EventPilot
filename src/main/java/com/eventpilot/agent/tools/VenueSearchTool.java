package com.eventpilot.agent.tools;

import com.eventpilot.application.VenueCatalog;
import com.eventpilot.domain.Venue;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** venue_search: searches the venue catalog (through the Adapter). */
public class VenueSearchTool implements AgentTool {

    private static final JsonNode SCHEMA = Schemas.of("""
            {"type": "object",
             "properties": {
               "city": {"type": "string", "description": "City, e.g. Toronto"},
               "min_capacity": {"type": "integer", "minimum": 1, "description": "Minimum number of guests"},
               "max_price_per_day": {"type": "number", "minimum": 0, "description": "Maximum daily rate in CAD"},
               "accessible": {"type": "boolean", "description": "Require wheelchair access"},
               "district": {"type": "string", "description": "Neighbourhood filter"},
               "features": {"type": "array", "items": {"type": "string"}, "description": "Required features, e.g. stage"}
             },
             "required": ["min_capacity"],
             "additionalProperties": false}
            """);

    private final VenueCatalog catalog;

    public VenueSearchTool(VenueCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "venue_search";
    }

    @Override
    public String description() {
        return "Search the venue catalog. Returns at most 8 venues matching the criteria, best rated first, "
                + "with id, capacity, daily price (CAD), accessibility, rating (out of 5), features and rooms.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode a, ToolContext context) {
        List<String> features = new ArrayList<>();
        a.path("features").forEach(f -> features.add(f.asText()));
        VenueCatalog.Criteria criteria = new VenueCatalog.Criteria(
                a.path("city").asText(null),
                a.path("min_capacity").asInt(),
                a.hasNonNull("max_price_per_day") ? a.get("max_price_per_day").asDouble() : null,
                a.hasNonNull("accessible") ? a.get("accessible").asBoolean() : null,
                a.path("district").asText(null),
                features);
        List<Venue> found = catalog.search(criteria).stream().limit(8).toList();
        Set<String> ids = new LinkedHashSet<>();
        ObjectNode result = Json.object();
        result.put("count", found.size());
        ArrayNode list = result.putArray("venues");
        for (Venue v : found) {
            ids.add(v.id());
            ObjectNode n = list.addObject();
            n.put("id", v.id()).put("name", v.name()).put("city", v.city()).put("district", v.district())
                    .put("capacity", v.capacity()).put("price_per_day", v.pricePerDay())
                    .put("accessible", v.accessible()).put("rating", v.rating());
            ArrayNode f = n.putArray("features");
            v.features().forEach(f::add);
            ArrayNode rooms = n.putArray("rooms");
            v.rooms().forEach(r -> rooms.addObject().put("name", r.name()).put("capacity", r.capacity()));
        }
        if (found.isEmpty()) {
            result.put("hint", "No venue matches. You may relax max_price_per_day or district, and must say so.");
        }
        context.recordVenues(ids);
        return Json.write(result);
    }
}
