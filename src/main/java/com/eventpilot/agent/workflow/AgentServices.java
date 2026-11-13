package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentOrchestrator;
import com.eventpilot.agent.CommandMapper;
import com.eventpilot.agent.GuardrailValidator;
import com.eventpilot.agent.memory.MemoryManager;
import com.eventpilot.agent.rag.KnowledgeBase;
import com.eventpilot.application.VenueCatalog;
import com.eventpilot.domain.service.BudgetTracker;
import com.eventpilot.domain.service.CateringCalculator;
import com.eventpilot.domain.service.ConflictDetector;
import com.eventpilot.domain.service.TaskScheduler;
import java.time.Clock;

/** The collaborators every workflow may use (passed in once instead of ten constructor parameters). */
public record AgentServices(AgentOrchestrator orchestrator, MemoryManager memory, GuardrailValidator guardrails,
                            CommandMapper mapper, KnowledgeBase knowledgeBase, VenueCatalog venues,
                            ConflictDetector conflicts, BudgetTracker budget, CateringCalculator catering,
                            TaskScheduler tasks, Clock clock) {
}
