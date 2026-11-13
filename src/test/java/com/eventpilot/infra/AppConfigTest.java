package com.eventpilot.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void parsesEnvFileIgnoringCommentsAndQuotes() {
        Map<String, String> parsed = AppConfig.parseEnvFile(List.of(
                "# comment", "", "LLM_PROVIDER=ollama", "LLM_MODEL=\"m1\"", "broken line"));
        assertEquals("ollama", parsed.get("LLM_PROVIDER"));
        assertEquals("m1", parsed.get("LLM_MODEL"));
        assertEquals(2, parsed.size());
    }

    @Test
    void claudeWithoutKeyFallsBackToMock() {
        AppConfig config = AppConfig.of(Map.of("LLM_PROVIDER", "claude",
                "ANTHROPIC_API_KEY", "YOUR_LOCAL_API_KEY_HERE"));
        assertEquals("mock", config.llmProvider());
        assertTrue(config.anthropicApiKey().isEmpty());
    }

    @Test
    void claudeWithKeyIsKept() {
        AppConfig config = AppConfig.of(Map.of("LLM_PROVIDER", "claude", "ANTHROPIC_API_KEY", "abc"));
        assertEquals("claude", config.llmProvider());
    }
}
