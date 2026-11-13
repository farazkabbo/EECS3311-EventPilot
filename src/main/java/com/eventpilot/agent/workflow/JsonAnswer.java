package com.eventpilot.agent.workflow;

import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;

/** Extracts the JSON object from an LLM answer (tolerates ```json fences and text around it). */
public final class JsonAnswer {

    private JsonAnswer() {
    }

    public static JsonNode parse(String text) throws ResponseParser.ParseException {
        if (text == null || text.isBlank()) {
            throw new ResponseParser.ParseException("the answer was empty; reply with a JSON object");
        }
        String t = text.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ResponseParser.ParseException("no JSON object found in the answer");
        }
        try {
            JsonNode node = Json.tree(t.substring(start, end + 1));
            if (!node.isObject()) {
                throw new ResponseParser.ParseException("the answer must be a JSON object");
            }
            return node;
        } catch (IllegalArgumentException e) {
            throw new ResponseParser.ParseException("invalid JSON (" + e.getMessage() + ")");
        }
    }
}
