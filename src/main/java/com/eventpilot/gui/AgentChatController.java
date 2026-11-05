package com.eventpilot.gui;

import com.eventpilot.agent.workflow.FaqAnswer;
import com.eventpilot.application.EventPlannerFacade;
import java.util.function.Consumer;

/** Chat actions: create an event from text (F01), change the plan (F12), ask the FAQ agent (F11). */
public class AgentChatController extends BaseController {

    public AgentChatController(GuiContext ctx) {
        super(ctx);
    }

    /** F01: a new description, or - when the agent asked questions - the answers to them. */
    public void submitMessage(String text, Consumer<String> reply) {
        boolean answering = ctx.facade().pendingDescription().isPresent();
        runAsync(answering ? "Agent is reading your answers" : "Agent is extracting the event",
                () -> answering ? ctx.facade().answerClarification(text) : ctx.facade().createEventFromDescription(text),
                result -> handle(result, reply));
    }

    public void cancelPending(Consumer<String> reply) {
        ctx.facade().cancelPendingDescription();
        reply.accept("OK - the pending description was discarded. Describe a new event any time.");
    }

    private void handle(EventPlannerFacade.CreateEventResult r, Consumer<String> reply) {
        if (r.event() != null) {
            ctx.selectedEventId().set(r.event().id());
            reply.accept(r.message() + "\n" + r.event().window() + ", " + r.event().expectedAttendance() + " people, budget $"
                    + String.format("%.0f", r.event().budget().limit()) + (r.event().city().isBlank() ? "" : ", " + r.event().city())
                    + ".\nNext: choose a venue (Venue tab) or generate an agenda.");
        } else if (r.needsAnswers()) {
            reply.accept(r.message() + "\n- " + String.join("\n- ", r.questions()) + "\n(Type your answers below.)");
        } else {
            reply.accept(r.message());
        }
    }

    /** F12 from the chat. */
    public void onModifyPlan(String instruction, Consumer<String> reply) {
        requireEvent().ifPresent(id -> runAsync("Agent is interpreting your change",
                () -> ctx.facade().modifyPlan(id, instruction), p -> {
                    if (p.isQuestion()) {
                        reply.accept(p.question());
                    } else {
                        reply.accept(p.summary().isBlank() ? "Here is my proposal." : p.summary());
                        reviewAndApply(p);
                    }
                }));
    }

    /** F11 from the chat (FAQ mode). */
    public void onAskFaq(String question, Consumer<String> reply) {
        requireEvent().ifPresent(id -> runAsync("FAQ agent is looking it up",
                () -> ctx.facade().askFaq(id, question), (FaqAnswer a) -> reply.accept(a.answer()
                        + (a.sources().isEmpty() ? "" : "\n[sources: " + String.join(", ", a.sources()) + "]")
                        + (a.escalated() ? "\n(Forwarded to the organizer - see Dashboard alerts.)" : ""))));
    }
}
