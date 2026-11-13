package com.eventpilot.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.infra.AppConfig;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProvidersTest {

    @Test
    void factoryPicksProviderFromConfig() {
        assertInstanceOf(MockLLMProvider.class,
                LLMProviderFactory.create(AppConfig.of(Map.of("LLM_PROVIDER", "mock")), r -> LLMResponse.text("")));
        assertInstanceOf(OllamaProvider.class,
                LLMProviderFactory.create(AppConfig.of(Map.of("LLM_PROVIDER", "ollama")), r -> LLMResponse.text("")));
        assertInstanceOf(ClaudeProvider.class, LLMProviderFactory.create(
                AppConfig.of(Map.of("LLM_PROVIDER", "claude", "ANTHROPIC_API_KEY", "test-key")), r -> LLMResponse.text("")));
    }

    @Test
    void serverFallbackOnlyForSupportedModels() {
        assertTrue(ClaudeProvider.supportsServerFallback("claude-sonnet-5-5"));
        assertFalse(ClaudeProvider.supportsServerFallback("claude-haiku-5-5"));
    }

    @Test
    void ollamaAdapterTranslatesToolsAndToolCalls() throws Exception {
        AtomicReference<String> sentBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            sentBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = """
                    {"message": {"role": "assistant", "content": "",
                      "tool_calls": [{"function": {"name": "venue_search", "arguments": {"city": "Toronto", "min_capacity": 100}}}]},
                     "prompt_eval_count": 42, "eval_count": 7}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        try {
            OllamaProvider provider = new OllamaProvider("http://127.0.0.1:" + server.getAddress().getPort(), "llama3.1:8b");
            LLMRequest request = new LLMRequest("system text",
                    List.of(ChatMessage.user("find a venue")),
                    List.of(new LLMRequest.ToolSpec("venue_search", "Search venues",
                            Json.tree("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}"))),
                    LLMRequest.Tier.MAIN, 500);
            LLMResponse response = provider.chat(request);

            assertTrue(response.wantsTools());
            assertEquals("venue_search", response.toolCalls().get(0).name());
            assertEquals(100, Json.tree(response.toolCalls().get(0).argumentsJson()).path("min_capacity").asInt());
            assertEquals(42, response.inputTokens());

            JsonNode body = Json.tree(sentBody.get());
            assertEquals("system", body.path("messages").get(0).path("role").asText());
            assertEquals("venue_search", body.path("tools").get(0).path("function").path("name").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ollamaNotRunningIsReportedClearly() {
        OllamaProvider provider = new OllamaProvider("http://127.0.0.1:1", "x");
        LLMRequest request = new LLMRequest("s", List.of(ChatMessage.user("hi")), List.of(), LLMRequest.Tier.MAIN, 10);
        LLMException e = org.junit.jupiter.api.Assertions.assertThrows(LLMException.class, () -> provider.chat(request));
        assertFalse(e.isRetryable());
    }
}
