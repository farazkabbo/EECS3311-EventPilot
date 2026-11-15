package com.eventpilot.gui;

import com.eventpilot.domain.DomainException;
import java.time.LocalDate;

/** F07: budget limit, AI budget plan and expense recording. */
public class BudgetController extends BaseController {

    public BudgetController(GuiContext ctx) {
        super(ctx);
    }

    public void onSetLimit(String text) {
        requireEvent().ifPresent(id -> run("Budget limit updated", () -> ctx.facade().setBudgetLimit(id, number(text, "limit"))));
    }

    public void onGenerateBudget() {
        requireEvent().ifPresent(id -> runAsync("Agent is planning the budget",
                () -> ctx.facade().generateBudget(id), this::reviewAndApply));
    }

    public boolean onRecordExpense(String category, String amount, LocalDate date, String description) {
        return requireEvent().map(id -> run("Expense recorded", () -> {
            if (category == null || category.isBlank()) {
                throw new DomainException("Choose a category.");
            }
            ctx.facade().recordExpense(id, category, number(amount, "amount"), date, description);
        })).orElse(false);
    }

    private static double number(String text, String what) {
        try {
            return Double.parseDouble(text.trim().replace("$", "").replace(",", ""));
        } catch (NumberFormatException e) {
            throw new DomainException("The " + what + " must be a number.");
        }
    }
}
