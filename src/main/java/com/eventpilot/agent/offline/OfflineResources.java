package com.eventpilot.agent.offline;

import com.eventpilot.agent.llm.LLMResponse;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline F03, F07, F08, F09: tool-driven venue ranking, budget split, menus and task templates. */
class OfflineResources {

    // ---------------- F03 venues ----------------

    LLMResponse venues(OfflinePlanner.Turn turn) {
        JsonNode e = turn.context().path("event");
        String needs = turn.context().path("extra_needs").asText("").toLowerCase(Locale.ROOT);
        if (turn.toolRounds() == 0) {
            ObjectNode args = Json.object();
            if (!e.path("city").asText("").isBlank()) {
                args.put("city", e.get("city").asText());
            }
            args.put("min_capacity", e.path("expected_attendance").asInt(1));
            if (needs.contains("accessib") || needs.contains("wheelchair")) {
                args.put("accessible", true);
            }
            ArrayNode features = Json.array();
            for (String f : List.of("stage", "projector", "parking", "livestream", "bar", "rooftop", "whiteboards")) {
                if (needs.contains(f)) {
                    features.add(f);
                }
            }
            if (!features.isEmpty()) {
                args.set("features", features);
            }
            return OfflinePlanner.call("venue_search", args);
        }
        if (turn.toolRounds() == 1) {
            return OfflinePlanner.call("budget_calculator", Json.object().put("operation", "remaining").put("category", "venue"));
        }
        JsonNode search = turn.lastToolResult("venue_search").orElse(Json.object());
        double budget = turn.lastToolResult("budget_calculator").map(b -> b.path("remaining").asDouble(0)).orElse(0.0);
        int attendance = e.path("expected_attendance").asInt(1);
        List<JsonNode> venues = new ArrayList<>();
        search.path("venues").forEach(venues::add);
        Map<JsonNode, Integer> scores = new LinkedHashMap<>();
        for (JsonNode v : venues) {
            double price = v.path("price_per_day").asDouble();
            double priceScore = budget <= 0 ? 15 : price <= budget ? 30 : 30 * budget / price;
            double fit = 20.0 * attendance / Math.max(attendance, v.path("capacity").asInt());
            scores.put(v, (int) Math.round(v.path("rating").asDouble() / 5 * 50 + priceScore + fit));
        }
        venues.sort(Comparator.comparing((JsonNode v) -> scores.get(v)).reversed());
        ObjectNode out = Json.object();
        ArrayNode recs = out.putArray("recommendations");
        for (JsonNode v : venues.subList(0, Math.min(3, venues.size()))) {
            double price = v.path("price_per_day").asDouble();
            String trade = (budget > 0 && price > budget ? String.format("$%.0f over the venue budget", price - budget) : "within budget")
                    + (v.path("accessible").asBoolean() ? "" : "; not wheelchair accessible")
                    + (v.path("capacity").asInt() > attendance * 2 ? "; much larger than needed" : "");
            recs.addObject().put("venue_id", v.path("id").asText()).put("score", scores.get(v))
                    .put("reason", String.format("Rated %.1f/5, seats %d, $%.0f/day in %s.", v.path("rating").asDouble(),
                            v.path("capacity").asInt(), price, v.path("district").asText()))
                    .put("tradeoffs", trade);
        }
        out.put("summary", venues.isEmpty() ? "No venue in the catalog matches these needs."
                : String.format("Searched the catalog for %d+ guests; ranked by rating, price against the $%.0f venue "
                + "budget and size fit.", attendance, budget));
        out.put("relaxed", "");
        return OfflinePlanner.text(Json.write(out));
    }

    // ---------------- F07 budget ----------------

