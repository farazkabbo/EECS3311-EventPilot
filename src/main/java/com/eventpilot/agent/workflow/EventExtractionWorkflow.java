package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.EventDraft;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** F01 - extracts a structured event from a plain-language description and asks about gaps. */
public class EventExtractionWorkflow extends PlanningWorkflow<EventExtractionWorkflow.Extraction> {

    /** The extracted draft, follow-up questions, and preferences the organizer stated. */
    public record Extraction(EventDraft draft, boolean eventRequest, String message, List<String> questions,
                             Map<String, String> preferences) {
    }

    private final String description;

    public EventExtractionWorkflow(AgentServices services, String description) {
        super(services, null);
        this.description = description;
    }

    @Override
    protected String gatherContext() {
        PromptContext ctx = PromptContext.of("event_extraction", null);
        ctx.node().put("today", LocalDate.now(services.clock()).toString());
        ctx.put("preferences", services.memory().preferences());
        return ctx.render();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: extract a new event from the organizer's description.
                - Resolve relative dates ("next Friday") using "today" from the context. Use 24-hour ISO times.
                - If the end time is not given but a duration is, compute it. Do not guess missing values: leave them null.
                - If the description contradicts itself (e.g. ends before it starts), leave the fields null and ask.
                - Use the default city from preferences when no city is given.
                - Record organizer habits they mention (e.g. "I like 15-minute breaks") in "preferences".
                - If the text is not a request to plan an event, set "is_event_request" to false and explain in "message".
                Reply format:
                {"is_event_request": true, "name": "...", "type": "meetup|conference|workshop|party|gala|...",
                 "start": "YYYY-MM-DDTHH:MM", "end": "YYYY-MM-DDTHH:MM", "expected_attendance": 120,
                 "budget": 10000, "city": "Toronto", "questions": ["..."], "preferences": {"break_minutes": "15"},
                 "message": "short confirmation or explanation"}
                """);
        return new AgentTask("event_extraction", system, context + "\n" + Prompts.request(description),
                List.of(), LLMRequest.Tier.MAIN, 2000);
    }

    @Override
    protected ResponseParser<Extraction> createParser() {
        return text -> {
            JsonNode root = JsonAnswer.parse(text);
            Map<String, String> prefs = new LinkedHashMap<>();
            root.path("preferences").fields().forEachRemaining(e -> prefs.put(e.getKey(), e.getValue().asText()));
            String message = root.path("message").asText("");
            if (root.has("is_event_request") && !root.get("is_event_request").asBoolean(true)) {
                return new Extraction(new EventDraft(), false, message, List.of(), prefs);
            }
            EventDraft draft = new EventDraft();
            draft.name = textOrNull(root, "name");
            draft.type = textOrNull(root, "type");
            draft.city = textOrNull(root, "city");
            draft.start = dateTime(root, "start");
            draft.end = dateTime(root, "end");
            draft.expectedAttendance = root.hasNonNull("expected_attendance") ? root.get("expected_attendance").asInt() : null;
            draft.budget = root.hasNonNull("budget") ? root.get("budget").asDouble() : null;
            List<String> questions = new ArrayList<>();
            root.path("questions").forEach(q -> questions.add(q.asText()));
            return new Extraction(draft, true, message, questions, prefs);
        };
    }

    @Override
    protected List<Warning> validate(Extraction result, AgentResult run) {
        return new ArrayList<>(); // completeness is handled with clarifying questions, not warnings
    }

    @Override
    protected boolean retryOnBlockingWarnings() {
        return false;
    }

    private static String textOrNull(JsonNode root, String field) {
        return root.hasNonNull(field) && !root.get(field).asText().isBlank() ? root.get(field).asText().trim() : null;
    }

    private static LocalDateTime dateTime(JsonNode root, String field) throws ResponseParser.ParseException {
        String t = textOrNull(root, field);
        if (t == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(t.length() == 16 ? t + ":00" : t);
        } catch (DateTimeParseException e) {
            throw new ResponseParser.ParseException("\"" + field + "\" must be an ISO date-time like 2026-12-05T09:00");
        }
    }
}
