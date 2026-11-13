package com.eventpilot.cli;

import com.eventpilot.application.EventPlannerFacade;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Room;
import com.eventpilot.domain.state.LifecycleAction;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** `event ...` - create (F01), list, select, show, lifecycle (UC15), notes and rooms. */
@Command(name = "event", description = "Create, select and manage events.",
        subcommands = {EventCliCommand.Create.class, EventCliCommand.Answer.class, EventCliCommand.New.class,
                EventCliCommand.ListCmd.class, EventCliCommand.Use.class, EventCliCommand.Show.class,
                EventCliCommand.Status.class, EventCliCommand.Note.class, EventCliCommand.Rooms.class,
                EventCliCommand.Delete.class})
public class EventCliCommand extends BaseCliCommand {

    @Override
    protected int run() {
        ctx.printer().println("Use a subcommand: create, answer, new, list, use, show, status, note, rooms, delete.");
        return 0;
    }

    static void report(EventPlannerFacade.CreateEventResult r) {
        if (r.event() != null) {
            ctx.setCurrentEvent(r.event().id());
            ctx.printer().ok(r.message() + " It is now the selected event.");
            ctx.printer().event(r.event());
        } else if (r.needsAnswers()) {
            ctx.printer().println(r.message());
            r.questions().forEach(q -> ctx.printer().println("  ? " + q));
            ctx.printer().println("Answer with: event answer \"<your answers>\"");
        } else {
            ctx.printer().println(r.message());
        }
    }

    @Command(name = "create", description = "F01: create an event from a plain-language description (AI).")
    static class Create extends BaseCliCommand {
        @Parameters(arity = "1..*", description = "Description, e.g. \"AI meetup for 120 people on Dec 5, 6-9pm, budget $3000\"")
        List<String> words;

        @Override
        protected int run() {
            report(ctx.facade().createEventFromDescription(String.join(" ", words)));
            return 0;
        }
    }

    @Command(name = "answer", description = "F01: answer the agent's follow-up questions.")
    static class Answer extends BaseCliCommand {
        @Parameters(arity = "1..*")
        List<String> words;

        @Override
        protected int run() {
            report(ctx.facade().answerClarification(String.join(" ", words)));
            return 0;
        }
    }

    @Command(name = "new", description = "Create an event manually (no AI).")
    static class New extends BaseCliCommand {
        @Option(names = "--name", required = true) String name;
        @Option(names = "--type", defaultValue = "event") String type;
        @Option(names = "--start", required = true, description = "e.g. 2026-12-05T09:00") LocalDateTime start;
        @Option(names = "--end", required = true) LocalDateTime end;
        @Option(names = "--attendance", required = true) int attendance;
        @Option(names = "--budget", defaultValue = "0") double budget;
        @Option(names = "--city", defaultValue = "") String city;

        @Override
        protected int run() {
            Event e = ctx.facade().createEvent(name, type, start, end, attendance, budget, city);
            ctx.setCurrentEvent(e.id());
            ctx.printer().ok("Created " + e.id() + " and selected it.");
            return 0;
        }
    }

    @Command(name = "list", description = "List events.")
    static class ListCmd extends BaseCliCommand {
        @Override
        protected int run() {
            String current;
            try {
                current = ctx.resolveEvent(null);
            } catch (DomainException e) {
                current = "";
            }
            List<Event> events = ctx.facade().listEvents();
            if (events.isEmpty()) {
                ctx.printer().println("No events yet. Try: event create \"<description>\"");
            }
            for (Event e : events) {
                ctx.printer().println((e.id().equals(current) ? "* " : "  ") + e);
            }
            return 0;
        }
    }

    @Command(name = "use", description = "Select the event the other commands work on.")
    static class Use extends BaseCliCommand {
        @Parameters(index = "0") String id;

        @Override
        protected int run() {
            Event e = ctx.facade().getEvent(id);
            ctx.setCurrentEvent(e.id());
            ctx.printer().ok("Selected " + e);
            return 0;
        }
    }

    @Command(name = "show", description = "Show the selected event.")
    static class Show extends BaseCliCommand {
        @Override
        protected int run() {
            ctx.printer().event(ctx.facade().getEvent(eventId()));
            return 0;
        }
    }

    @Command(name = "status", description = "UC15: change lifecycle state: start-planning | confirm | reopen | complete | cancel.")
    static class Status extends BaseCliCommand {
        @Parameters(index = "0") String action;

        @Override
        protected int run() throws DomainException {
            LifecycleAction a = LifecycleAction.parse(action);
            if (a == LifecycleAction.CANCEL && !common.approve()) {
                ctx.printer().println("Cancel the event? It becomes read-only. [y/N]: ");
                try {
                    String answer = ctx.in().readLine();
                    if (answer == null || !answer.trim().toLowerCase().startsWith("y")) {
                        ctx.printer().println("Not cancelled.");
                        return 0;
                    }
                } catch (java.io.IOException e) {
                    return 1;
                }
            }
            Event e = ctx.facade().transitionEvent(eventId(), a);
            ctx.printer().ok("Event is now " + e.status() + ". Next possible: " + e.state().allowedActions());
            return 0;
        }
    }

    @Command(name = "note", description = "Add organizer information the FAQ agent can use (e.g. parking).")
    static class Note extends BaseCliCommand {
        @Parameters(arity = "1..*") List<String> words;

        @Override
        protected int run() {
            ctx.facade().addNote(eventId(), String.join(" ", words));
            ctx.printer().ok("Note added.");
            return 0;
        }
    }

    @Command(name = "rooms", description = "Set rooms, e.g. rooms \"Hall A:150\" \"Room B:40\".")
    static class Rooms extends BaseCliCommand {
        @Parameters(arity = "1..*") List<String> rooms;

        @Override
        protected int run() {
            List<Room> list = new ArrayList<>();
            for (String r : rooms) {
                String[] parts = r.split(":");
                if (parts.length != 2) {
                    throw new DomainException("Room must look like \"Name:capacity\": " + r);
                }
                list.add(new Room(parts[0].trim(), Integer.parseInt(parts[1].trim())));
            }
            ctx.facade().setRooms(eventId(), list);
            ctx.printer().ok("Rooms set: " + list);
            return 0;
        }
    }

    @Command(name = "delete", description = "Delete an event permanently.")
    static class Delete extends BaseCliCommand {
        @Parameters(index = "0") String id;

        @Override
        protected int run() {
            ctx.facade().deleteEvent(id);
            ctx.printer().ok("Deleted " + id);
            return 0;
        }
    }
}
