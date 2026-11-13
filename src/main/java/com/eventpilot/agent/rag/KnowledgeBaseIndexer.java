package com.eventpilot.agent.rag;

import com.eventpilot.application.event.DomainEvent;
import com.eventpilot.application.event.EventObserver;

/** Concrete observer: keeps the FAQ knowledge base in sync with every saved change. */
public class KnowledgeBaseIndexer implements EventObserver {

    private final KnowledgeBase knowledgeBase;

    public KnowledgeBaseIndexer(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void onEventChanged(DomainEvent change) {
        switch (change.type()) {
            case ALERTS_UPDATED, FAQ_ESCALATED -> {
                // nothing the FAQ answers depend on
            }
            case EVENT_DELETED -> knowledgeBase.remove(change.eventId());
            default -> {
                if (change.event() != null) {
                    knowledgeBase.index(change.event());
                }
            }
        }
    }
}
