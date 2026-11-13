package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentOrchestrator;
import com.eventpilot.agent.CommandMapper;
import com.eventpilot.agent.GuardrailValidator;
import com.eventpilot.agent.llm.LLMClient;
import com.eventpilot.agent.llm.LLMProvider;
import com.eventpilot.agent.memory.MemoryManager;
import com.eventpilot.agent.rag.KnowledgeBase;
import com.eventpilot.agent.tools.AttendeeQueryTool;
import com.eventpilot.agent.tools.BudgetCalculatorTool;
import com.eventpilot.agent.tools.CateringCalculatorTool;
import com.eventpilot.agent.tools.ConflictCheckTool;
import com.eventpilot.agent.tools.SpeakerAvailabilityTool;
import com.eventpilot.agent.tools.ToolRegistry;
import com.eventpilot.agent.tools.VenueSearchTool;
import com.eventpilot.domain.service.BudgetTracker;
import com.eventpilot.domain.service.CateringCalculator;
import com.eventpilot.domain.service.ConflictDetector;
import com.eventpilot.domain.service.TaskScheduler;
import com.eventpilot.infra.InMemoryEventRepository;
import com.eventpilot.infra.JsonVenueCatalogAdapter;
import com.eventpilot.infra.VenueDatasetReader;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Wires real services around a given (usually mock) LLM provider for workflow tests. */
public final class TestServices {

    private TestServices() {
    }

    public static AgentServices with(LLMProvider provider) {
        ConflictDetector conflicts = new ConflictDetector();
        BudgetTracker budget = new BudgetTracker();
        CateringCalculator catering = new CateringCalculator();
        TaskScheduler tasks = new TaskScheduler();
        CommandMapper mapper = new CommandMapper();
        JsonVenueCatalogAdapter venues = new JsonVenueCatalogAdapter(new VenueDatasetReader());
        ToolRegistry registry = new ToolRegistry()
                .register(new VenueSearchTool(venues))
                .register(new BudgetCalculatorTool(budget))
                .register(new ConflictCheckTool(conflicts, mapper))
                .register(new SpeakerAvailabilityTool())
                .register(new CateringCalculatorTool(catering))
                .register(new AttendeeQueryTool());
        Clock clock = Clock.fixed(LocalDate.of(2026, 11, 12).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        return new AgentServices(new AgentOrchestrator(new LLMClient(provider, 0), registry),
                new MemoryManager(new InMemoryEventRepository()),
                new GuardrailValidator(conflicts, tasks, catering, venues), mapper, new KnowledgeBase(), venues,
                conflicts, budget, catering, tasks, clock);
    }
}
