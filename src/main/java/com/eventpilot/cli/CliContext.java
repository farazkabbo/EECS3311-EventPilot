package com.eventpilot.cli;

import com.eventpilot.EventPilotApp;
import com.eventpilot.application.EventPlannerFacade;
import com.eventpilot.application.EventRepository;
import com.eventpilot.domain.DomainException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** What every CLI command needs: the facade, the printer, stdin and the selected event. */
public class CliContext {

    private static final String CURRENT_EVENT = "cli.current_event";

    private final EventPilotApp app;
    private final ConsolePrinter printer;
    private final PrintStream out;
    private final BufferedReader in;

    public CliContext(EventPilotApp app, PrintStream out, java.io.InputStream in) {
        this.app = app;
        this.out = out;
        this.printer = new ConsolePrinter(out, true);
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    public EventPlannerFacade facade() {
        return app.facade();
    }

    public EventPilotApp app() {
        return app;
    }

    public ConsolePrinter printer() {
        return printer;
    }

    public PrintStream out() {
        return out;
    }

    public BufferedReader in() {
        return in;
    }

    /** The event given with --event, else the one selected with `event use`. */
    public String resolveEvent(String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return facade().getEvent(explicit).id();
        }
        return settings().getValue("_global", CURRENT_EVENT)
                .filter(id -> facade().listEvents().stream().anyMatch(e -> e.id().equals(id)))
                .orElseThrow(() -> new DomainException(
                        "No event selected. Use `event use <id>` or --event <id> (see `event list`)."));
    }

    public void setCurrentEvent(String id) {
        settings().putValue("_global", CURRENT_EVENT, id);
    }

    private EventRepository settings() {
        return app.repository();
    }
}
