package com.eventpilot.agent.offline;

import com.eventpilot.agent.llm.ChatMessage;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.agent.llm.LLMResponse;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Deterministic, rule-based stand-in for an LLM, used by {@code MockLLMProvider}
 * when no API key is configured and in tests. It reads the same {@code <context>}
 * the real model receives, calls the same tools through the agent loop, and
 * answers in the same JSON formats - so every feature can be demonstrated and
 * tested offline. It is NOT a language model: free-text understanding is limited
 * to simple patterns (documented in the README and report).
 */
public class OfflinePlanner implements Function<LLMRequest, LLMResponse> {

    private final OfflineExtraction extraction = new OfflineExtraction();
    private final OfflineScheduling scheduling = new OfflineScheduling();
    private final OfflineResources resources = new OfflineResources();
    private final OfflineWriting writing = new OfflineWriting();
    private final OfflineModification modification = new OfflineModification();

    @Override
    public LLMResponse apply(LLMRequest request) {
        Turn turn = Turn.of(request);
        String workflow = turn.context().path("workflow").asText(turn.firstUserText().contains("<sources>") ? "faq_answer" : "");
        try {
            return switch (workflow) {
                case "event_extraction" -> text(extraction.extract(turn));
                case "venue_recommendation" -> resources.venues(turn);
                case "agenda_generation" -> scheduling.agenda(turn);
                case "speaker_scheduling" -> scheduling.speakers(turn);
                case "conflict_resolution" -> scheduling.resolve(turn);
                case "budget_planning" -> resources.budget(turn);
                case "catering_planning" -> resources.catering(turn);
                case "task_decomposition" -> text(resources.tasks(turn));
                case "invitation_drafting" -> text(writing.invitations(turn));
                case "faq_answer" -> text(writing.faq(turn));
                case "plan_modification" -> text(modification.modify(turn));
                default -> text(Json.write(Json.object().put("error", "offline planner: unknown task")));
            };
        } catch (RuntimeException e) {
            ObjectNode err = Json.object();
            err.put("question", "The offline planner could not handle this request (" + e.getMessage()
                    + "). Configure a real LLM (see README) for free-form requests.");
            return text(Json.write(err));
        }
    }

    static LLMResponse text(String json) {
        return new LLMResponse(json, List.of(), "end_turn", 0, 0, "mock-offline-planner");
    }

    static LLMResponse call(String tool, ObjectNode args) {
        return new LLMResponse("", List.of(new ChatMessage.ToolCall("call_" + tool + "_" + System.nanoTime(), tool,
                Json.write(args))), "tool_use", 0, 0, "mock-offline-planner");
    }

    /** A read-only view of the request the planner answers. */
    record Turn(LLMRequest request, JsonNode context, String firstUserText) {

        static Turn of(LLMRequest request) {
            String first = request.messages().isEmpty() ? "" : request.messages().get(0).text();
            return new Turn(request, Json.tree(between(first, "<context>", "</context>").orElse("{}")), first);
        }

        String userRequest() {
            return between(firstUserText, "<user_request>", "</user_request>").orElse("").trim();
        }

        String section(String tag) {
            return between(firstUserText, "<" + tag + ">", "</" + tag + ">").orElse("").trim();
        }

        /** Number of tool rounds already done in this run. */
        long toolRounds() {
            return request.messages().stream().filter(m -> m.role() == ChatMessage.Role.TOOL_RESULTS).count();
        }

        Optional<JsonNode> lastToolResult(String tool) {
            List<ChatMessage> msgs = request.messages();
            for (int i = msgs.size() - 1; i >= 0; i--) {
                for (ChatMessage.ToolResult r : msgs.get(i).toolResults()) {
                    if (r.name().equals(tool) && !r.isError()) {
                        return Optional.of(Json.tree(r.content()));
                    }
                }
            }
            return Optional.empty();
        }

        boolean offered(String tool) {
            return request.tools().stream().anyMatch(t -> t.name().equals(tool));
        }

        static Optional<String> between(String text, String open, String close) {
            int a = text.indexOf(open);
            int b = text.indexOf(close);
            if (a < 0 || b < a) {
                return Optional.empty();
            }
            return Optional.of(text.substring(a + open.length(), b));
        }
    }
}
