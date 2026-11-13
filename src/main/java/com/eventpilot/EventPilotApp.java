package com.eventpilot;

import com.eventpilot.agent.AgentOrchestrator;
import com.eventpilot.agent.CommandMapper;
import com.eventpilot.agent.GuardrailValidator;
import com.eventpilot.agent.llm.LLMClient;
import com.eventpilot.agent.llm.LLMProvider;
import com.eventpilot.agent.llm.LLMProviderFactory;
import com.eventpilot.agent.memory.MemoryManager;
import com.eventpilot.agent.offline.OfflinePlanner;
import com.eventpilot.agent.rag.KnowledgeBase;
import com.eventpilot.agent.rag.KnowledgeBaseIndexer;
import com.eventpilot.agent.tools.AttendeeQueryTool;
import com.eventpilot.agent.tools.BudgetCalculatorTool;
import com.eventpilot.agent.tools.CateringCalculatorTool;
import com.eventpilot.agent.tools.ConflictCheckTool;
import com.eventpilot.agent.tools.SpeakerAvailabilityTool;
import com.eventpilot.agent.tools.ToolRegistry;
import com.eventpilot.agent.tools.VenueSearchTool;
import com.eventpilot.agent.workflow.AgentServices;
import com.eventpilot.application.EventPlannerFacade;
import com.eventpilot.application.EventRepository;
import com.eventpilot.application.command.CommandHistory;
import com.eventpilot.application.event.AlertService;
import com.eventpilot.application.event.EventBus;
import com.eventpilot.domain.service.BudgetTracker;
import com.eventpilot.domain.service.CateringCalculator;
import com.eventpilot.domain.service.ConflictDetector;
import com.eventpilot.domain.service.TaskScheduler;
import com.eventpilot.infra.AppConfig;
import com.eventpilot.infra.AttendeeCsvImporter;
import com.eventpilot.infra.JsonVenueCatalogAdapter;
import com.eventpilot.infra.OutboxFileGateway;
import com.eventpilot.infra.SqliteEventRepository;
import com.eventpilot.infra.VenueDatasetReader;
import java.time.Clock;

/**
 * Composition root: wires interfaces to implementations (dependency inversion).
 * Both the GUI and the CLI start from {@link #bootstrap(AppConfig)}.
 */
public final class EventPilotApp {

    private final AppConfig config;
    private final EventPlannerFacade facade;
    private final EventBus bus;
    private final AlertService alerts;
    private final EventRepository repository;

    private EventPilotApp(AppConfig config, EventPlannerFacade facade, EventBus bus, AlertService alerts,
                          EventRepository repository) {
        this.config = config;
        this.facade = facade;
        this.bus = bus;
        this.alerts = alerts;
        this.repository = repository;
    }

    public static EventPilotApp bootstrap(AppConfig config) {
        return bootstrap(config, new SqliteEventRepository(config.dataDir().resolve("eventpilot.db")),
                LLMProviderFactory.create(config, new OfflinePlanner()), Clock.systemDefaultZone());
    }

    public static EventPilotApp bootstrap(AppConfig config, EventRepository repository, LLMProvider provider, Clock clock) {
        ConflictDetector conflicts = new ConflictDetector();
        BudgetTracker budget = new BudgetTracker();
        CateringCalculator catering = new CateringCalculator();
        TaskScheduler tasks = new TaskScheduler();
        CommandMapper mapper = new CommandMapper();
        JsonVenueCatalogAdapter venues = new JsonVenueCatalogAdapter(new VenueDatasetReader());

        ToolRegistry tools = new ToolRegistry()
                .register(new VenueSearchTool(venues))
                .register(new BudgetCalculatorTool(budget))
                .register(new ConflictCheckTool(conflicts, mapper))
                .register(new SpeakerAvailabilityTool())
                .register(new CateringCalculatorTool(catering))
                .register(new AttendeeQueryTool());
        LLMClient llm = new LLMClient(provider);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        AgentServices agent = new AgentServices(new AgentOrchestrator(llm, tools), new MemoryManager(repository),
                new GuardrailValidator(conflicts, tasks, catering, venues), mapper, knowledgeBase, venues,
                conflicts, budget, catering, tasks, clock);

        EventBus bus = new EventBus();
        AlertService alerts = new AlertService(bus, conflicts, budget, tasks, clock);
        bus.subscribe(alerts);
        bus.subscribe(new KnowledgeBaseIndexer(knowledgeBase));

        EventPlannerFacade facade = new EventPlannerFacade(repository, venues,
                new OutboxFileGateway(config.dataDir().resolve("outbox")), new AttendeeCsvImporter(),
                agent, new CommandHistory(), bus, alerts, llm);
        return new EventPilotApp(config, facade, bus, alerts, repository);
    }

    /** Settings storage used by the front-ends (e.g. the CLI's selected event). */
    public EventRepository repository() {
        return repository;
    }

    /** Start-up check: refreshes alerts (including overdue tasks) for every event. */
    public void runStartupChecks() {
        facade.listEvents().forEach(e -> facade.alerts(e.id()));
    }

    public EventPlannerFacade facade() {
        return facade;
    }

    public AppConfig config() {
        return config;
    }

    public EventBus bus() {
        return bus;
    }

    public AlertService alerts() {
        return alerts;
    }
}
