package com.eventpilot.gui;

import com.eventpilot.agent.llm.LLMUnavailableException;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.domain.DomainException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;

/**
 * MVC controller base. Turns GUI actions into facade calls. AI calls run on a
 * background thread ({@link #runAsync}) so the window stays responsive; results
 * and errors come back on the JavaFX thread.
 */
public abstract class BaseController {

    private static final ExecutorService WORKERS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "eventpilot-worker");
        t.setDaemon(true);
        return t;
    });

    protected final GuiContext ctx;

    protected BaseController(GuiContext ctx) {
        this.ctx = ctx;
    }

    /** The selected event id, or an error dialog when none is selected. */
    protected Optional<String> requireEvent() {
        String id = ctx.selectedEventId().get();
        if (id == null) {
            error("Select or create an event first.");
            return Optional.empty();
        }
        return Optional.of(id);
    }

    protected <T> void runAsync(String what, Supplier<T> work, Consumer<T> onSuccess) {
        ctx.busy().set(true);
        ctx.status().set(what + " ...");
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        task.setOnSucceeded(e -> {
            ctx.busy().set(false);
            ctx.status().set(what + " - done");
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            ctx.busy().set(false);
            ctx.status().set(what + " - failed");
            Throwable t = task.getException();
            if (t instanceof LLMUnavailableException) {
                error(t.getMessage() + "\n\nNothing was changed. You can do this step manually, "
                        + "or set LLM_PROVIDER=mock in .env to use the offline planner.");
            } else {
                error(t.getMessage() == null ? t.toString() : t.getMessage());
            }
        });
        WORKERS.submit(task);
    }

    /** Runs a quick facade call on the JavaFX thread and reports rule violations as dialogs. */
    protected void run(String what, Runnable action) {
        try {
            action.run();
            ctx.status().set(what);
        } catch (DomainException | IllegalArgumentException e) {
            error(e.getMessage());
        }
    }

    /** UC13: shows the review dialog and applies what the organizer approves as one undoable step. */
    protected void reviewAndApply(PlanProposal proposal) {
        if (proposal.isQuestion()) {
            info("The agent needs more information", proposal.question());
            return;
        }
        Optional<List<Integer>> decision = new GuiProposalReviewer().review(proposal);
        if (decision.isEmpty()) {
            ctx.status().set("Proposal rejected - nothing changed");
            return;
        }
        run("Applied " + proposal.title(), () ->
                ctx.status().set(ctx.facade().applyProposal(proposal, decision.get()) + "  (Ctrl+Z to undo)"));
    }

    public static void error(String message) {
        Runnable show = () -> {
            Alert a = new Alert(Alert.AlertType.ERROR, message);
            a.setHeaderText("EventPilot");
            a.showAndWait();
        };
        if (Platform.isFxApplicationThread()) {
            show.run();
        } else {
            Platform.runLater(show);
        }
    }

    public static void info(String header, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message);
        a.setHeaderText(header);
        a.showAndWait();
    }
}
