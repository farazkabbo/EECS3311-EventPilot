package com.eventpilot.agent.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class LLMClientTest {

    private static LLMRequest request() {
        return new LLMRequest("sys", List.of(ChatMessage.user("hi")), List.of(), LLMRequest.Tier.MAIN, 100);
    }

    @Test
    void retriesTemporaryFailures() {
        MockLLMProvider mock = new MockLLMProvider()
                .thenFail(new LLMException("overloaded", true))
                .then(LLMResponse.text("ok"));
        LLMClient client = new LLMClient(mock, 0);
        assertEquals("ok", client.complete(request()).text());
        assertEquals(2, mock.received().size());
        assertEquals(1, client.usage().snapshot().get("mock-offline-planner").calls());
    }

    @Test
    void givesUpAfterThreeAttempts() {
        MockLLMProvider mock = new MockLLMProvider()
                .thenFail(new LLMException("down", true))
                .thenFail(new LLMException("down", true))
                .thenFail(new LLMException("down", true));
        LLMClient client = new LLMClient(mock, 0);
        assertThrows(LLMUnavailableException.class, () -> client.complete(request()));
        assertEquals(3, mock.received().size());
    }

    @Test
    void doesNotRetryPermanentErrors() {
        MockLLMProvider mock = new MockLLMProvider().thenFail(new LLMException("bad key", false));
        LLMClient client = new LLMClient(mock, 0);
        assertThrows(LLMUnavailableException.class, () -> client.complete(request()));
        assertEquals(1, mock.received().size());
    }

    @Test
    void providerCanBeSwappedAtRuntime() {
        LLMClient client = new LLMClient(new MockLLMProvider().then(LLMResponse.text("first")), 0);
        client.setProvider(new MockLLMProvider().then(LLMResponse.text("second")));
        assertEquals("second", client.complete(request()).text());
    }
}