    LLMResponse budget(OfflinePlanner.Turn turn) {
        JsonNode e = turn.context().path("event");
        double limit = turn.context().path("budget").path("limit").asDouble(e.path("budget_limit").asDouble());
        String type = e.path("type").asText("event").toLowerCase(Locale.ROOT);
        Map<String, Double> pct = new LinkedHashMap<>();
        boolean social = type.matches(".*(gala|party|wedding|fundraiser|reception).*");
        pct.put("venue", social ? 30.0 : 35.0);
        pct.put("catering", social ? 35.0 : 28.0);
        pct.put("av", social ? 5.0 : 10.0);
        pct.put("decor", social ? 12.0 : 9.0);
        pct.put("marketing", 8.0);
        pct.put("contingency", 10.0);
        if (e.path("venue").has("price_per_day") && limit > 0) {
            double venuePct = Math.max(10, Math.min(55,
                    Math.ceil(e.path("venue").path("price_per_day").asDouble() / limit * 100)));
            double rest = 100 - venuePct;
            double oldRest = 100 - pct.get("venue");
            pct.replaceAll((k, v) -> k.equals("venue") ? venuePct : Math.round(v * rest / oldRest * 10) / 10.0);
        }
        // contingency absorbs rounding so the split is exactly 100%
        double others = pct.entrySet().stream().filter(x -> !x.getKey().equals("contingency"))
                .mapToDouble(Map.Entry::getValue).sum();
        pct.put("contingency", Math.round((100 - others) * 10) / 10.0);
        if (turn.toolRounds() == 0) {
            ObjectNode args = Json.object().put("operation", "allocate");
            ObjectNode p = args.putObject("percentages");
            pct.forEach(p::put);
            return OfflinePlanner.call("budget_calculator", args);
        }
        Map<String, String> why = Map.of(
                "venue", e.path("venue").has("name") ? "covers the chosen venue (" + e.path("venue").path("name").asText() + ")"
                        : "space rental, usually the largest cost",
                "catering", "food and drinks for " + e.path("expected_attendance").asInt() + " people",
                "av", "projector, microphones and sound",
                "decor", social ? "a social event needs more decoration" : "signage and basic decoration",
                "marketing", "promotion and printed material",
                "contingency", "buffer for surprises");
        ObjectNode out = Json.object();
        out.put("summary", "Split for a " + type + " (" + (social ? "social" : "professional")
                + " profile), amounts computed by budget_calculator.");
        ArrayNode alloc = out.putArray("allocation");
        pct.forEach((k, v) -> alloc.addObject().put("category", k).put("percent", v).put("rationale", why.get(k)));
        return OfflinePlanner.text(Json.write(out));
    }

    // ---------------- F08 catering ----------------

    LLMResponse catering(OfflinePlanner.Turn turn) {
        JsonNode ctx = turn.context();
        List<String[]> slots = new ArrayList<>(); // name, meal_type, time
        for (JsonNode s : ctx.path("meal_slots_in_agenda")) {
            String title = s.path("title").asText();
            slots.add(new String[] {title, mealType(title, s.path("start").asText()), s.path("start").asText()});
        }
        if (slots.isEmpty()) {
            LocalDateTime start = LocalDateTime.parse(ctx.path("event").path("start").asText());
            LocalDateTime end = LocalDateTime.parse(ctx.path("event").path("end").asText());
            if (start.getHour() < 10) {
                slots.add(new String[] {"Breakfast", "breakfast", start.toLocalTime().toString()});
            }
            if (start.getHour() <= 12 && end.getHour() >= 13) {
                slots.add(new String[] {"Lunch", "lunch", "12:00"});
            }
            if (end.getHour() >= 19) {
                slots.add(new String[] {"Dinner", "dinner", "18:00"});
            }
            if (slots.isEmpty()) {
                slots.add(new String[] {"Refreshments", "coffee break", start.plusHours(1).toLocalTime().toString()});
            }
        }
        if (turn.toolRounds() == 0) {
            return OfflinePlanner.call("catering_calculator", Json.object().put("meal_type", slots.get(0)[1]));
        }
        List<String> diets = new ArrayList<>();
        ctx.path("dietary_counts").fieldNames().forEachRemaining(diets::add);
        ObjectNode out = Json.object();
        ArrayNode meals = out.putArray("meals");
        for (String[] slot : slots) {
            ObjectNode meal = meals.addObject().put("name", slot[0]).put("meal_type", slot[1]).put("time", slot[2]);
            ArrayNode menu = meal.putArray("menu");
            List<String> covered = new ArrayList<>();
            for (Object[] item : baseMenu(slot[1])) {
                ObjectNode mi = menu.addObject().put("name", (String) item[0]);
                ArrayNode tags = mi.putArray("suitable_for");
                for (String tag : (String[]) item[1]) {
                    tags.add(tag);
                    covered.add(tag);
                }
            }
            for (String d : diets) {
                if (!covered.contains(d)) {
                    menu.addObject().put("name", OfflineExtraction.capitalize(d) + " meal, prepared and packed separately")
                            .putArray("suitable_for").add(d);
                }
            }
        }
        out.put("summary", slots.size() + " meal(s) aligned with the agenda; every dietary need ("
                + (diets.isEmpty() ? "none recorded" : String.join(", ", diets)) + ") has an option in every meal.");
        out.put("notes", diets.isEmpty() ? "No dietary restrictions recorded." : "Label all dishes; keep allergen-free meals sealed and separate.");
        return OfflinePlanner.text(Json.write(out));
    }

    private static String mealType(String title, String time) {
        String t = title.toLowerCase(Locale.ROOT);
        for (String type : List.of("breakfast", "lunch", "dinner", "reception")) {
            if (t.contains(type)) {
                return type;
            }
        }
        return t.contains("networking") ? "reception" : "coffee break";
    }

