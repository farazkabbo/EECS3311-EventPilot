package com.eventpilot.agent.offline;

import com.eventpilot.agent.TimeParser;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline F01: pattern-based extraction of event fields from a description. */
class OfflineExtraction {

    private static final List<String> TYPES = List.of("hackathon", "conference", "workshop", "meetup", "seminar",
            "gala", "party", "wedding", "networking", "webinar", "fundraiser", "launch", "festival", "retreat", "summit");
    private static final List<String> CITIES = List.of("Toronto", "Mississauga", "Ottawa", "Waterloo", "Montreal",
            "Vancouver", "Hamilton", "Markham", "Brampton");
    private static final Pattern ATTENDANCE = Pattern.compile(
            "(?i)(\\d{1,5})\\s*(?:people|attendees|guests|participants|persons|students|members)");
    private static final Pattern BUDGET = Pattern.compile(
            "(?i)(?:\\$\\s?([\\d,]+(?:\\.\\d+)?)\\s*(k)?|budget(?: of| is)?\\s*\\$?\\s?([\\d,]+)\\s*(k)?|([\\d,]+)\\s*(k)?\\s*(?:dollars|cad))");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern NEXT_DAY = Pattern.compile(
            "(?i)\\b(next|this)\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)");
    private static final String TIME = "(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|noon)";
    private static final Pattern RANGE = Pattern.compile("(?i)(?:from\\s+)?" + TIME + "\\s*(?:-|to|until|till)\\s*" + TIME);
    private static final Pattern AT_TIME = Pattern.compile("(?i)\\b(?:at|starting at|starts at)\\s+" + TIME);
    private static final Pattern DURATION = Pattern.compile("(?i)for\\s+(\\d{1,2})\\s*(?:hours|hrs|h)\\b");
    private static final Pattern NAME = Pattern.compile("(?i)(?:called|named|titled)\\s+\"?([^\",.]+)\"?|\"([^\"]+)\"");
    private static final Pattern BREAKS = Pattern.compile("(?i)(\\d{1,2})[- ]minute breaks?");

    String extract(OfflinePlanner.Turn turn) {
        String text = turn.userRequest();
        String lower = text.toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.parse(turn.context().path("today").asText(LocalDate.now().toString()));
        JsonNode prefs = turn.context().path("preferences");
        ObjectNode out = Json.object();

        String type = TYPES.stream().filter(lower::contains).findFirst().orElse(null);
        boolean eventish = type != null || lower.matches("(?s).*\\b(event|plan|organi[sz]e|host|throw)\\b.*");
        if (!eventish) {
            out.put("is_event_request", false);
            out.put("message", "I can help you plan events. Describe the event: what, when, how many people and the budget.");
            return Json.write(out);
        }
        out.put("is_event_request", true);
        out.put("type", type == null ? "event" : type);

        Matcher name = NAME.matcher(text);
        String eventName = null;
        while (name.find()) {
            eventName = (name.group(1) != null ? name.group(1) : name.group(2)).trim();
        }
        if (eventName == null && type != null) {
            String topic = topicBefore(text, type);
            eventName = (topic.isEmpty() ? "" : topic + " ") + capitalize(type);
        }
        put(out, "name", eventName);

        Integer attendance = lastInt(ATTENDANCE, text, 1);
        if (attendance != null) {
            out.put("expected_attendance", attendance);
        } else {
            out.putNull("expected_attendance");
        }
        Double budget = lastBudget(text);
        if (budget != null) {
            out.put("budget", budget);
        } else {
            out.putNull("budget");
        }

        LocalDate date = lastDate(text, today);
        LocalTime start = null;
        LocalTime end = null;
        Matcher range = RANGE.matcher(text);
        while (range.find()) {
            try {
                start = TimeParser.parseTime(fixAmPm(range.group(1), range.group(2)));
                end = TimeParser.parseTime(range.group(2));
            } catch (IllegalArgumentException ignored) {
                // not a time range
            }
        }
        if (start == null) {
            Matcher at = AT_TIME.matcher(text);
            while (at.find()) {
                try {
                    start = TimeParser.parseTime(at.group(1));
                } catch (IllegalArgumentException ignored) {
                    // not a time
                }
            }
        }
        Integer hours = lastInt(DURATION, text, 1);
        if (end == null && start != null && hours != null) {
            end = start.plusHours(hours);
        }
        if (date != null && start != null) {
            out.put("start", LocalDateTime.of(date, start).toString());
        } else {
            out.putNull("start");
        }
        if (date != null && end != null) {
            out.put("end", LocalDateTime.of(date, end).toString());
        } else {
            out.putNull("end");
        }

        String city = null;
        for (String c : CITIES) {
            if (lower.contains(c.toLowerCase(Locale.ROOT))) {
                city = c;
            }
        }
        if (city == null && prefs.hasNonNull("default_city")) {
            city = prefs.get("default_city").asText();
        }
        put(out, "city", city);

        ObjectNode learned = out.putObject("preferences");
        Matcher breaks = BREAKS.matcher(text);
        if (breaks.find()) {
            learned.put("break_minutes", breaks.group(1));
        }
        if (city != null && !prefs.hasNonNull("default_city")) {
            learned.put("default_city", city);
        }
        out.putArray("questions");
        out.put("message", "Extracted the event details.");
        return Json.write(out);
    }

