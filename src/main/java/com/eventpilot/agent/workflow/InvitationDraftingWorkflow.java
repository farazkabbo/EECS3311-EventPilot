package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Invitation;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * F10 - the agent writes one template per attendee group in the chosen tone;
 * code personalises them and checks that each contains the real date, start
 * time and venue (drafts missing a fact are regenerated).
 */
public class InvitationDraftingWorkflow extends PlanningWorkflow<List<Invitation>> {

    public static final List<String> TONES = List.of("formal", "friendly", "playful");

    private final String tone;
    private Map<String, String[]> lastTemplates = new LinkedHashMap<>();

    public InvitationDraftingWorkflow(AgentServices services, Event event, String tone) {
        super(services, event);
        String t = tone == null ? "friendly" : tone.toLowerCase(Locale.ROOT);
        if (!TONES.contains(t)) {
            throw new WorkflowException("Unknown tone \"" + tone + "\". Use formal, friendly or playful.");
        }
        if (recipients().isEmpty()) {
            throw new WorkflowException("There are no attendees to invite (declined attendees are skipped).");
        }
        this.tone = t;
    }

    private List<Attendee> recipients() {
        return event.attendees().stream().filter(a -> a.rsvp() != Attendee.Rsvp.DECLINED).toList();
    }

    @Override
    protected String gatherContext() {
        Set<String> groups = new LinkedHashSet<>();
        recipients().forEach(a -> groups.add(a.role().name()));
        PromptContext ctx = PromptContext.of("invitation_drafting", event)
                .put("tone", tone)
                .put("groups", groups)
                .put("date_text", event.start().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)))
                .put("start_time_text", event.start().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)))
                .put("end_time_text", event.end().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)));
        if (event.venue() != null) {
            ctx.put("venue_text", event.venue().name() + ", " + event.venue().address() + ", " + event.venue().city());
        }
        return ctx.render();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: write invitation e-mails, one template per attendee group listed in "groups"
                (SPEAKER, VIP, GUEST, STAFF), in the requested tone.
                - Each body MUST contain the date exactly as date_text, the start time exactly as start_time_text,
                  and the venue exactly as venue_text (if given; otherwise say the venue will be announced).
                - Use the placeholder {name} for the recipient's name. Tailor the message to the group
                  (e.g. speakers: arrival time and their role; VIPs: reserved seating). Ask recipients to RSVP.
                - Keep each body under 160 words. Do not invent facts that are not in the context.
                Reply format:
                {"templates": [{"group": "GUEST", "subject": "...", "body": "Dear {name}, ..."}]}
                """);
        return new AgentTask("invitation_drafting", system, context + "\n" + Prompts.request("Draft the invitations."),
                List.of(), LLMRequest.Tier.LIGHT, 3000);
    }

    @Override
    protected ResponseParser<List<Invitation>> createParser() {
        return text -> {
            JsonNode root = JsonAnswer.parse(text);
            Map<String, String[]> templates = new LinkedHashMap<>();
            for (JsonNode t : root.path("templates")) {
                templates.put(t.path("group").asText("GUEST").toUpperCase(Locale.ROOT),
                        new String[] {t.path("subject").asText(""), t.path("body").asText("")});
            }
            if (templates.isEmpty()) {
                throw new ResponseParser.ParseException("\"templates\" is empty");
            }
            lastTemplates = templates;
            List<Invitation> invitations = new ArrayList<>();
            for (Attendee a : recipients()) {
                String[] tpl = templates.getOrDefault(a.role().name(), templates.getOrDefault("GUEST",
                        templates.values().iterator().next()));
                invitations.add(new Invitation(a.id(), a.email(), a.role().name(),
                        tpl[0].replace("{name}", a.name()), tpl[1].replace("{name}", a.name())));
            }
            return invitations;
        };
    }

    /** Every template must state the real date, start time and venue. */
    @Override
    protected List<Warning> validate(List<Invitation> invitations, AgentResult run) {
        List<Warning> warnings = new ArrayList<>();
        for (Map.Entry<String, String[]> e : lastTemplates.entrySet()) {
            String body = e.getValue()[1];
            List<String> missing = missingFacts(body);
            if (!missing.isEmpty()) {
                warnings.add(Warning.high("The " + e.getKey() + " invitation is missing the " + String.join(", ", missing) + "."));
            }
            if (e.getValue()[0].isBlank()) {
                warnings.add(Warning.high("The " + e.getKey() + " invitation has no subject."));
            }
        }
        return warnings;
    }

    List<String> missingFacts(String body) {
        String b = body.toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        String month = event.start().format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)).toLowerCase(Locale.ROOT);
        String day = String.valueOf(event.start().getDayOfMonth());
        boolean hasDate = b.contains(event.start().toLocalDate().toString())
                || (b.contains(month.substring(0, 3)) && b.matches("(?s).*\\b" + day + "\\b.*"));
        if (!hasDate) {
            missing.add("date");
        }
        int h24 = event.start().getHour();
        int h12 = h24 % 12 == 0 ? 12 : h24 % 12;
        String mm = String.format("%02d", event.start().getMinute());
        boolean hasTime = b.contains(h12 + ":" + mm) || b.contains(String.format("%02d:%s", h24, mm))
                || b.contains(h24 + ":" + mm) || b.matches("(?s).*\\b" + h12 + "\\s?(am|pm|a\\.m\\.|p\\.m\\.).*");
        if (!hasTime) {
            missing.add("start time");
        }
        if (event.venue() != null && !b.contains(event.venue().name().toLowerCase(Locale.ROOT))) {
            missing.add("venue");
        }
        return missing;
    }
}
