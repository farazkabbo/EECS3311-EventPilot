package com.eventpilot.domain.service;

import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Event;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic catering facts for F08: headcount, dietary counts, portions and cost estimates. */
public class CateringCalculator {

    /** Extra portions ordered on top of the headcount. */
    public static final double BUFFER = 0.05;

    public record Headcount(int count, boolean fromRsvps, String warning) {
    }

    public record Portions(String mealType, int headcount, int portions, double costPerPortion, double totalCost) {
    }

    /** Confirmed RSVPs when there are any, otherwise the expected attendance (with a warning). */
    public Headcount computeHeadcount(Event event) {
        long confirmed = event.attendees().stream().filter(Attendee::isConfirmed).count();
        if (confirmed > 0) {
            return new Headcount((int) confirmed, true, null);
        }
        return new Headcount(event.expectedAttendance(), false,
                "No confirmed RSVPs yet - using the expected attendance of " + event.expectedAttendance() + ".");
    }

    /** Number of people per dietary need (confirmed attendees, or everyone when none confirmed). */
    public Map<String, Integer> summarizeDiets(Event event) {
        boolean anyConfirmed = event.attendees().stream().anyMatch(Attendee::isConfirmed);
        Map<String, Integer> counts = new TreeMap<>();
        for (Attendee a : event.attendees()) {
            if (anyConfirmed && !a.isConfirmed()) {
                continue;
            }
            for (String diet : a.dietary()) {
                counts.merge(diet, 1, Integer::sum);
            }
        }
        return counts;
    }

    public Portions computePortions(String mealType, int headcount) {
        if (headcount <= 0) {
            throw new IllegalArgumentException("headcount must be positive");
        }
        int portions = (int) Math.ceil(headcount * (1 + BUFFER));
        double cost = costPerPortion(mealType);
        return new Portions(mealType, headcount, portions, cost, Math.round(portions * cost * 100) / 100.0);
    }

    /** Typical Toronto catering prices per portion (CAD), by meal type. */
    public double costPerPortion(String mealType) {
        String t = mealType == null ? "" : mealType.toLowerCase(Locale.ROOT);
        if (t.contains("dinner")) {
            return 32.0;
        }
        if (t.contains("lunch")) {
            return 18.0;
        }
        if (t.contains("breakfast")) {
            return 12.0;
        }
        if (t.contains("reception") || t.contains("appetizer")) {
            return 15.0;
        }
        return 6.0; // coffee break / snacks
    }

    public static List<String> mealTypes() {
        return List.of("breakfast", "coffee break", "lunch", "reception", "dinner");
    }
}
