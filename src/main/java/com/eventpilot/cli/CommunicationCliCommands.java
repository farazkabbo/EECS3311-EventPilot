package com.eventpilot.cli;

import com.eventpilot.agent.llm.UsageTracker;
import com.eventpilot.agent.workflow.FaqAnswer;
import com.eventpilot.application.EventPlannerFacade;
import com.eventpilot.application.event.Alert;
import com.eventpilot.domain.Invitation;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** `invite ...` (F10), `ask` (F11), `alerts`, `pref`, `ai`. */
public final class CommunicationCliCommands {

    private CommunicationCliCommands() {
    }

    @Command(name = "invite", description = "F10: draft and send invitations (event must be PLANNING or CONFIRMED).",
            subcommands = {Invite.Draft.class, Invite.ListCmd.class, Invite.Edit.class, Invite.Send.class})
    public static class Invite extends BaseCliCommand {
        @Override
        protected int run() {
            return ListCmd.print(eventId(), false);
        }

        @Command(name = "draft", description = "AI drafts per-group invitations in a tone: formal | friendly | playful.")
        static class Draft extends BaseCliCommand {
            @Option(names = "--tone", defaultValue = "friendly") String tone;

            @Override
            protected int run() {
                List<Invitation> drafts = ctx.facade().draftInvitations(eventId(), tone);
                ctx.printer().ok(drafts.size() + " invitation(s) drafted (all contain the real date, time and venue).");
                return ListCmd.print(eventId(), true);
            }
        }

        @Command(name = "list", description = "Show invitations and their delivery status.")
        static class ListCmd extends BaseCliCommand {
            @Option(names = "--full", description = "Print the full text") boolean full;

            @Override
            protected int run() {
                return print(eventId(), full);
            }

            static int print(String eventId, boolean full) {
                List<Invitation> list = ctx.facade().getEvent(eventId).invitations();
                ctx.printer().heading("Invitations");
                if (list.isEmpty()) {
                    ctx.printer().println("  None yet - try `invite draft --tone formal`.");
                }
                java.util.Set<String> shownGroups = new java.util.HashSet<>();
                for (Invitation i : list) {
                    ctx.printer().println(String.format("  %-8s %-32s %-7s %s%s", i.status(), i.email(), i.group(), i.subject(),
                            i.error() == null ? "" : "  (" + i.error() + ")"));
                    if (full && shownGroups.add(i.group())) {
                        ctx.printer().println("    ---\n    " + i.body().replace("\n", "\n    ") + "\n    ---");
                    }
                }
                return 0;
            }
        }

        @Command(name = "edit", description = "Edit one drafted invitation.")
        static class Edit extends BaseCliCommand {
            @Parameters(index = "0") String email;
            @Option(names = "--subject", required = true) String subject;
            @Option(names = "--body", required = true) String body;

            @Override
            protected int run() {
                ctx.facade().editInvitation(eventId(), email, subject, body);
                ctx.printer().ok("Edited invitation to " + email);
                return 0;
            }
        }

        @Command(name = "send", description = "Send drafted invitations through the e-mail gateway (outbox).")
        static class Send extends BaseCliCommand {
            @Override
            protected int run() {
                EventPlannerFacade.DeliveryReport r = ctx.facade().sendInvitations(eventId());
                ctx.printer().ok("Delivery report: " + r.summary());
                r.failed().forEach(f -> ctx.printer().println("  FAILED " + f));
                return 0;
            }
        }
    }

    @Command(name = "ask", description = "F11: ask the FAQ agent a question about the event (answers cite sources).")
    public static class Ask extends BaseCliCommand {
        @Parameters(arity = "1..*") List<String> words;

        @Override
        protected int run() {
            FaqAnswer a = ctx.facade().askFaq(eventId(), String.join(" ", words));
            ctx.printer().println(a.answer());
            if (!a.sources().isEmpty()) {
                ctx.printer().println("  sources: " + a.sources());
            }
            if (a.escalated()) {
                ctx.printer().println("  (escalated to the organizer - see `alerts`)");
            }
            return 0;
        }
    }

    @Command(name = "alerts", description = "Conflicts, budget alerts, overdue tasks and unanswered FAQ questions.")
    public static class Alerts extends BaseCliCommand {
        @Option(names = "--clear-faq", description = "Mark escalated FAQ questions as handled") boolean clearFaq;

        @Override
        protected int run() {
            String id = eventId();
            if (clearFaq) {
                ctx.facade().clearEscalations(id);
            }
            List<Alert> alerts = ctx.facade().alerts(id);
            ctx.printer().heading("Alerts");
            if (alerts.isEmpty()) {
                ctx.printer().ok("No alerts.");
            }
            alerts.forEach(a -> ctx.printer().println("  " + a));
            return 0;
        }
    }

    @Command(name = "pref", description = "Organizer preferences remembered by the agent: pref [key value]")
    public static class Pref extends BaseCliCommand {
        @Parameters(arity = "0..2") List<String> args;

        @Override
        protected int run() {
            if (args != null && args.size() == 2) {
                ctx.facade().setPreference(args.get(0), args.get(1));
                ctx.printer().ok("Saved preference " + args.get(0) + " = " + args.get(1));
            }
            Map<String, String> prefs = ctx.facade().preferences();
            ctx.printer().println(prefs.isEmpty() ? "No preferences saved." : "Preferences: " + prefs);
            return 0;
        }
    }

    @Command(name = "ai", description = "Show the configured AI provider/models and token usage of this session.")
    public static class Ai extends BaseCliCommand {
        @Override
        protected int run() {
            ctx.printer().println("AI provider: " + ctx.facade().aiDescription());
            Map<String, UsageTracker.ModelUsage> usage = ctx.facade().usage();
            usage.values().forEach(u -> ctx.printer().println(String.format(
                    "  %s: %d calls, %d input / %d output tokens, est. $%.4f", u.model(), u.calls(),
                    u.inputTokens(), u.outputTokens(), u.estimatedCost())));
            return 0;
        }
    }
}
