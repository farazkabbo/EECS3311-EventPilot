package com.eventpilot.agent.offline;

import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;

/** Offline F10 and F11: template-based invitations and extractive FAQ answers. */
class OfflineWriting {

    String invitations(OfflinePlanner.Turn turn) {
        JsonNode ctx = turn.context();
        String name = ctx.path("event").path("name").asText("our event");
        String tone = ctx.path("tone").asText("friendly");
        String date = ctx.path("date_text").asText();
        String start = ctx.path("start_time_text").asText();
        String end = ctx.path("end_time_text").asText();
        String venue = ctx.hasNonNull("venue_text") ? ctx.get("venue_text").asText() : "a venue to be announced";
        String facts = date + " from " + start + " to " + end + " at " + venue;

        ObjectNode out = Json.object();
        ArrayNode templates = out.putArray("templates");
        for (JsonNode g : ctx.path("groups")) {
            String group = g.asText();
            String extra = switch (group) {
                case "SPEAKER" -> "As one of our speakers, please arrive 30 minutes early so we can test your slides.";
                case "VIP" -> "A reserved seat in the front row is waiting for you.";
                case "STAFF" -> "Thank you for helping out - the team briefing starts one hour before doors open.";
                default -> "We would love to see you there.";
            };
            String subject;
            String body;
            switch (tone) {
                case "formal" -> {
                    subject = "Invitation: " + name;
                    body = "Dear {name},\n\nYou are cordially invited to " + name + " on " + facts + ". " + extra
                            + "\n\nKindly confirm your attendance by replying to this message.\n\nSincerely,\nThe organizing team";
                }
                case "playful" -> {
                    subject = "You're in for a treat: " + name + "!";
                    body = "Hey {name}!\n\nClear your calendar: " + name + " is happening on " + facts + ". " + extra
                            + "\n\nHit reply to RSVP - we're saving you a spot!\n\nCheers,\nThe crew";
                }
                default -> {
                    subject = "Join us at " + name;
                    body = "Hi {name},\n\nWe'd love for you to join " + name + " on " + facts + ". " + extra
                            + "\n\nPlease reply to let us know if you can make it.\n\nSee you soon,\nThe organizers";
                }
            }
            templates.addObject().put("group", group).put("subject", subject).put("body", body);
        }
        return Json.write(out);
    }

    String faq(OfflinePlanner.Turn turn) {
        JsonNode sources = Json.tree(turn.section("sources").isBlank() ? "[]" : turn.section("sources"));
        String question = turn.section("question").toLowerCase(Locale.ROOT);
        ObjectNode out = Json.object();
        if (sources.isEmpty()) {
            out.put("answer", "").put("answerable", false).putArray("sources");
            return Json.write(out);
        }
        JsonNode best = sources.get(0);
        String text = best.path("text").asText();
        String answer = question.startsWith("is") || question.startsWith("are") || question.startsWith("will")
                || question.startsWith("can") || question.startsWith("do")
                ? "Based on the event information: " + text : text;
        out.put("answer", answer);
        out.putArray("sources").add(best.path("id").asText());
        out.put("answerable", true).put("off_topic", false);
        return Json.write(out);
    }
}
