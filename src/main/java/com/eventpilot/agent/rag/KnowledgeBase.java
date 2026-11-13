package com.eventpilot.agent.rag;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.CateringPlan;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Speaker;
import com.eventpilot.domain.Venue;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Retrieval for the FAQ agent (F11). The event's information is split into
 * small cited pieces ("chunks") and ranked by a BM25-style keyword score with a
 * few event-planning synonyms. Only retrieved chunks may be used in answers.
 */
public class KnowledgeBase {

    public record Chunk(String id, String title, String text) {
    }

    public record Hit(Chunk chunk, double score) {
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final Set<String> STOP = Set.of("the", "a", "an", "is", "are", "be", "to", "of", "and", "or",
            "in", "on", "at", "for", "do", "does", "will", "there", "i", "we", "you", "it", "can", "what", "how",
            "my", "me", "with", "this", "that", "any", "have", "has", "get", "about", "event");
    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("food", List.of("lunch", "meal", "menu", "catering", "dinner", "breakfast", "coffee")),
            Map.entry("eat", List.of("lunch", "meal", "menu", "catering", "dinner", "breakfast")),
            Map.entry("vegan", List.of("vegan", "dietary", "menu")),
            Map.entry("vegetarian", List.of("vegetarian", "dietary", "menu")),
            Map.entry("halal", List.of("halal", "dietary", "menu")),
            Map.entry("where", List.of("venue", "address", "location", "room")),
            Map.entry("location", List.of("venue", "address")),
            Map.entry("address", List.of("venue", "address")),
            Map.entry("when", List.of("time", "start", "schedule", "date")),
            Map.entry("start", List.of("time", "start", "schedule", "begin")),
            Map.entry("schedule", List.of("agenda", "schedule", "time")),
            Map.entry("wheelchair", List.of("accessible", "accessibility")),
            Map.entry("accessible", List.of("accessible", "accessibility")),
            Map.entry("speaker", List.of("speaker", "talk", "keynote")),
            Map.entry("wifi", List.of("wifi", "internet")),
            Map.entry("parking", List.of("parking")));

    private final Map<String, List<Chunk>> chunksByEvent = new ConcurrentHashMap<>();

    /** (Re)builds the searchable pieces of one event. */
    public void index(Event event) {
        List<Chunk> chunks = new ArrayList<>();
        chunks.add(new Chunk("event", "Event overview", String.format(
                "%s is a %s on %s from %s to %s%s. Expected attendance: %d. Status: %s.",
                event.name(), event.type(), event.start().format(DAY), event.start().format(HM),
                event.end().format(HM), event.city().isBlank() ? "" : " in " + event.city(),
                event.expectedAttendance(), event.status())));
        Venue v = event.venue();
        if (v != null) {
            chunks.add(new Chunk("venue", "Venue and location", String.format(
                    "The venue is %s at %s, %s (%s). It is %s. Features: %s. Rooms: %s.",
                    v.name(), v.address(), v.city(), v.district(),
                    v.accessible() ? "wheelchair accessible" : "NOT wheelchair accessible",
                    String.join(", ", v.features()),
                    v.rooms().stream().map(r -> r.name() + " (" + r.capacity() + " seats)").collect(Collectors.joining(", ")))));
        }
        for (AgendaItem item : event.agenda().items()) {
            String speaker = item.speakerId() == null ? "" : event.findSpeaker(item.speakerId())
                    .map(s -> " Speaker: " + s.name() + (s.topic().isBlank() ? "" : " (" + s.topic() + ")") + ".")
                    .orElse("");
            chunks.add(new Chunk("agenda:" + item.id(), "Agenda: " + item.title(), String.format(
                    "%s \"%s\" from %s to %s in %s.%s Part of the schedule.",
                    item.type().name().toLowerCase(Locale.ROOT), item.title(), item.start().format(HM),
                    item.end().format(HM), item.room() == null ? "the main room" : item.room(), speaker)));
        }
        for (Speaker s : event.speakers()) {
            chunks.add(new Chunk("speaker:" + s.id(), "Speaker " + s.name(),
                    s.name() + " is a speaker" + (s.topic().isBlank() ? "" : " talking about " + s.topic()) + "."));
        }
        CateringPlan plan = event.cateringPlan();
        if (plan != null) {
            for (CateringPlan.Meal meal : plan.meals()) {
                String menu = meal.menu().stream()
                        .map(m -> m.name() + (m.suitableFor().isEmpty() ? "" : " (" + String.join(", ", m.suitableFor()) + ")"))
                        .collect(Collectors.joining("; "));
                chunks.add(new Chunk("catering:" + meal.name().toLowerCase(Locale.ROOT).replace(' ', '-'),
                        "Food: " + meal.name(), String.format("%s is served at %s. Menu: %s.",
                        meal.name(), meal.time().format(HM), menu)));
            }
            if (!plan.notes().isBlank()) {
                chunks.add(new Chunk("catering:notes", "Food notes", plan.notes()));
            }
        }
        List<String> notes = event.notes();
        for (int i = 0; i < notes.size(); i++) {
            chunks.add(new Chunk("note:" + (i + 1), "Organizer note", notes.get(i)));
        }
        chunksByEvent.put(event.id(), chunks);
    }

    public void remove(String eventId) {
        chunksByEvent.remove(eventId);
    }

    public boolean isIndexed(String eventId) {
        return chunksByEvent.containsKey(eventId);
    }

    public List<Chunk> chunks(String eventId) {
        return List.copyOf(chunksByEvent.getOrDefault(eventId, List.of()));
    }

    /** The k best chunks with a positive score (none when nothing is relevant). */
    public List<Hit> search(String eventId, String question, int k) {
        List<Chunk> chunks = chunksByEvent.getOrDefault(eventId, List.of());
        Set<String> terms = expand(tokens(question));
        if (terms.isEmpty() || chunks.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> docFreq = new HashMap<>();
        List<List<String>> docs = new ArrayList<>();
        double avgLen = 0;
        for (Chunk c : chunks) {
            List<String> t = tokens(c.title() + " " + c.text());
            docs.add(t);
            avgLen += t.size();
            new HashSet<>(t).forEach(w -> docFreq.merge(w, 1, Integer::sum));
        }
        avgLen /= chunks.size();
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            List<String> doc = docs.get(i);
            double score = 0;
            for (String term : terms) {
                long tf = doc.stream().filter(term::equals).count();
                if (tf == 0) {
                    continue;
                }
                double idf = Math.log(1 + (chunks.size() - docFreq.get(term) + 0.5) / (docFreq.get(term) + 0.5));
                score += idf * (tf * 2.2) / (tf + 1.2 * (0.25 + 0.75 * doc.size() / avgLen));
            }
            if (score > 0.15) {
                hits.add(new Hit(chunks.get(i), score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.stream().limit(k).toList();
    }

    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.isBlank() || STOP.contains(raw)) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    private static String stem(String w) {
        if (w.length() > 4 && w.endsWith("ies")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    private static Set<String> expand(List<String> terms) {
        Set<String> out = new HashSet<>(terms);
        for (String t : terms) {
            SYNONYMS.getOrDefault(t, List.of()).forEach(s -> out.add(stem(s)));
        }
        return out;
    }
}
