package com.eventpilot.agent.workflow;

import java.util.List;

/** An FAQ answer with the knowledge-base sources it is based on. */
public record FaqAnswer(String answer, List<String> sources, boolean escalated, boolean declined) {

    public FaqAnswer {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public static FaqAnswer escalated(String question) {
        return new FaqAnswer("I don't have that information yet, so I've forwarded your question to the organizer.",
                List.of(), true, false);
    }

    public static FaqAnswer declined(String reason) {
        return new FaqAnswer(reason, List.of(), false, true);
    }
}
