package com.eventpilot.domain.service;

import com.eventpilot.domain.Budget;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All money arithmetic for F07. The agent only chooses percentages; this class
 * turns them into amounts, records expenses and computes planned-vs-spent status.
 */
public class BudgetTracker {

    public static final double ALERT_THRESHOLD = 0.9;

    public record CategoryStatus(String category, double planned, double spent) {
        public boolean overPlan() {
            return planned > 0 ? spent > planned : spent > 0;
        }
    }

    public record BudgetStatus(double limit, double totalPlanned, double totalSpent,
                               List<CategoryStatus> categories, List<String> alerts) {
        public double remaining() {
            return limit - totalSpent;
        }

        public double spentRatio() {
            return limit <= 0 ? 0 : totalSpent / limit;
        }
    }

    /**
     * Splits the limit by percentages. Percentages are normalised when they sum above 100,
     * and amounts are rounded to whole dollars with the rounding remainder put in the last line.
     */
    public List<Budget.Line> allocate(double limit, Map<String, Double> percentages, Map<String, String> rationale) {
        if (limit <= 0) {
            throw new DomainException("Set a budget limit before planning the budget.");
        }
        if (percentages.isEmpty()) {
            throw new DomainException("No budget categories given.");
        }
        double sum = 0;
        for (double p : percentages.values()) {
            if (p < 0) {
                throw new DomainException("Budget percentages cannot be negative.");
            }
            sum += p;
        }
        double scale = sum > 100.0001 ? 100.0 / sum : 1.0;
        List<Budget.Line> lines = new ArrayList<>();
        double allocated = 0;
        List<Map.Entry<String, Double>> entries = new ArrayList<>(percentages.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Double> e = entries.get(i);
            double amount = Math.floor(limit * e.getValue() * scale / 100.0);
            allocated += amount;
            if (i == entries.size() - 1 && sum * scale >= 99.999) {
                amount += Math.max(0, Math.floor(limit) - allocated);
            }
            lines.add(new Budget.Line(e.getKey(), amount,
                    rationale == null ? "" : rationale.getOrDefault(e.getKey(), "")));
        }
        return lines;
    }

    public Budget.Expense recordExpense(Event event, String category, double amount, LocalDate date, String description) {
        if (amount <= 0) {
            throw new DomainException("The expense amount must be greater than 0.");
        }
        if (category == null || category.isBlank()) {
            throw new DomainException("The expense needs a category.");
        }
        Budget.Expense expense = new Budget.Expense(event.budget().nextExpenseId(), category, amount, date, description);
        event.budget().addExpense(expense);
        return expense;
    }

    public BudgetStatus computeStatus(Event event) {
        Budget budget = event.budget();
        Set<String> categories = new LinkedHashSet<>();
        budget.lines().forEach(l -> categories.add(l.category()));
        budget.expenses().forEach(e -> categories.add(e.category()));

        Map<String, Double> spent = new LinkedHashMap<>();
        budget.expenses().forEach(e -> spent.merge(e.category(), e.amount(), Double::sum));

        List<CategoryStatus> rows = new ArrayList<>();
        List<String> alerts = new ArrayList<>();
        for (String c : categories) {
            double planned = budget.line(c).map(Budget.Line::amount).orElse(0.0);
            CategoryStatus row = new CategoryStatus(c, planned, spent.getOrDefault(c, 0.0));
            rows.add(row);
            if (row.overPlan()) {
                alerts.add(String.format("Category '%s' is over plan: spent $%.2f of $%.2f planned.",
                        c, row.spent(), row.planned()));
            }
        }
        double totalPlanned = budget.lines().stream().mapToDouble(Budget.Line::amount).sum();
        double totalSpent = budget.expenses().stream().mapToDouble(Budget.Expense::amount).sum();
        if (budget.limit() > 0 && totalSpent >= budget.limit() * ALERT_THRESHOLD) {
            alerts.add(String.format("%.0f%% of the budget is spent ($%.2f of $%.2f).",
                    100 * totalSpent / budget.limit(), totalSpent, budget.limit()));
        }
        if (budget.limit() > 0 && totalPlanned > budget.limit() + 0.01) {
            alerts.add(String.format("The plan ($%.2f) exceeds the budget limit ($%.2f).", totalPlanned, budget.limit()));
        }
        return new BudgetStatus(budget.limit(), totalPlanned, totalSpent, rows, alerts);
    }

    /** Remaining amount available for a category: its planned line minus what was spent, or the free budget. */
    public double remainingFor(Event event, String category) {
        BudgetStatus status = computeStatus(event);
        return status.categories().stream()
                .filter(c -> c.category().equals(Budget.normalize(category)))
                .findFirst()
                .map(c -> c.planned() - c.spent())
                .orElse(Math.max(0, status.limit() - status.totalPlanned()));
    }
}
