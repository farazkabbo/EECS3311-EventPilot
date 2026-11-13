package com.eventpilot.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.agent.llm.ChatMessage;
import com.eventpilot.agent.llm.LLMClient;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.agent.llm.LLMResponse;
import com.eventpilot.agent.llm.MockLLMProvider;
import com.eventpilot.agent.memory.MemoryManager;
import com.eventpilot.agent.rag.KnowledgeBase;
import com.eventpilot.agent.tools.ToolContext;
import com.eventpilot.agent.tools.ToolRegistry;
import com.eventpilot.agent.tools.VenueSearchTool;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Event;
import com.eventpilot.infra.InMemoryEventRepository;
import com.eventpilot.infra.JsonVenueCatalogAdapter;
import com.eventpilot.infra.VenueDatasetReader;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentLoopTest {

    static final LocalDateTime NINE = LocalDateTime.of(2026, 12, 5, 9, 0);

    private Event event() {
        return new Event("AI Night", "meetup", NINE, NINE.plusHours(8), 120, 10000, "Toronto");
    }

    private ToolRegistry tools() {
        return new ToolRegistry().register(new VenueSearchTool(new JsonVenueCatalogAdapter(new VenueDatasetReader())));
    }

    private AgentTask task() {
        return new AgentTask("test", "system", "find a venue", List.of("venue_search"), LLMRequest.Tier.MAIN, 1000);
    }

    @Test
    void loopRunsToolsAndFeedsResultsBack() {
        MockLLMProvider mock = new MockLLMProvider()
                .then(LLMResponse.tools(List.of(new ChatMessage.ToolCall("c1", "venue_search", "{\"min_capacity\": \"x\"}"))))
                .then(LLMResponse.tools(List.of(new ChatMessage.ToolCall("c2", "venue_search", "{\"city\": \"Toronto\", \"min_capacity\": 120}"))))
                .then(LLMResponse.text("{\"done\": true}"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(new LLMClient(mock, 0), tools());
        AgentResult result = orchestrator.run(task(), new ToolContext(event()));

        assertEquals("{\"done\": true}", result.finalText());
        assertEquals(2, result.trace().size());
        assertTrue(result.trace().get(0).error(), "invalid arguments reported, not run");
        assertFalse(result.trace().get(1).error());
        assertFalse(result.toolContext().returnedVenueIds().isEmpty());
        // third request contains both tool results
        List<ChatMessage> sent = mock.received().get(2).messages();
        assertEquals(5, sent.size());
        assertEquals(ChatMessage.Role.TOOL_RESULTS, sent.get(4).role());
    }

    @Test
    void stepLimitStopsEndlessToolUse() {
        MockLLMProvider mock = new MockLLMProvider(r -> LLMResponse.tools(List.of(
                new ChatMessage.ToolCall("c", "venue_search", "{\"min_capacity\": 10}"))));
        AgentResult result = new AgentOrchestrator(new LLMClient(mock, 0), tools()).run(task(), new ToolContext(event()));
        assertTrue(result.stepLimitReached());
        assertEquals(AgentOrchestrator.MAX_STEPS, mock.received().size());
        assertTrue(mock.received().get(AgentOrchestrator.MAX_STEPS - 1).tools().isEmpty(), "last step offers no tools");
    }

    @Test
    void followUpContinuesConversation() {
        MockLLMProvider mock = new MockLLMProvider().then(LLMResponse.text("not json")).then(LLMResponse.text("{}"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(new LLMClient(mock, 0), tools());
        AgentResult first = orchestrator.run(task(), new ToolContext(event()));
        AgentResult second = orchestrator.followUp(first, "Return valid JSON only.");
        assertEquals("{}", second.finalText());
        assertEquals(3, mock.received().get(1).messages().size());
    }

    @Test
    void memoryKeepsPreferencesAndRecentTurns() {
        MemoryManager memory = new MemoryManager(new InMemoryEventRepository());
        memory.setPreference("Break minutes", "15");
        for (int i = 0; i < 20; i++) {
            memory.remember("e1", "user", "message " + i);
        }
        assertEquals(MemoryManager.MAX_TURNS, memory.conversation("e1").size());
        String context = memory.buildMemoryContext("e1");
        assertTrue(context.contains("break_minutes: 15"));
        assertTrue(context.contains("message 19") && !context.contains("message 3\n"));
    }

    @Test
    void knowledgeBaseRetrievesRelevantChunksOnly() {
        Event event = event();
        event.agenda().add(new AgendaItem(null, "Opening keynote", AgendaItemType.KEYNOTE, NINE, NINE.plusHours(1), "Main Hall", null, 100));
        event.agenda().add(new AgendaItem(null, "Lunch", AgendaItemType.MEAL, NINE.plusHours(3), NINE.plusHours(4), "Main Hall", null, 0));
        event.addNote("Free parking is available in the north lot.");
        KnowledgeBase kb = new KnowledgeBase();
        kb.index(event);

        List<KnowledgeBase.Hit> parking = kb.search(event.id(), "Is there parking?", 5);
        assertEquals("note:1", parking.get(0).chunk().id());
        List<KnowledgeBase.Hit> keynote = kb.search(event.id(), "When does the keynote start?", 5);
        assertEquals("agenda:a1", keynote.get(0).chunk().id());
        assertTrue(kb.search(event.id(), "quantum chromodynamics", 5).isEmpty());
    }
}
