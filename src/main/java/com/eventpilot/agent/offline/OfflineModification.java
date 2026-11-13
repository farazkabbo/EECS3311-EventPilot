package com.eventpilot.agent.offline;

import com.eventpilot.agent.TimeParser;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline F12: understands a fixed set of instruction patterns ("move X to 2pm",
 * "add a 15-minute break after X", "remove X", "rename X to Y", "assign NAME to X"),
 * resolves "it" to the item mentioned before, and asks when a reference is ambiguous.
 */
class OfflineModification {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String TIME = "(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|noon)";
    private static final Pattern SPLIT = Pattern.compile(
            "(?i)\\s*(?:,|;|\\.|\\band then\\b|\\bthen\\b|\\band\\b)\\s*(?=(?:move|shift|push|reschedule|put|add|insert|remove|delete|cancel|drop|rename|assign|give)\\b)");
    private static final Pattern MOVE = Pattern.compile(
            "(?i)^(?:move|shift|push|reschedule|put)\\s+(?:the\\s+)?(.+?)\\s+(?:to|at)\\s+" + TIME + "(?:\\s+in\\s+(?:the\\s+)?(.+))?$");
    private static final Pattern ADD = Pattern.compile(
            "(?i)^(?:add|insert)\\s+(?:a\\s+|an\\s+)?(?:(\\d+)[- ]?(?:minute|min)s?\\s+)?(coffee break|break|lunch|networking|session|talk|workshop|panel)"
                    + "(?:\\s+(?:called|named|titled|on)\\s+\"?([^\"]+?)\"?)?(?:\\s+(after|before)\\s+(?:the\\s+)?(.+)|\\s+at\\s+" + TIME + ")?$");
    private static final Pattern REMOVE = Pattern.compile("(?i)^(?:remove|delete|cancel|drop)\\s+(?:the\\s+)?(.+)$");
    private static final Pattern RENAME = Pattern.compile("(?i)^rename\\s+(?:the\\s+)?(.+?)\\s+to\\s+\"?(.+?)\"?$");
    private static final Pattern ASSIGN = Pattern.compile("(?i)^(?:assign|give)\\s+(.+?)\\s+to\\s+(?:the\\s+)?(.+)$");
    private static final Set<String> PRONOUNS = Set.of("it", "that", "this", "that session", "this session", "that item");

    private static final class Ambiguous extends RuntimeException {
        Ambiguous(String question) {
            super(question);
        }
    }

