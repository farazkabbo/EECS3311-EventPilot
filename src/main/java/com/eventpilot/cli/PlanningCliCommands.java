package com.eventpilot.cli;

import com.eventpilot.agent.workflow.AgendaPreferences;
import com.eventpilot.domain.Conflict;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** `venue ...` (F03) and `agenda ...` (F04, F05, F06, F12, UC14). */
public final class PlanningCliCommands {

    private PlanningCliCommands() {
    }

    @Command(name = "venue", description = "F03: AI venue recommendation.",
            subcommands = {Venue.Recommend.class, Venue.Show.class})
    public static class Venue extends BaseCliCommand {
        @Override
        protected int run() {
            ctx.printer().println("Use a subcommand: recommend, show.");
            return 0;
        }

        @Command(name = "recommend", description = "Agent searches the catalog with tools and ranks the top 3.")
        static class Recommend extends BaseCliCommand {
            @Option(names = "--needs", defaultValue = "", description = "Extra needs, e.g. \"wheelchair accessible, stage\"")
            String needs;

            @Override
            protected int run() {
                return reviewAndApply(ctx.facade().recommendVenues(eventId(), needs));
            }
        }

        @Command(name = "show", description = "Show the chosen venue.")
        static class Show extends BaseCliCommand {
            @Override
            protected int run() {
                com.eventpilot.domain.Venue v = ctx.facade().getEvent(eventId()).venue();
                ctx.printer().println(v == null ? "No venue chosen yet - try `venue recommend`." : v.toString()
                        + "\n  " + v.address() + "\n  features: " + v.features() + "\n  rooms: " + v.rooms());
                return 0;
            }
        }
    }

    @Command(name = "agenda", description = "F04/F05/F06/F12: agenda generation, speakers, conflicts, changes, undo/redo.",
            subcommands = {Agenda.Show.class, Agenda.Generate.class, Agenda.Schedule.class, Agenda.Conflicts.class,
                    Agenda.Resolve.class, Agenda.Modify.class, Agenda.Undo.class, Agenda.Redo.class, Agenda.History.class})
    public static class Agenda extends BaseCliCommand {
        @Override
        protected int run() {
            ctx.printer().agenda(ctx.facade().getEvent(eventId()));
            return 0;
        }

        @Command(name = "show", description = "Print the agenda.")
        static class Show extends BaseCliCommand {
            @Override
            protected int run() {
                ctx.printer().agenda(ctx.facade().getEvent(eventId()));
                return 0;
            }
        }

        @Command(name = "generate", description = "F04: AI agenda generation (checked with conflict_check).")
        static class Generate extends BaseCliCommand {
            @Option(names = "--tracks", defaultValue = "1") int tracks;
            @Option(names = "--session", defaultValue = "45", description = "Session length in minutes") int session;
            @Option(names = "--break", description = "Break length (default: your saved preference or 15)") Integer breakMinutes;
            @Option(names = "--lunch", defaultValue = "12:00") String lunch;
            @Option(names = "--topics", split = ",", description = "Comma-separated topics") List<String> topics = new ArrayList<>();
            @Option(names = "--extend", description = "Keep the current agenda and add to it") boolean extend;

            @Override
            protected int run() {
                int brk = breakMinutes != null ? breakMinutes
                        : Integer.parseInt(ctx.facade().preferences().getOrDefault("break_minutes", "15"));
                String id = eventId();
                int code = reviewAndApply(ctx.facade().generateAgenda(id,
                        new AgendaPreferences(tracks, session, brk, lunch, topics, !extend)));
                ctx.printer().agenda(ctx.facade().getEvent(id));
                return code;
            }
        }

        @Command(name = "schedule", description = "F05: AI speaker scheduling.")
        static class Schedule extends BaseCliCommand {
            @Override
            protected int run() {
                return reviewAndApply(ctx.facade().scheduleSpeakers(eventId()));
            }
        }

        @Command(name = "conflicts", description = "F06: detect conflicts (deterministic, no AI).")
        static class Conflicts extends BaseCliCommand {
            @Override
            protected int run() {
                List<Conflict> conflicts = ctx.facade().detectConflicts(eventId());
                if (conflicts.isEmpty()) {
                    ctx.printer().ok("No conflicts found.");
                }
                conflicts.forEach(c -> ctx.printer().println("  " + c + "  items " + c.itemIds()));
                return 0;
            }
        }

        @Command(name = "resolve", description = "F06: ask the agent to fix the conflicts (fix tested on a copy).")
        static class Resolve extends BaseCliCommand {
            @Override
            protected int run() {
                return reviewAndApply(ctx.facade().resolveConflicts(eventId()));
            }
        }

        @Command(name = "modify", description = "F12: change the plan in plain language.")
        static class Modify extends BaseCliCommand {
            @Parameters(arity = "1..*", description = "e.g. \"move the keynote to 2pm and add a 15-minute break after it\"")
            List<String> words;

            @Override
            protected int run() {
                String id = eventId();
                int code = reviewAndApply(ctx.facade().modifyPlan(id, String.join(" ", words)));
                ctx.printer().agenda(ctx.facade().getEvent(id));
                return code;
            }
        }

        @Command(name = "undo", description = "UC14: undo the last applied change.")
        static class Undo extends BaseCliCommand {
            @Override
            protected int run() {
                ctx.facade().undo(eventId()).ifPresentOrElse(d -> ctx.printer().ok("Undid: " + d),
                        () -> ctx.printer().println("Nothing to undo."));
                return 0;
            }
        }

        @Command(name = "redo", description = "UC14: redo the last undone change.")
        static class Redo extends BaseCliCommand {
            @Override
            protected int run() {
                ctx.facade().redo(eventId()).ifPresentOrElse(d -> ctx.printer().ok("Redid: " + d),
                        () -> ctx.printer().println("Nothing to redo."));
                return 0;
            }
        }

        @Command(name = "history", description = "List undoable changes (newest first).")
        static class History extends BaseCliCommand {
            @Override
            protected int run() {
                List<String> h = ctx.facade().undoHistory(eventId());
                if (h.isEmpty()) {
                    ctx.printer().println("No changes yet.");
                }
                for (int i = 0; i < h.size(); i++) {
                    ctx.printer().println("  " + (i + 1) + ". " + h.get(i));
                }
                return 0;
            }
        }
    }
}
