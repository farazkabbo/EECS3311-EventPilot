package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.tools.ToolContext;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Template Method pattern - the "Abstract Class". {@link #execute()} fixes the
 * steps every AI feature follows:
 *
 * <ol>
 *   <li>{@link #gatherContext()} - hook: facts, memory, retrieved documents</li>
 *   <li>{@link #shortCircuit(String)} - hook: answer without the LLM when possible</li>
 *   <li>{@link #buildTask(String)} - abstract: instructions, prompt, allowed tools</li>
 *   <li>run the agent loop</li>
 *   <li>{@link #createParser()} - abstract: parse; invalid answers are sent back for repair (max 2)</li>
 *   <li>{@link #validate(Object, AgentResult)} - hook: deterministic guardrails; blocking problems are
 *       sent back once for a corrected answer</li>
 * </ol>
 *
 * Subclasses only supply their prompt, parser and extra checks, so retries,
 * repair, guardrails and the tool trace behave the same in every feature.
 */
public abstract class PlanningWorkflow<T> {

    public static final int MAX_REPAIRS = 2;

    protected final AgentServices services;
    protected final Event event;

    protected PlanningWorkflow(AgentServices services, Event event) {
        this.services = services;
        this.event = event;
    }

    public final WorkflowResult<T> execute() {
        String context = gatherContext();
        Optional<T> direct = shortCircuit(context);
        if (direct.isPresent()) {
            return new WorkflowResult<>(direct.get(), validate(direct.get(), null), List.of());
        }
        AgentTask task = buildTask(context);
        AgentResult run = services.orchestrator().run(task, new ToolContext(event));
        ResponseParser<T> parser = createParser();

        Parsed<T> parsed = parseWithRepair(parser, run);
        List<Warning> warnings = validate(parsed.value, parsed.run);
        if (retryOnBlockingWarnings() && warnings.stream().anyMatch(Warning::isBlocking)) {
            String problems = warnings.stream().filter(Warning::isBlocking).map(Warning::message)
                    .collect(Collectors.joining("\n- ", "- ", ""));
            AgentResult retry = services.orchestrator().followUp(parsed.run,
                    "Your proposal was checked and has blocking problems:\n" + problems
                            + "\nFix them (use the tools to check) and reply again with the complete corrected JSON only.");
            try {
                Parsed<T> second = parseWithRepair(parser, retry);
                List<Warning> secondWarnings = validate(second.value, second.run);
                parsed = second;
                warnings = secondWarnings;
            } catch (WorkflowException e) {
                // keep the first answer; its blocking warnings will be shown to the organizer
            }
        }
        return new WorkflowResult<>(parsed.value, warnings, parsed.run.trace());
    }

    private record Parsed<T>(T value, AgentResult run) {
    }

    private Parsed<T> parseWithRepair(ResponseParser<T> parser, AgentResult run) {
        AgentResult current = run;
        for (int attempt = 0; ; attempt++) {
            if (current.stepLimitReached()) {
                throw new WorkflowException("The agent could not finish within " + com.eventpilot.agent.AgentOrchestrator.MAX_STEPS
                        + " steps. Please refine your request (for example, narrower criteria).");
            }
            try {
                return new Parsed<>(parser.parse(current.finalText()), current);
            } catch (ResponseParser.ParseException e) {
                if (attempt >= MAX_REPAIRS) {
                    throw new WorkflowException("The AI's answer could not be used after " + (MAX_REPAIRS + 1)
                            + " attempts: " + e.getMessage());
                }
                current = services.orchestrator().followUp(current, "Your answer could not be used: " + e.getMessage()
                        + ". Reply again with only the corrected JSON object, in the required format.");
            }
        }
    }

    // ---------------- steps for subclasses ----------------

    /** Hook: the facts the prompt needs. Default: saved preferences and recent conversation. */
    protected String gatherContext() {
        return services.memory().buildMemoryContext(event == null ? null : event.id());
    }

    /** Hook: return a result without calling the LLM (e.g. FAQ with nothing relevant). */
    protected Optional<T> shortCircuit(String context) {
        return Optional.empty();
    }

    protected abstract AgentTask buildTask(String context);

    protected abstract ResponseParser<T> createParser();

    /** Hook: deterministic checks of the parsed result. {@code run} is null for short-circuited results. */
    protected List<Warning> validate(T result, AgentResult run) {
        return new ArrayList<>();
    }

    /** Hook: whether blocking warnings are sent back to the LLM once for a fix (default yes). */
    protected boolean retryOnBlockingWarnings() {
        return true;
    }
}
