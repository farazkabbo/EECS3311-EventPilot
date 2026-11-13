package com.eventpilot.cli;

import com.eventpilot.agent.TimeParser;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Speaker;
import com.eventpilot.domain.TimeWindow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** `speaker ...` - speakers and their availability (input for F05). */
@Command(name = "speaker", description = "Manage speakers and their availability.",
        subcommands = {SpeakerCliCommand.Add.class, SpeakerCliCommand.ListCmd.class})
public class SpeakerCliCommand extends BaseCliCommand {

    @Override
    protected int run() {
        ctx.printer().println("Use a subcommand: add, list.");
        return 0;
    }

    @Command(name = "add", description = "Add a speaker, e.g. --available 09:00-12:00 --available 14:00-16:00")
    static class Add extends BaseCliCommand {
        @Option(names = "--name", required = true) String name;
        @Option(names = "--topic", defaultValue = "") String topic;
        @Option(names = "--email") String email;
        @Option(names = "--available", description = "HH:MM-HH:MM on the event day (repeatable; none = all day)")
        List<String> windows = new ArrayList<>();

        @Override
        protected int run() {
            Event e = ctx.facade().getEvent(eventId());
            LocalDate day = e.start().toLocalDate();
            List<TimeWindow> availability = new ArrayList<>();
            for (String w : windows) {
                String[] parts = w.split("-");
                if (parts.length != 2) {
                    throw new DomainException("Availability must look like 09:00-12:00: " + w);
                }
                availability.add(new TimeWindow(day.atTime(TimeParser.parseTime(parts[0])), day.atTime(TimeParser.parseTime(parts[1]))));
            }
            Speaker s = ctx.facade().addSpeaker(e.id(), name, topic, email, availability);
            ctx.printer().ok("Added speaker " + s);
            return 0;
        }
    }

    @Command(name = "list", description = "List speakers.")
    static class ListCmd extends BaseCliCommand {
        @Override
        protected int run() {
            Event e = ctx.facade().getEvent(eventId());
            ctx.printer().heading("Speakers - " + e.name());
            e.speakers().forEach(s -> ctx.printer().println("  " + s));
            return 0;
        }
    }
}
