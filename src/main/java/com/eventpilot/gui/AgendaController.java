package com.eventpilot.gui;

import com.eventpilot.agent.TimeParser;
import com.eventpilot.agent.workflow.AgendaPreferences;
import com.eventpilot.domain.Conflict;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.TimeWindow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Agenda actions: generate (F04), schedule speakers (F05), conflicts (F06), plain-language change (F12). */
public class AgendaController extends BaseController {

    public AgendaController(GuiContext ctx) {
        super(ctx);
    }

    public void onGenerateAgenda(AgendaPreferences prefs) {
        requireEvent().ifPresent(id -> runAsync("Agent is designing the agenda",
                () -> ctx.facade().generateAgenda(id, prefs), this::reviewAndApply));
    }

    public void onScheduleSpeakers() {
        requireEvent().ifPresent(id -> runAsync("Agent is scheduling speakers",
                () -> ctx.facade().scheduleSpeakers(id), this::reviewAndApply));
    }

    public void onDetectConflicts(Consumer<List<Conflict>> show) {
        requireEvent().ifPresent(id -> {
            List<Conflict> conflicts = ctx.facade().detectConflicts(id);
            ctx.status().set(conflicts.isEmpty() ? "No conflicts found" : conflicts.size() + " conflict(s) found");
            show.accept(conflicts);
        });
    }

    public void onResolveConflicts() {
        requireEvent().ifPresent(id -> runAsync("Agent is resolving conflicts",
                () -> ctx.facade().resolveConflicts(id), this::reviewAndApply));
    }

    public void onModifyPlan(String instruction) {
        requireEvent().ifPresent(id -> runAsync("Agent is interpreting your change",
                () -> ctx.facade().modifyPlan(id, instruction), this::reviewAndApply));
    }

    /** Availability text like "09:00-12:00, 14:00-16:00" (empty = all day). */
    public void onAddSpeaker(String name, String topic, String availability) {
        requireEvent().ifPresent(id -> run("Speaker added", () -> {
            Event e = ctx.facade().getEvent(id);
            List<TimeWindow> windows = new ArrayList<>();
            for (String w : Arrays.stream(availability.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()) {
                String[] p = w.split("-");
                if (p.length != 2) {
                    throw new DomainException("Availability must look like 09:00-12:00");
                }
                windows.add(new TimeWindow(e.start().toLocalDate().atTime(TimeParser.parseTime(p[0])),
                        e.start().toLocalDate().atTime(TimeParser.parseTime(p[1]))));
            }
            ctx.facade().addSpeaker(id, name, topic, null, windows);
        }));
    }
}
