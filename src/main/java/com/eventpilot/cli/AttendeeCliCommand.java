package com.eventpilot.cli;

import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Event;
import com.eventpilot.infra.AttendeeCsvImporter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** `attendee ...` - F02 attendee and RSVP management. */
@Command(name = "attendee", description = "F02: manage attendees and RSVPs.",
        subcommands = {AttendeeCliCommand.Add.class, AttendeeCliCommand.Import.class, AttendeeCliCommand.Rsvp.class,
                AttendeeCliCommand.ListCmd.class, AttendeeCliCommand.Remove.class})
public class AttendeeCliCommand extends BaseCliCommand {

    @Override
    protected int run() {
        ctx.printer().println("Use a subcommand: add, import, rsvp, list, remove.");
        return 0;
    }

    @Command(name = "add", description = "Add one attendee.")
    static class Add extends BaseCliCommand {
        @Option(names = "--name", required = true) String name;
        @Option(names = "--email", required = true) String email;
        @Option(names = "--role", defaultValue = "GUEST", description = "GUEST, SPEAKER, VIP, STAFF") Attendee.Role role;
        @Option(names = "--diet", split = ";", description = "e.g. vegan;gluten-free") List<String> diet = new ArrayList<>();
        @Option(names = "--access", defaultValue = "") String access;
        @Option(names = "--rsvp", defaultValue = "INVITED") Attendee.Rsvp rsvp;

        @Override
        protected int run() {
            Attendee a = ctx.facade().addAttendee(eventId(), name, email, role, diet, access, rsvp);
            ctx.printer().ok("Added " + a);
            return 0;
        }
    }

    @Command(name = "import", description = "Import attendees from CSV (" + AttendeeCsvImporter.EXPECTED_HEADER + ").")
    static class Import extends BaseCliCommand {
        @Parameters(index = "0") Path file;

        @Override
        protected int run() {
            AttendeeCsvImporter.ImportReport r = ctx.facade().importAttendees(eventId(), file);
            ctx.printer().ok(r.summary());
            r.rejected().forEach(e -> ctx.printer().println("  rejected " + e));
            r.duplicates().forEach(d -> ctx.printer().println("  duplicate " + d));
            return 0;
        }
    }

    @Command(name = "rsvp", description = "Set RSVP: rsvp <id|email> ACCEPTED|TENTATIVE|DECLINED|INVITED")
    static class Rsvp extends BaseCliCommand {
        @Parameters(index = "0") String who;
        @Parameters(index = "1") Attendee.Rsvp status;

        @Override
        protected int run() {
            ctx.printer().ok("Updated " + ctx.facade().updateRsvp(eventId(), who, status));
            return 0;
        }
    }

    @Command(name = "list", description = "List attendees with RSVP counts.")
    static class ListCmd extends BaseCliCommand {
        @Override
        protected int run() {
            Event e = ctx.facade().getEvent(eventId());
            ctx.printer().heading("Attendees - " + e.name());
            Map<Attendee.Rsvp, Integer> counts = new EnumMap<>(Attendee.Rsvp.class);
            for (Attendee a : e.attendees()) {
                ctx.printer().println("  " + a + (a.accessibility().isBlank() ? "" : " access=" + a.accessibility()));
                counts.merge(a.rsvp(), 1, Integer::sum);
            }
            ctx.printer().println("  RSVP: " + counts);
            return 0;
        }
    }

    @Command(name = "remove", description = "Remove an attendee.")
    static class Remove extends BaseCliCommand {
        @Parameters(index = "0") String who;

        @Override
        protected int run() {
            ctx.facade().removeAttendee(eventId(), who);
            ctx.printer().ok("Removed " + who);
            return 0;
        }
    }
}
