package com.eventpilot.cli;

import com.eventpilot.agent.llm.LLMUnavailableException;
import com.eventpilot.agent.workflow.WorkflowException;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.domain.DomainException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * Base of every CLI command: turns arguments into ONE facade call and prints
 * the result. Errors from business rules or the AI become readable messages.
 */
public abstract class BaseCliCommand implements Callable<Integer> {

    /**
     * Options shared by all commands. They may be given before or after the subcommand
     * ({@code eventpilot -y agenda generate} or {@code eventpilot agenda generate -y}), so the
     * values are kept for the whole invocation.
     */
    public static class Common {
        private static String invocationEvent;
        private static boolean invocationYes;

        String eventId;
        boolean yes;

        @CommandLine.Option(names = {"-e", "--event"}, description = "Event id (default: the one chosen with `event use`).")
        void setEventId(String id) {
            eventId = id;
            invocationEvent = id;
        }

        @CommandLine.Option(names = {"-y", "--yes"}, description = "Approve agent proposals without asking.")
        void setYes(boolean value) {
            yes = value;
            invocationYes |= value;
        }

        /** Called before each command line is executed. */
        public static void reset() {
            invocationEvent = null;
            invocationYes = false;
        }

        String event() {
            return eventId != null ? eventId : invocationEvent;
        }

        boolean approve() {
            return yes || invocationYes;
        }
    }

    @CommandLine.Mixin
    protected Common common = new Common();

    protected static CliContext ctx;

    public static void setContext(CliContext context) {
        ctx = context;
    }

    @Override
    public final Integer call() {
        try {
            return run();
        } catch (DomainException | WorkflowException | IllegalArgumentException e) {
            ctx.printer().error(e.getMessage());
            return 1;
        } catch (LLMUnavailableException e) {
            ctx.printer().error(e.getMessage() + "\n       Nothing was changed. You can do this step manually, "
                    + "or switch provider in .env (LLM_PROVIDER=mock works offline).");
            return 2;
        }
    }

    protected abstract int run();

    protected String eventId() {
        return ctx.resolveEvent(common.event());
    }

    /** UC13 in the CLI: show the proposal, ask y/N/select, apply the approved part. */
    protected int reviewAndApply(PlanProposal proposal) {
        ConsoleProposalReviewer reviewer = new ConsoleProposalReviewer(ctx.printer(), ctx.in(), common.approve());
        Optional<List<Integer>> decision = reviewer.review(proposal);
        if (proposal.isQuestion()) {
            ctx.printer().println("  Answer by running the command again with more detail.");
            return 0;
        }
        if (decision.isEmpty()) {
            ctx.printer().println("  Not applied - nothing changed.");
            return 0;
        }
        ctx.printer().ok(ctx.facade().applyProposal(proposal, decision.get()) + "  (undo with `agenda undo`)");
        return 0;
    }
}
