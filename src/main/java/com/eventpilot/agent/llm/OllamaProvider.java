package com.eventpilot.agent.llm;

import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Concrete strategy and Adapter for a local model served by Ollama
 * ({@code POST /api/chat} with tool definitions). Free and works offline.
 */
public class OllamaProvider implements LLMProvider {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final String model;

    public OllamaProvider(String baseUrl, String model) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public String modelFor(LLMRequest.Tier tier) {
        return model;
    }

    @Override
    public LLMResponse chat(LLMRequest request) throws LLMException {
        ObjectNode body = Json.object();
        body.put("model", model);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.system());
        for (ChatMessage m : request.messages()) {
            switch (m.role()) {
                case USER -> messages.addObject().put("role", "user").put("content", m.text());
                case ASSISTANT -> {
                    ObjectNode msg = messages.addObject().put("role", "assistant").put("content", m.text());
                    if (!m.toolCalls().isEmpty()) {
                        ArrayNode calls = msg.putArray("tool_calls");
                        for (ChatMessage.ToolCall c : m.toolCalls()) {
                            ObjectNode fn = calls.addObject().putObject("function");
                            fn.put("name", c.name());
                            fn.set("arguments", safeTree(c.argumentsJson()));
                        }
                    }
                }
                case TOOL_RESULTS -> m.toolResults().forEach(r -> messages.addObject()
                        .put("role", "tool").put("tool_name", r.name()).put("content", r.content()));
            }
        }
        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (LLMRequest.ToolSpec spec : request.tools()) {
                ObjectNode fn = tools.addObject().put("type", "function").putObject("function");
                fn.put("name", spec.name());
                fn.put("description", spec.description());
                fn.set("parameters", spec.inputSchema());
            }
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException e) {
            throw new LLMException("Ollama is not running at " + baseUrl + " (start it with 'ollama serve')", false, e);
        } catch (HttpTimeoutException e) {
            throw new LLMException("Ollama timed out", true, e);
        } catch (IOException e) {
            throw new LLMException("Network error calling Ollama: " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("Interrupted", false, e);
        }
        if (response.statusCode() >= 400) {
            throw new LLMException("Ollama error " + response.statusCode() + ": " + response.body(),
                    response.statusCode() >= 500);
        }
        JsonNode root = Json.tree(response.body());
        JsonNode msg = root.path("message");
        List<ChatMessage.ToolCall> calls = new ArrayList<>();
        msg.path("tool_calls").forEach(c -> calls.add(new ChatMessage.ToolCall(
                "call_" + UUID.randomUUID().toString().substring(0, 8),
                c.path("function").path("name").asText(),
                Json.write(c.path("function").path("arguments")))));
        return new LLMResponse(msg.path("content").asText(""), calls,
                calls.isEmpty() ? "end_turn" : "tool_use",
                root.path("prompt_eval_count").asLong(0), root.path("eval_count").asLong(0), model);
    }

    private static JsonNode safeTree(String json) {
        try {
            return Json.tree(json);
        } catch (IllegalArgumentException e) {
            return Json.object();
        }
    }
}
