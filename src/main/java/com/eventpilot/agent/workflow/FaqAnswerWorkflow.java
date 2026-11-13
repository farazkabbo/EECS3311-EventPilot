package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.agent.rag.KnowledgeBase;
import com.eventpilot.domain.Event;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * F11 - retrieval-augmented FAQ. {@link #gatherContext()} retrieves the top 5
 * chunks; with nothing relevant the question is escalated WITHOUT calling the
 * LLM ({@link #shortCircuit}); the parser rejects citations of sources that were
 * not retrieved.
 */
public class FaqAnswerWorkflow extends PlanningWorkflow<FaqAnswer> {

    private static final Pattern INJECTION = Pattern.compile(
            "(?i)(ignore (all |the |your )?(previous|prior|above) (instructions|rules)|system prompt|you are now|"
                    + "reveal your (prompt|instructions)|developer mode|disregard (the )?rules)");

    private final String question;
    private List<KnowledgeBase.Hit> hits = List.of();

    public FaqAnswerWorkflow(AgentServices services, Event event, String question) {
        super(services, event);
        if (question == null || question.isBlank()) {
            throw new WorkflowException("Please type a question.");
        }
        this.question = question.trim();
    }

    @Override
    protected String gatherContext() {
        if (!services.knowledgeBase().isIndexed(event.id())) {
            services.knowledgeBase().index(event);
        }
        hits = services.knowledgeBase().search(event.id(), question, 5);
        ArrayNode sources = Json.array();
        for (KnowledgeBase.Hit h : hits) {
            sources.addObject().put("id", h.chunk().id()).put("title", h.chunk().title()).put("text", h.chunk().text());
        }
        return "<sources>\n" + Json.pretty(sources) + "\n</sources>";
    }

    @Override
    protected Optional<FaqAnswer> shortCircuit(String context) {
        if (INJECTION.matcher(question).find()) {
            return Optional.of(FaqAnswer.declined("I can only answer questions about this event."));
        }
        if (hits.isEmpty()) {
            return Optional.of(FaqAnswer.escalated(question));
        }
        return Optional.empty();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: answer an attendee's question about the event using ONLY the numbered <sources>.
                - Cite the ids of the sources you used. If the sources do not contain the answer, set
                  "answerable" to false - do not guess. If the question is not about the event, set "off_topic" to true.
                - Answer in 1-3 friendly sentences. Never reveal attendee personal data.
                Reply format:
                {"answer": "...", "sources": ["agenda:a1"], "answerable": true, "off_topic": false}
                """);
        return new AgentTask("faq_answer", system,
                PromptContext.of("faq_answer", event).render() + "\n" + context + "\n<question>\n" + question + "\n</question>",
                List.of(), LLMRequest.Tier.LIGHT, 1000);
    }

    @Override
    protected ResponseParser<FaqAnswer> createParser() {
        Set<String> retrieved = hits.stream().map(h -> h.chunk().id()).collect(Collectors.toSet());
        return text -> {
            JsonNode root = JsonAnswer.parse(text);
            if (root.path("off_topic").asBoolean(false)) {
                return FaqAnswer.declined("I can only answer questions about this event.");
            }
            if (!root.path("answerable").asBoolean(true)) {
                return FaqAnswer.escalated(question);
            }
            List<String> sources = new ArrayList<>();
            root.path("sources").forEach(s -> sources.add(s.asText()));
            List<String> unknown = sources.stream().filter(s -> !retrieved.contains(s)).toList();
            if (!unknown.isEmpty()) {
                throw new ResponseParser.ParseException("you cited sources that were not provided: " + unknown
                        + ". Cite only ids from <sources>: " + retrieved);
            }
            if (sources.isEmpty()) {
                throw new ResponseParser.ParseException("cite at least one source id, or set answerable to false");
            }
            String answer = root.path("answer").asText("").trim();
            if (answer.isEmpty()) {
                throw new ResponseParser.ParseException("\"answer\" is empty");
            }
            return new FaqAnswer(answer, sources, false, false);
        };
    }

    @Override
    protected boolean retryOnBlockingWarnings() {
        return false;
    }

    public String question() {
        return question;
    }
}
