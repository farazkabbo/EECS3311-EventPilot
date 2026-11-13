package com.eventpilot.agent.tools;

import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;

/** Small helper to write tool schemas compactly. */
final class Schemas {

    private Schemas() {
    }

    static JsonNode of(String json) {
        return Json.tree(json);
    }
}
