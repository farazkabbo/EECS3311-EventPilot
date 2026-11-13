package com.eventpilot.agent.workflow;

import com.eventpilot.agent.CommandMapper;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.domain.Event;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses agenda-style answers: {"summary": "...", "changes": [{"op": ..., "reason": ...}]}
 * or {"question": "..."} when the agent needs clarification. Every change goes
 * through {@link CommandMapper}; any invalid change makes the whole answer invalid
 * so the LLM is asked to fix it.
 */
public class ProposalParser implements ResponseParser<PlanProposal> {

    private final CommandMapper mapper;
    private final Event event;
    private final String title;

    public ProposalParser(CommandMapper mapper, Event event, String title) {
        this.mapper = mapper;
        this.event = event;
        this.title = title;
    }

    @Override
    public PlanProposal parse(String text) throws ParseException {
        JsonNode root = JsonAnswer.parse(text);
        if (root.hasNonNull("question") && !root.get("question").asText().isBlank()
                && (!root.has("changes") || root.get("changes").isEmpty())) {
            return PlanProposal.question(event.id(), title, root.get("question").asText());
        }
        JsonNode changes = root.path("changes");
        if (!changes.isArray()) {
            throw new ParseException("\"changes\" must be an array of operations");
        }
        List<PlanProposal.Change> list = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int i = 0;
        for (JsonNode op : changes) {
            i++;
            try {
                list.add(new PlanProposal.Change(mapper.toCommand(op, event), op.path("reason").asText("")));
            } catch (CommandMapper.MappingException e) {
                errors.add("change " + i + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new ParseException(String.join("; ", errors));
        }
        return new PlanProposal(event.id(), title, root.path("summary").asText(""), list, false);
    }
}
