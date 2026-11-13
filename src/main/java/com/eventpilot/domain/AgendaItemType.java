package com.eventpilot.domain;

public enum AgendaItemType {
    KEYNOTE, SESSION, WORKSHOP, PANEL, BREAK, MEAL, NETWORKING;

    /** Sessions are items that can have a speaker. */
    public boolean isSession() {
        return this == KEYNOTE || this == SESSION || this == WORKSHOP || this == PANEL;
    }

    public boolean isMealOrBreak() {
        return this == BREAK || this == MEAL;
    }

    public static AgendaItemType parse(String text) {
        if (text == null) {
            return SESSION;
        }
        try {
            return valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String t = text.toLowerCase();
            if (t.contains("lunch") || t.contains("dinner") || t.contains("breakfast") || t.contains("meal")) {
                return MEAL;
            }
            if (t.contains("break") || t.contains("coffee")) {
                return BREAK;
            }
            if (t.contains("keynote")) {
                return KEYNOTE;
            }
            return SESSION;
        }
    }
}
