package com.eventpilot.domain.service;

import com.eventpilot.domain.EventTask;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Deterministic task rules for F09: dependency cycles, ordering, blocking and overdue checks. */
public class TaskScheduler {

    /** Returns one dependency cycle as a list of task ids (first id repeated at the end), if any. */
    public Optional<List<String>> detectCycles(List<EventTask> tasks) {
        Map<String, List<String>> graph = new HashMap<>();
        tasks.forEach(t -> graph.put(t.id(), t.dependsOn()));
        Set<String> done = new HashSet<>();
        for (EventTask t : tasks) {
            Deque<String> path = new ArrayDeque<>();
            Optional<List<String>> cycle = visit(t.id(), graph, new HashSet<>(), done, path);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        return Optional.empty();
    }

    private Optional<List<String>> visit(String id, Map<String, List<String>> graph, Set<String> onPath,
                                         Set<String> done, Deque<String> path) {
        if (done.contains(id) || !graph.containsKey(id)) {
            return Optional.empty();
        }
        if (onPath.contains(id)) {
            List<String> cycle = new ArrayList<>();
            boolean started = false;
            for (String p : path) {
                if (p.equals(id)) {
                    started = true;
                }
                if (started) {
                    cycle.add(p);
                }
            }
            cycle.add(id);
            return Optional.of(cycle);
        }
        onPath.add(id);
        path.addLast(id);
        for (String dep : graph.get(id)) {
            Optional<List<String>> cycle = visit(dep, graph, onPath, done, path);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        path.removeLast();
        onPath.remove(id);
        done.add(id);
        return Optional.empty();
    }

    /** Dependencies that do not exist in the list. */
    public List<String> unknownDependencies(List<EventTask> tasks) {
        Set<String> ids = new HashSet<>();
        tasks.forEach(t -> ids.add(t.id()));
        List<String> unknown = new ArrayList<>();
        for (EventTask t : tasks) {
            for (String d : t.dependsOn()) {
                if (!ids.contains(d)) {
                    unknown.add(t.id() + " -> " + d);
                }
            }
        }
        return unknown;
    }

    /** Topological order (dependencies first, then by deadline). Throws when there is a cycle. */
    public List<EventTask> orderByDependencies(List<EventTask> tasks) {
        detectCycles(tasks).ifPresent(c -> {
            throw new IllegalArgumentException("Circular task dependency: " + String.join(" -> ", c));
        });
        Map<String, EventTask> byId = new LinkedHashMap<>();
        tasks.stream().sorted(Comparator.comparing(EventTask::deadline).thenComparing(EventTask::id))
                .forEach(t -> byId.put(t.id(), t));
        List<EventTask> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (EventTask t : byId.values()) {
            place(t, byId, placed, ordered);
        }
        return ordered;
    }

    private void place(EventTask t, Map<String, EventTask> byId, Set<String> placed, List<EventTask> ordered) {
        if (placed.contains(t.id())) {
            return;
        }
        for (String dep : t.dependsOn()) {
            EventTask d = byId.get(dep);
            if (d != null) {
                place(d, byId, placed, ordered);
            }
        }
        placed.add(t.id());
        ordered.add(t);
    }

    /** Dependencies of the task that are not done yet. */
    public List<EventTask> openDependencies(EventTask task, List<EventTask> all) {
        return all.stream()
                .filter(t -> task.dependsOn().contains(t.id()) && t.status() != EventTask.Status.DONE)
                .toList();
    }

    public List<EventTask> findOverdue(List<EventTask> tasks, LocalDate today) {
        return tasks.stream().filter(t -> t.isOverdue(today)).toList();
    }
}
