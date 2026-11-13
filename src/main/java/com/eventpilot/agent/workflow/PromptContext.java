package com.eventpilot.agent.workflow;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Budget;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventTask;
import com.eventpilot.domain.Room;
import com.eventpilot.domain.Speaker;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the {@code <context>} JSON block put into prompts: the real event state
 * the agent must reason over (ids included, so answers can refer to real items).
 */
public final class PromptContext {

    private final ObjectNode root = Json.object();

    private PromptContext() {
    }

    public static PromptContext of(String workflowId, Event event) {
        PromptContext c = new PromptContext();
        c.root.put("workflow", workflowId);
        if (event != null) {
            ObjectNode e = c.root.putObject("event");
            e.put("id", event.id()).put("name", event.name()).put("type", event.type())
                    .put("start", event.start().toString()).put("end", event.end().toString())
                    .put("expected_attendance", event.expectedAttendance()).put("city", event.city())
                    .put("status", event.status().name()).put("budget_limit", event.budget().limit());
            ArrayNode rooms = e.putArray("rooms");
            for (Room r : event.rooms()) {
                rooms.addObject().put("name", r.name()).put("capacity", r.capacity());
            }
            if (event.venue() != null) {
                e.putObject("venue").put("id", event.venue().id()).put("name", event.venue().name())
                        .put("price_per_day", event.venue().pricePerDay());
            }
        }
        return c;
    }

    public PromptContext withAgenda(Event event) {
        ArrayNode items = root.putArray("agenda");
        for (AgendaItem i : event.agenda().items()) {
            ObjectNode n = items.addObject().put("id", i.id()).put("title", i.title())
                    .put("type", i.type().name().toLowerCase()).put("start", i.start().toString())
                    .put("end", i.end().toString()).put("room", i.room()).put("expected_audience", i.expectedAudience());
            if (i.speakerId() != null) {
                n.put("speaker_id", i.speakerId());
            }
        }
        return this;
    }

    public PromptContext withSpeakers(Event event) {
        ArrayNode list = root.putArray("speakers");
        for (Speaker s : event.speakers()) {
            ObjectNode n = list.addObject().put("id", s.id()).put("name", s.name()).put("topic", s.topic());
            ArrayNode av = n.putArray("available");
            s.availability().forEach(w -> av.addObject().put("start", w.start().toString()).put("end", w.end().toString()));
        }
        return this;
    }

    public PromptContext withAttendeeSummary(Event event) {
        ObjectNode n = root.putObject("attendees");
        Map<String, Integer> byRsvp = new TreeMap<>();
        Map<String, Integer> byRole = new TreeMap<>();
        for (Attendee a : event.attendees()) {
            byRsvp.merge(a.rsvp().name(), 1, Integer::sum);
            byRole.merge(a.role().name(), 1, Integer::sum);
        }
        n.put("total", event.attendees().size());
        ObjectNode r = n.putObject("by_rsvp");
        byRsvp.forEach(r::put);
        ObjectNode g = n.putObject("by_role");
        byRole.forEach(g::put);
        return this;
    }

    public PromptContext withBudget(Event event) {
        ObjectNode b = root.putObject("budget");
        b.put("limit", event.budget().limit());
        ArrayNode lines = b.putArray("lines");
        for (Budget.Line l : event.budget().lines()) {
            lines.addObject().put("category", l.category()).put("amount", l.amount());
        }
        return this;
    }

    public PromptContext withTasks(Event event) {
        ArrayNode list = root.putArray("tasks");
        for (EventTask t : event.tasks()) {
            ObjectNode n = list.addObject().put("id", t.id()).put("title", t.title()).put("owner", t.owner())
                    .put("deadline", t.deadline().toString()).put("status", t.status().name());
            ArrayNode deps = n.putArray("depends_on");
            t.dependsOn().forEach(deps::add);
        }
        return this;
    }

    public PromptContext put(String key, Object value) {
        root.set(key, Json.toTree(value));
        return this;
    }

    public ObjectNode node() {
        return root;
    }

    /** The block placed in the prompt. */
    public String render() {
        return "<context>\n" + Json.pretty(root) + "\n</context>";
    }
}
