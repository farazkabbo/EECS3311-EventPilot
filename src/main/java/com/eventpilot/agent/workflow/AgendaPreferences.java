package com.eventpilot.agent.workflow;

import java.util.List;

/**
 * Organizer choices for agenda generation (F04).
 *
 * @param replaceExisting true = replace the current agenda, false = extend it
 */
public record AgendaPreferences(int tracks, int sessionMinutes, int breakMinutes, String lunchTime,
                                List<String> topics, boolean replaceExisting) {

    public AgendaPreferences {
        tracks = Math.max(1, tracks);
        sessionMinutes = sessionMinutes <= 0 ? 45 : sessionMinutes;
        breakMinutes = breakMinutes <= 0 ? 15 : breakMinutes;
        lunchTime = lunchTime == null || lunchTime.isBlank() ? "12:00" : lunchTime;
        topics = topics == null ? List.of() : List.copyOf(topics);
    }

    public static AgendaPreferences defaults() {
        return new AgendaPreferences(1, 45, 15, "12:00", List.of(), true);
    }
}