    private static String topicBefore(String text, String type) {
        Matcher m = Pattern.compile("(?i)\\b(?:an?|our|the)\\s+((?:[A-Za-z0-9+#]+\\s){0,3})" + type).matcher(text);
        if (m.find()) {
            String topic = m.group(1).trim();
            String[] words = topic.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (w.isEmpty() || w.matches("(?i)\\d+|small|big|large|new|one-day|day|evening|student")) {
                    continue;
                }
                sb.append(sb.length() == 0 ? "" : " ").append(w.length() <= 3 ? w.toUpperCase(Locale.ROOT) : capitalize(w));
            }
            return sb.toString();
        }
        return "";
    }

    private static String fixAmPm(String first, String second) {
        String f = first.trim().toLowerCase(Locale.ROOT);
        String s = second.trim().toLowerCase(Locale.ROOT);
        if (!f.endsWith("am") && !f.endsWith("pm") && !f.equals("noon") && (s.endsWith("pm") || s.endsWith("am"))) {
            int h = Integer.parseInt(f.split(":")[0]);
            int endH = Integer.parseInt(s.replaceAll("[^0-9:]", "").split(":")[0]);
            String suffix = s.endsWith("pm") && h <= endH && h != 12 ? "pm" : "am";
            if (s.endsWith("am")) {
                suffix = "am";
            }
            return f + suffix;
        }
        return f;
    }

    private static LocalDate lastDate(String text, LocalDate today) {
        LocalDate found = null;
        Matcher iso = ISO_DATE.matcher(text);
        while (iso.find()) {
            found = LocalDate.parse(iso.group(1));
        }
        Matcher md = MONTH_DAY.matcher(text);
        while (md.find()) {
            Month month = monthOf(md.group(1));
            int day = Integer.parseInt(md.group(2));
            int year = md.group(3) != null ? Integer.parseInt(md.group(3)) : today.getYear();
            LocalDate d = LocalDate.of(year, month, Math.min(day, month.length(java.time.Year.isLeap(year))));
            if (md.group(3) == null && d.isBefore(today)) {
                d = d.plusYears(1);
            }
            found = d;
        }
        Matcher next = NEXT_DAY.matcher(text);
        while (next.find()) {
            DayOfWeek dow = DayOfWeek.valueOf(next.group(2).toUpperCase(Locale.ROOT));
            found = next.group(1).equalsIgnoreCase("next") ? today.with(TemporalAdjusters.next(dow))
                    : today.with(TemporalAdjusters.nextOrSame(dow));
        }
        if (text.toLowerCase(Locale.ROOT).contains("tomorrow")) {
            found = today.plusDays(1);
        }
        return found;
    }

    private static Month monthOf(String abbr) {
        String a = abbr.toLowerCase(Locale.ROOT).substring(0, 3);
        for (Month m : Month.values()) {
            if (m.name().toLowerCase(Locale.ROOT).startsWith(a)) {
                return m;
            }
        }
        throw new IllegalArgumentException(abbr);
    }

    private static Integer lastInt(Pattern p, String text, int group) {
        Integer value = null;
        Matcher m = p.matcher(text);
        while (m.find()) {
            value = Integer.parseInt(m.group(group));
        }
        return value;
    }

    private static Double lastBudget(String text) {
        Double value = null;
        Matcher m = BUDGET.matcher(text);
        while (m.find()) {
            String num = m.group(1) != null ? m.group(1) : m.group(3) != null ? m.group(3) : m.group(5);
            String k = m.group(2) != null ? m.group(2) : m.group(4) != null ? m.group(4) : m.group(6);
            if (num == null) {
                continue;
            }
            double v = Double.parseDouble(num.replace(",", ""));
            value = k != null ? v * 1000 : v;
        }
        return value;
    }

    private static void put(ObjectNode n, String field, String value) {
        if (value == null) {
            n.putNull(field);
        } else {
            n.put(field, value);
        }
    }

    static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
