package com.eventpilot.domain.state;

import java.util.Locale;

public enum LifecycleAction {
    START_PLANNING, CONFIRM, REOPEN, COMPLETE, CANCEL;

    public static LifecycleAction parse(String text) {
        String t = text.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (t.equals("PLAN") || t.equals("START")) {
            return START_PLANNING;
        }
        return valueOf(t);
    }
}
