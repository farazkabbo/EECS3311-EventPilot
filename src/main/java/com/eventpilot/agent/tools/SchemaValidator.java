package com.eventpilot.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Minimal JSON-schema checker for tool arguments: types, required fields, enums, minimum, unknown fields. */
public final class SchemaValidator {

    private SchemaValidator() {
    }

    public static List<String> validate(JsonNode value, JsonNode schema) {
        List<String> errors = new ArrayList<>();
        check(value, schema, "arguments", errors);
        return errors;
    }

    private static void check(JsonNode value, JsonNode schema, String path, List<String> errors) {
        String type = schema.path("type").asText("");
        if (!type.isEmpty() && !matchesType(value, type)) {
            errors.add(path + " must be " + type + " but was " + value.getNodeType().name().toLowerCase());
            return;
        }
        if (schema.has("enum")) {
            boolean ok = false;
            for (JsonNode allowed : schema.get("enum")) {
                ok |= allowed.equals(value);
            }
            if (!ok) {
                errors.add(path + " must be one of " + schema.get("enum"));
            }
        }
        if (schema.has("minimum") && value.isNumber() && value.asDouble() < schema.get("minimum").asDouble()) {
            errors.add(path + " must be >= " + schema.get("minimum").asText());
        }
        if (type.equals("object")) {
            for (JsonNode req : schema.path("required")) {
                if (!value.has(req.asText()) || value.get(req.asText()).isNull()) {
                    errors.add(path + "." + req.asText() + " is required");
                }
            }
            JsonNode props = schema.path("properties");
            boolean closed = schema.path("additionalProperties").isBoolean() && !schema.get("additionalProperties").asBoolean();
            Iterator<Map.Entry<String, JsonNode>> it = value.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (props.has(e.getKey())) {
                    if (!e.getValue().isNull()) {
                        check(e.getValue(), props.get(e.getKey()), path + "." + e.getKey(), errors);
                    }
                } else if (closed) {
                    errors.add(path + "." + e.getKey() + " is not a known argument");
                } else if (schema.path("additionalProperties").isObject()) {
                    check(e.getValue(), schema.get("additionalProperties"), path + "." + e.getKey(), errors);
                }
            }
        }
        if (type.equals("array") && schema.has("items")) {
            for (int i = 0; i < value.size(); i++) {
                check(value.get(i), schema.get("items"), path + "[" + i + "]", errors);
            }
        }
    }

    private static boolean matchesType(JsonNode v, String type) {
        return switch (type) {
            case "object" -> v.isObject();
            case "array" -> v.isArray();
            case "string" -> v.isTextual();
            case "integer" -> v.isIntegralNumber() || (v.isNumber() && v.asDouble() == Math.rint(v.asDouble()));
            case "number" -> v.isNumber();
            case "boolean" -> v.isBoolean();
            default -> true;
        };
    }
}
