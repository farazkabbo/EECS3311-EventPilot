package com.eventpilot.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The planned budget lines and the recorded expenses of one event. */
public class Budget {

    public static final List<String> STANDARD_CATEGORIES =
            List.of("venue", "catering", "av", "decor", "marketing", "contingency");

    public record Line(String category, double amount, String rationale) {
        public Line {
            category = normalize(category);
            if (amount < 0) {
                throw new IllegalArgumentException("budget line amount cannot be negative");
            }
            rationale = rationale == null ? "" : rationale;
        }
    }

    public record Expense(String id, String category, double amount, LocalDate date, String description) {
        public Expense {
            category = normalize(category);
            if (amount <= 0) {
                throw new IllegalArgumentException("expense amount must be positive");
            }
            date = date == null ? LocalDate.now() : date;
            description = description == null ? "" : description;
        }
    }

    private double limit;
    private final List<Line> lines = new ArrayList<>();
    private final List<Expense> expenses = new ArrayList<>();

    public static String normalize(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        return category.trim().toLowerCase(Locale.ROOT);
    }

    public double limit() {
        return limit;
    }

    public void setLimit(double limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("budget limit cannot be negative");
        }
        this.limit = limit;
    }

    /** Sets (or replaces) the planned line for a category and returns the previous one, if any. */
    public Optional<Line> setLine(Line line) {
        Optional<Line> previous = line(line.category());
        previous.ifPresent(lines::remove);
        lines.add(line);
        return previous;
    }

    public Optional<Line> removeLine(String category) {
        Optional<Line> previous = line(category);
        previous.ifPresent(lines::remove);
        return previous;
    }

    public Optional<Line> line(String category) {
        String c = normalize(category);
        return lines.stream().filter(l -> l.category().equals(c)).findFirst();
    }

    public List<Line> lines() {
        return List.copyOf(lines);
    }

    public void addExpense(Expense expense) {
        expenses.add(expense);
    }

    public boolean removeExpense(String id) {
        return expenses.removeIf(e -> e.id().equals(id));
    }

    public List<Expense> expenses() {
        return List.copyOf(expenses);
    }

    public String nextExpenseId() {
        return "x" + (expenses.size() + 1);
    }
}
