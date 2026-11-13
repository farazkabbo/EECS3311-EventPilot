package com.eventpilot.agent.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * Concrete strategy used for repeatable tests and offline demos. Scripted replies
 * are returned first (in order); when the script is empty, the fallback
 * behaviour answers (by default the offline planner, which derives answers from
 * the context in the prompt - see {@code OfflinePlanner}).
 */
public class MockLLMProvider implements LLMProvider {

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<LLMRequest> received = new ArrayList<>();
    private final Function<LLMRequest, LLMResponse> fallback;

    public MockLLMProvider() {
        this(r -> LLMResponse.text("{\"error\": \"no scripted reply\"}"));
    }

    public MockLLMProvider(Function<LLMRequest, LLMResponse> fallback) {
        this.fallback = fallback;
    }

    /** Queues a reply. */
    public MockLLMProvider then(LLMResponse response) {
        script.addLast(response);
        return this;
    }

    /** Queues a failure (to test retries and error handling). */
    public MockLLMProvider thenFail(LLMException failure) {
        script.addLast(failure);
        return this;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String modelFor(LLMRequest.Tier tier) {
        return "mock-offline-planner";
    }

    @Override
    public synchronized LLMResponse chat(LLMRequest request) throws LLMException {
        received.add(request);
        Object next = script.pollFirst();
        if (next instanceof LLMException e) {
            throw e;
        }
        LLMResponse r = next != null ? (LLMResponse) next : fallback.apply(request);
        return new LLMResponse(r.text(), r.toolCalls(), r.stopReason(), r.inputTokens(), r.outputTokens(),
                modelFor(request.tier()));
    }

    public synchronized List<LLMRequest> received() {
        return List.copyOf(received);
    }
}
