package com.eventpilot.cli;

import com.eventpilot.application.EventPlannerFacade;
import com.eventpilot.domain.Budget;
import com.eventpilot.domain.CateringPlan;
import com.eventpilot.domain.EventTask;
import com.eventpilot.domain.service.BudgetTracker;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** `budget ...` (F07), `catering ...` (F08), `task ...` (F09). */
public final class ResourceCliCommands {

    private ResourceCliCommands() {
    }

    @Command(name = "budget", description = "F07: budget planning and expense tracking.",
            subcommands = {BudgetCmd.Limit.class, BudgetCmd.Generate.class, BudgetCmd.Expense.class, BudgetCmd.Status.class})
    public static class BudgetCmd extends BaseCliCommand {
        @Override
        protected int run() {
            return Status.print(eventId());
        }

        @Command(name = "limit", description = "Set the total budget limit.")
        static class Limit extends BaseCliCommand {
            @Parameters(index = "0") double amount;

            @Override
            protected int run() {
                ctx.facade().setBudgetLimit(eventId(), amount);
                ctx.printer().ok(String.format("Budget limit set to $%.2f", amount));
                return 0;
            }
        }

        @Command(name = "generate", description = "AI budget split (amounts computed by BudgetTracker).")
        static class Generate extends BaseCliCommand {
            @Override
            protected int run() {
                int code = reviewAndApply(ctx.facade().generateBudget(eventId()));
                Status.print(eventId());
                return code;
            }
        }

        @Command(name = "expense", description = "Record an expense.")
        static class Expense extends BaseCliCommand {
            @Option(names = "--category", required = true) String category;
            @Option(names = "--amount", required = true) double amount;
            @Option(names = "--date") LocalDate date;
            @Option(names = "--desc", defaultValue = "") String description;

            @Override
            protected int run() {
                Budget.Expense e = ctx.facade().recordExpense(eventId(), category, amount, date, description);
                ctx.printer().ok(String.format("Recorded %s: $%.2f for %s", e.id(), e.amount(), e.category()));
                Status.print(eventId());
                return 0;
            }
        }

        @Command(name = "status", description = "Planned vs spent, with alerts.")
        static class Status extends BaseCliCommand {
            @Override
            protected int run() {
                return print(eventId());
            }

            static int print(String eventId) {
                BudgetTracker.BudgetStatus s = ctx.facade().budgetStatus(eventId);
                ctx.printer().heading("Budget");
                ctx.printer().println(String.format("  Limit $%.2f   planned $%.2f   spent $%.2f   remaining $%.2f",
                        s.limit(), s.totalPlanned(), s.totalSpent(), s.remaining()));
                for (BudgetTracker.CategoryStatus c : s.categories()) {
                    ctx.printer().println(String.format("  %-12s planned %10.2f   spent %10.2f%s", c.category(), c.planned(),
                            c.spent(), c.overPlan() ? "   OVER PLAN" : ""));
                }
                s.alerts().forEach(a -> ctx.printer().println("  ALERT " + a));
                return 0;
            }
        }
    }

    @Command(name = "catering", description = "F08: dietary-aware catering.",
            subcommands = {Catering.Summary.class, Catering.Plan.class, Catering.Show.class})
    public static class Catering extends BaseCliCommand {
        @Override
        protected int run() {
            return Show.print(eventId());
        }

        @Command(name = "summary", description = "Headcount and dietary counts (deterministic).")
        static class Summary extends BaseCliCommand {
            @Override
            protected int run() {
                EventPlannerFacade.CateringSummary s = ctx.facade().cateringSummary(eventId());
                ctx.printer().println("  Headcount: " + s.headcount().count()
                        + (s.headcount().fromRsvps() ? " (confirmed RSVPs)" : " (" + s.headcount().warning() + ")"));
                ctx.printer().println("  Dietary needs: " + (s.diets().isEmpty() ? "none" : s.diets()));
                return 0;
            }
        }

        @Command(name = "plan", description = "AI catering plan covering every dietary need.")
        static class Plan extends BaseCliCommand {
            @Override
            protected int run() {
                int code = reviewAndApply(ctx.facade().planCatering(eventId()));
                Show.print(eventId());
                return code;
            }
        }

        @Command(name = "show", description = "Show the catering plan.")
        static class Show extends BaseCliCommand {
            @Override
            protected int run() {
                return print(eventId());
            }

            static int print(String eventId) {
                CateringPlan plan = ctx.facade().getEvent(eventId).cateringPlan();
                ctx.printer().heading("Catering");
                if (plan == null) {
                    ctx.printer().println("  No plan yet - try `catering plan`.");
                    return 0;
                }
                DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
                for (CateringPlan.Meal m : plan.meals()) {
                    ctx.printer().println(String.format("  %s %s - %d portions x $%.2f = $%.2f", m.time().format(hm), m.name(),
                            m.portions(), m.costPerPortion(), m.cost()));
                    m.menu().forEach(i -> ctx.printer().println("      - " + i.name() + (i.suitableFor().isEmpty() ? "" : " " + i.suitableFor())));
                }
                ctx.printer().println(String.format("  Estimated total $%.2f. %s", plan.estimatedCost(), plan.notes()));
                return 0;
            }
        }
    }

    @Command(name = "task", description = "F09: task plan and tracking.",
            subcommands = {Task.Generate.class, Task.ListCmd.class, Task.Add.class, Task.Status.class})
    public static class Task extends BaseCliCommand {
        @Override
        protected int run() {
            return ListCmd.print(eventId());
        }

        @Command(name = "generate", description = "AI task plan planned backwards from the event date.")
        static class Generate extends BaseCliCommand {
            @Option(names = "--team", split = ",", description = "Team member names, comma-separated")
            List<String> team = new ArrayList<>();

            @Override
            protected int run() {
                int code = reviewAndApply(ctx.facade().generateTasks(eventId(), team));
                ListCmd.print(eventId());
                return code;
            }
        }

        @Command(name = "list", description = "Tasks in dependency order.")
        static class ListCmd extends BaseCliCommand {
            @Override
            protected int run() {
                return print(eventId());
            }

            static int print(String eventId) {
                List<EventTask> tasks = ctx.facade().orderedTasks(eventId);
                ctx.printer().heading("Tasks");
                if (tasks.isEmpty()) {
                    ctx.printer().println("  No tasks yet - try `task generate --team Sam,Lee`.");
                }
                LocalDate today = LocalDate.now();
                tasks.forEach(t -> ctx.printer().println("  " + t + (t.isOverdue(today) ? "  OVERDUE" : "")));
                return 0;
            }
        }

        @Command(name = "add", description = "Add a task manually.")
        static class Add extends BaseCliCommand {
            @Option(names = "--title", required = true) String title;
            @Option(names = "--owner", defaultValue = "organizer") String owner;
            @Option(names = "--due", required = true) LocalDate due;
            @Option(names = "--after", split = ",") List<String> after = new ArrayList<>();

            @Override
            protected int run() {
                ctx.printer().ok("Added " + ctx.facade().addTask(eventId(), title, owner, due, after));
                return 0;
            }
        }

        @Command(name = "status", description = "Set status: status <id> TODO|IN_PROGRESS|DONE")
        static class Status extends BaseCliCommand {
            @Parameters(index = "0") String taskId;
            @Parameters(index = "1") EventTask.Status status;

            @Override
            protected int run() {
                ctx.printer().ok("Updated " + ctx.facade().updateTaskStatus(eventId(), taskId, status));
                return 0;
            }
        }
    }
}
