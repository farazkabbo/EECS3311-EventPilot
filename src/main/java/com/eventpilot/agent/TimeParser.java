package com.eventpilot.agent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the time formats LLMs and users write: "14:00", "2pm", "2:30 pm", "noon", ISO date-times. */
public final class TimeParser {

    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?$");

    private TimeParser() {
    }

    public static LocalDateTime parse(String text, LocalDate defaultDate) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("time is missing");
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        try {
            return LocalDateTime.parse(t.length() == 16 ? t + ":00" : t.replace(' ', 'T'));
        } catch (DateTimeParseException ignored) {
            // not ISO
        }
        return LocalDateTime.of(defaultDate, parseTime(t));
    }

    public static LocalTime parseTime(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.equals("noon") || t.equals("midday")) {
            return LocalTime.NOON;
        }
        if (t.equals("midnight")) {
            return LocalTime.MIDNIGHT;
        }
        Matcher m = CLOCK.matcher(t);
        if (!m.matches()) {
            throw new IllegalArgumentException("cannot understand time \"" + text + "\"");
        }
        int hour = Integer.parseInt(m.group(1));
        int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        String ampm = m.group(3);
        if (ampm != null) {
            if (hour < 1 || hour > 12) {
                throw new IllegalArgumentException("invalid 12-hour time \"" + text + "\"");
            }
            boolean pm = ampm.startsWith("p");
            hour = hour % 12 + (pm ? 12 : 0);
        }
        if (hour > 23 || minute > 59) {
            throw new IllegalArgumentException("invalid time \"" + text + "\"");
        }
        return LocalTime.of(hour, minute);
    }
}
