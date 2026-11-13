package com.eventpilot.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** The ordered list of agenda items of one event. */
public class Agenda {

    private final List<AgendaItem> items = new ArrayList<>();

    /** Adds an item; assigns a fresh id ("a1", "a2", ...) when the item has none. */
    public AgendaItem add(AgendaItem item) {
        if (item.id() == null || item.id().isBlank()) {
            item.setId(nextId());
        } else if (find(item.id()).isPresent()) {
            throw new DomainException("Agenda item id already exists: " + item.id());
        }
        items.add(item);
        return item;
    }

    public Optional<AgendaItem> remove(String id) {
        Optional<AgendaItem> found = find(id);
        found.ifPresent(items::remove);
        return found;
    }

    public Optional<AgendaItem> find(String id) {
        return items.stream().filter(i -> i.id().equalsIgnoreCase(id)).findFirst();
    }

    public AgendaItem require(String id) {
        return find(id).orElseThrow(() -> new DomainException("No agenda item with id " + id));
    }

    /** Items sorted by start time (a copy - callers cannot change the agenda through it). */
    public List<AgendaItem> items() {
        List<AgendaItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(AgendaItem::start).thenComparing(AgendaItem::id));
        return sorted;
    }

    public List<AgendaItem> sessions() {
        return items().stream().filter(i -> i.type().isSession()).toList();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    public void clear() {
        items.clear();
    }

    public String nextId() {
        int max = 0;
        for (AgendaItem item : items) {
            String id = item.id();
            if (id != null && id.startsWith("a")) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(1)));
                } catch (NumberFormatException ignored) {
                    // non-numeric id, ignore
                }
            }
        }
        return "a" + (max + 1);
    }
}