    private static List<Object[]> baseMenu(String mealType) {
        return switch (mealType) {
            case "breakfast" -> List.of(
                    new Object[] {"Oatmeal bar with fresh fruit", new String[] {"vegan", "vegetarian", "dairy-free", "halal"}},
                    new Object[] {"Scrambled eggs and toast", new String[] {"vegetarian"}},
                    new Object[] {"Coffee and tea", new String[] {"vegan", "vegetarian", "gluten-free", "dairy-free", "halal"}});
            case "coffee break" -> List.of(
                    new Object[] {"Fresh fruit platter", new String[] {"vegan", "vegetarian", "gluten-free", "dairy-free", "halal", "kosher", "nut allergy"}},
                    new Object[] {"Coffee, tea and juice", new String[] {"vegan", "vegetarian", "gluten-free", "dairy-free", "halal", "kosher", "nut allergy"}});
            case "reception" -> List.of(
                    new Object[] {"Vegetable crudites with hummus", new String[] {"vegan", "vegetarian", "gluten-free", "dairy-free", "halal"}},
                    new Object[] {"Cheese and crackers", new String[] {"vegetarian"}});
            default -> List.of( // lunch and dinner
                    new Object[] {"Chickpea and spinach curry with rice", new String[] {"vegan", "vegetarian", "gluten-free", "dairy-free"}},
                    new Object[] {"Halal chicken shawarma bowl", new String[] {"halal", "gluten-free"}},
                    new Object[] {"Mixed green salad", new String[] {"vegan", "vegetarian", "gluten-free", "dairy-free", "halal"}});
        };
    }

    // ---------------- F09 tasks ----------------

    private record Template(String title, int daysBefore, String skipIf, int... deps) {
    }

    private static final List<Template> TEMPLATES = List.of(
            new Template("Confirm venue booking and pay deposit", 45, "has_venue"),
            new Template("Finalize agenda and speaker list", 35, "has_agenda"),
            new Template("Create registration / RSVP form", 35, null),
            new Template("Send invitations", 28, null, 2, 0),
            new Template("Confirm speakers and send them a briefing", 25, null, 1),
            new Template("Book AV equipment", 21, null, 0),
            new Template("Promote on social media and mailing lists", 21, null, 3),
            new Template("Order catering with final headcount", 10, "has_catering_plan", 3),
            new Template("Prepare signage and name badges", 7, null, 3),
            new Template("Send reminder to attendees", 3, null, 3),
            new Template("Brief volunteers and staff", 2, null, 1),
            new Template("Day-before venue walkthrough", 1, null, 5));

    String tasks(OfflinePlanner.Turn turn) {
        JsonNode ctx = turn.context();
        LocalDate eventDay = LocalDateTime.parse(ctx.path("event").path("start").asText()).toLocalDate();
        LocalDate today = LocalDate.parse(ctx.path("today").asText(LocalDate.now().toString()));
        List<String> team = new ArrayList<>();
        ctx.path("team").forEach(t -> team.add(t.asText()));
        if (team.isEmpty()) {
            team.add("organizer");
        }
        List<String> existingTitles = new ArrayList<>();
        ctx.path("tasks").forEach(t -> existingTitles.add(t.path("title").asText().toLowerCase(Locale.ROOT)));
        int nextId = Integer.parseInt(ctx.path("next_task_id").asText("t1").substring(1));
        long available = Math.max(0, ChronoUnit.DAYS.between(today, eventDay));
        double scale = available >= 45 ? 1.0 : available / 45.0;

        String[] ids = new String[TEMPLATES.size()];
        ObjectNode out = Json.object();
        ArrayNode tasks = out.putArray("tasks");
        int owner = 0;
        for (int i = 0; i < TEMPLATES.size(); i++) {
            Template t = TEMPLATES.get(i);
            boolean skip = (t.skipIf() != null && ctx.path(t.skipIf()).asBoolean(false))
                    || existingTitles.contains(t.title().toLowerCase(Locale.ROOT));
            if (skip) {
                continue;
            }
            ids[i] = "t" + nextId++;
            long before = Math.round(Math.ceil(t.daysBefore() * scale));
            LocalDate deadline = eventDay.minusDays(Math.max(0, before));
            if (deadline.isBefore(today)) {
                deadline = today.isAfter(eventDay) ? eventDay : today;
            }
            ObjectNode n = tasks.addObject().put("id", ids[i]).put("title", t.title())
                    .put("owner", team.get(owner++ % team.size())).put("deadline", deadline.toString());
            ArrayNode deps = n.putArray("depends_on");
            for (int d : t.deps()) {
                if (ids[d] != null) {
                    deps.add(ids[d]);
                }
            }
        }
        out.put("summary", tasks.size() + " tasks planned backwards from " + eventDay
                + (scale < 1 ? " (timeline compressed: only " + available + " days left)" : "") + ", shared between "
                + String.join(", ", team) + ".");
        return Json.write(out);
    }
}