    String modify(OfflinePlanner.Turn turn) {
        AgendaModel m = new AgendaModel(turn.context());
        String instruction = turn.userRequest().trim().replaceAll("[.!]+$", "");
        ArrayNode changes = Json.array();
        List<String> done = new ArrayList<>();
        AgendaModel.Item last = null;
        try {
            for (String raw : SPLIT.split(instruction)) {
                String clause = raw.trim();
                if (clause.isEmpty()) {
                    continue;
                }
                Matcher mv = MOVE.matcher(clause);
                Matcher ad = ADD.matcher(clause);
                Matcher rm = REMOVE.matcher(clause);
                Matcher rn = RENAME.matcher(clause);
                Matcher as = ASSIGN.matcher(clause);
                if (mv.matches()) {
                    AgendaModel.Item item = find(m, mv.group(1), last);
                    LocalDateTime start = TimeParser.parse(mv.group(2), item.start.toLocalDate());
                    Duration d = Duration.between(item.start, item.end);
                    item.start = start;
                    item.end = start.plus(d);
                    ObjectNode op = Json.object().put("op", "move").put("item_id", item.id)
                            .put("start", item.start.format(HM)).put("end", item.end.format(HM));
                    if (mv.group(3) != null) {
                        item.room = mv.group(3).trim();
                        op.put("room", item.room);
                    }
                    changes.add(op.put("reason", "requested move"));
                    done.add("moved \"" + item.title + "\" to " + item.start.format(HM));
                    last = item;
                } else if (ad.matches()) {
                    String kind = ad.group(2).toLowerCase(Locale.ROOT);
                    String type = kind.contains("break") ? "break" : kind.equals("lunch") ? "meal"
                            : kind.equals("talk") ? "session" : kind;
                    int minutes = ad.group(1) != null ? Integer.parseInt(ad.group(1))
                            : type.equals("break") ? 15 : type.equals("meal") ? 60 : 45;
                    String title = ad.group(3) != null ? ad.group(3).trim()
                            : OfflineExtraction.capitalize(kind.equals("talk") ? "session" : kind);
                    LocalDateTime start;
                    String room = m.rooms.get(0).name();
                    if (ad.group(4) != null) {
                        AgendaModel.Item ref = find(m, ad.group(5), last);
                        start = ad.group(4).equalsIgnoreCase("after") ? ref.end : ref.start.minusMinutes(minutes);
                        room = ref.room == null ? room : ref.room;
                    } else if (ad.group(6) != null) {
                        start = TimeParser.parse(ad.group(6), m.eventStart.toLocalDate());
                    } else {
                        throw new Ambiguous("When should the new " + kind + " take place (e.g. \"after the keynote\" or \"at 3pm\")?");
                    }
                    changes.add(Json.object().put("op", "add").put("title", title).put("type", type)
                            .put("start", start.format(HM)).put("end", start.plusMinutes(minutes).format(HM))
                            .put("room", room).put("reason", "requested addition"));
                    done.add("added a " + minutes + "-minute " + kind + " at " + start.format(HM));
                } else if (rn.matches()) {
                    AgendaModel.Item item = find(m, rn.group(1), last);
                    changes.add(Json.object().put("op", "rename").put("item_id", item.id).put("title", rn.group(2).trim()));
                    done.add("renamed \"" + item.title + "\"");
                    last = item;
                } else if (as.matches()) {
                    AgendaModel.SpeakerInfo sp = m.speakers.stream()
                            .filter(s -> s.name().toLowerCase(Locale.ROOT).contains(as.group(1).trim().toLowerCase(Locale.ROOT))
                                    || s.id().equalsIgnoreCase(as.group(1).trim()))
                            .findFirst().orElseThrow(() -> new Ambiguous("I don't know a speaker called \"" + as.group(1)
                                    + "\". Speakers: " + m.speakers.stream().map(AgendaModel.SpeakerInfo::name).toList()));
                    AgendaModel.Item item = find(m, as.group(2), last);
                    changes.add(Json.object().put("op", "assign_speaker").put("item_id", item.id).put("speaker_id", sp.id()));
                    done.add("assigned " + sp.name() + " to \"" + item.title + "\"");
                    last = item;
                } else if (rm.matches()) {
                    AgendaModel.Item item = find(m, rm.group(1), last);
                    changes.add(Json.object().put("op", "remove").put("item_id", item.id));
                    done.add("removed \"" + item.title + "\"");
                    m.items.remove(item);
                } else {
                    throw new Ambiguous("I couldn't understand \"" + clause + "\". Try e.g. \"move the keynote to 2pm\", "
                            + "\"add a 15-minute break after the keynote\", \"remove the panel\" or \"rename the panel to ...\".");
                }
            }
        } catch (Ambiguous a) {
            return Json.write(Json.object().put("question", a.getMessage()));
        }
        ObjectNode out = Json.object();
        out.put("summary", done.isEmpty() ? "Nothing to change." : OfflineExtraction.capitalize(String.join("; ", done)) + ".");
        out.set("changes", changes);
        return Json.write(out);
    }

    private static AgendaModel.Item find(AgendaModel m, String phrase, AgendaModel.Item last) {
        String p = phrase.trim().toLowerCase(Locale.ROOT).replaceAll("^(the|a|an)\\s+", "");
        if (PRONOUNS.contains(p)) {
            if (last == null) {
                throw new Ambiguous("What does \"" + phrase + "\" refer to? Please name the agenda item.");
            }
            return last;
        }
        for (AgendaModel.Item i : m.items) {
            if (i.id.equalsIgnoreCase(p)) {
                return i;
            }
        }
        List<AgendaModel.Item> matches = new ArrayList<>();
        String[] words = p.split("\\s+");
        for (AgendaModel.Item i : m.items) {
            String hay = (i.title + " " + i.type).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) {
                if (w.length() > 1 && !hay.contains(w.replaceAll("s$", ""))) {
                    all = false;
                }
            }
            if (all) {
                matches.add(i);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            throw new Ambiguous("I couldn't find \"" + phrase + "\" in the agenda. Items: "
                    + m.items.stream().map(i -> i.id + " " + i.title).toList());
        }
        throw new Ambiguous("Which one do you mean by \"" + phrase + "\"? "
                + matches.stream().map(i -> i.id + " \"" + i.title + "\" at " + i.start.format(HM)).toList());
    }
}
