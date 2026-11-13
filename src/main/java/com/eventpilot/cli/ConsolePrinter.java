package com.eventpilot.cli;

import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Speaker;
import java.io.PrintStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Formats domain objects and proposals for the terminal. */
public class ConsolePrinter {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private final PrintStream out;
    private final boolean showTrace;

    public ConsolePrinter(PrintStream out, boolean showTrace) {
        this.out = out;
        this.showTrace = showTrace;
    }

    public void println(String s) {
        out.println(s);
    }

    public void ok(String s) {
        out.println("OK  " + s);
    }

    public void error(String s) {
        out.println("ERROR  " + s);
    }

    public void heading(String s) {
        out.println();
        out.println("== " + s + " " + "=".repeat(Math.max(0, 60 - s.length())));
    }

    public void event(Event e) {
        heading(e.name() + " [" + e.id() + "]");
        out.printf("  Type: %s   Status: %s%n", e.type(), e.status());
        out.printf("  When: %s%n", e.window());
        out.printf("  Where: %s%s%n", e.city().isBlank() ? "-" : e.city(),
                e.venue() == null ? " (no venue yet)" : " - " + e.venue().name() + ", " + e.venue().address());
        out.printf("  Expected attendance: %d   Budget limit: $%.2f%n", e.expectedAttendance(), e.budget().limit());
        out.printf("  Rooms: %s%n", e.rooms().stream().map(r -> r.name() + " (" + r.capacity() + ")").toList());
        out.printf("  Attendees: %d   Speakers: %d   Agenda items: %d   Tasks: %d   Invitations: %d%n",
                e.attendees().size(), e.speakers().size(), e.agenda().size(), e.tasks().size(), e.invitations().size());
        out.printf("  Allowed lifecycle actions: %s%n", e.state().allowedActions());
    }

    public void agenda(Event e) {
        heading("Agenda - " + e.name());
        if (e.agenda().isEmpty()) {
            out.println("  (empty) - try `agenda generate`");
            return;
        }
        for (AgendaItem i : e.agenda().items()) {
            String speaker = i.speakerId() == null ? "" : e.findSpeaker(i.speakerId()).map(Speaker::name).orElse(i.speakerId());
            out.printf("  %-4s %s-%s  %-10s %-34s %-14s %s%n", i.id(), i.start().format(HM), i.end().format(HM),
                    i.type().name().toLowerCase(), trim(i.title(), 34), trim(i.room() == null ? "" : i.room(), 14), speaker);
        }
    }

    public void proposal(PlanProposal p) {
        heading("Proposal: " + p.title());
        if (showTrace && !p.trace().isEmpty()) {
            out.println("  Agent tool calls:");
            p.trace().forEach(t -> out.println("    " + t));
        }
        if (!p.summary().isBlank()) {
            out.println("  " + p.summary());
        }
        if (p.isQuestion()) {
            out.println("  The agent needs more information: " + p.question());
            return;
        }
        out.println(p.alternatives() ? "  Alternatives (choose one):" : "  Changes:");
        List<PlanProposal.Change> changes = p.changes();
        for (int i = 0; i < changes.size(); i++) {
            out.printf("  %2d. %s%n", i + 1, changes.get(i).describe());
            if (!changes.get(i).reason().isBlank()) {
                out.println("      why: " + changes.get(i).reason());
            }
        }
        if (!p.warnings().isEmpty()) {
            out.println("  Checks:");
            for (Warning w : p.warnings()) {
                out.println("    " + w);
            }
        }
    }

    static String trim(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 3) + "...";
    }
}
