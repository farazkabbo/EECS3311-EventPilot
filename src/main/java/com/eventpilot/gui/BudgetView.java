package com.eventpilot.gui;

import com.eventpilot.domain.Budget;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.service.BudgetTracker;
import java.time.LocalDate;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** F07: planned vs spent table and chart, budget plan and expense form, budget alerts. */
public class BudgetView extends BaseView {

    private final TextField limit = new TextField();
    private final Label totals = new Label();
    private final TableView<BudgetTracker.CategoryStatus> table = new TableView<>();
    private final BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final ListView<String> alerts = new ListView<>();

    public BudgetView(GuiContext ctx, BudgetController controller) {
        super(ctx);
        Button setLimit = new Button("Set limit");
        setLimit.setOnAction(e -> controller.onSetLimit(limit.getText()));
        Button generate = new Button("Generate budget plan (AI)");
        generate.disableProperty().bind(ctx.busy().isEqualTo(true));
        generate.setOnAction(e -> controller.onGenerateBudget());
        HBox top = new HBox(6, new Label("Budget limit $"), limit, setLimit, generate, totals);

        table.getColumns().add(col("Category", c -> c.category()));
        table.getColumns().add(col("Planned", c -> String.format("%.2f", c.planned())));
        table.getColumns().add(col("Spent", c -> String.format("%.2f", c.spent())));
        table.getColumns().add(col("Status", c -> c.overPlan() ? "OVER PLAN" : ""));
        table.setPlaceholder(new Label("No budget lines yet."));
        chart.setTitle("Planned vs spent");
        chart.setAnimated(false);

        ComboBox<String> category = new ComboBox<>();
        category.getItems().addAll(Budget.STANDARD_CATEGORIES);
        category.setEditable(true);
        category.setPromptText("category");
        TextField amount = new TextField();
        amount.setPromptText("amount");
        DatePicker date = new DatePicker(LocalDate.now());
        TextField desc = new TextField();
        desc.setPromptText("description");
        Button add = new Button("Add expense");
        add.setOnAction(e -> {
            controller.onRecordExpense(category.getEditor().getText().isBlank() ? category.getValue()
                    : category.getEditor().getText(), amount.getText(), date.getValue(), desc.getText());
            amount.clear();
            desc.clear();
        });
        HBox expense = new HBox(6, new Label("Expense:"), category, amount, date, desc, add);

        alerts.setPrefHeight(70);
        alerts.setPlaceholder(new Label("No budget alerts."));
        HBox middle = new HBox(10, table, chart);
        HBox.setHgrow(chart, Priority.ALWAYS);
        VBox box = new VBox(10, top, middle, expense, new Label("Budget alerts"), alerts);
        VBox.setVgrow(middle, Priority.ALWAYS);
        setCenter(box);
    }

    private static TableColumn<BudgetTracker.CategoryStatus, String> col(String name,
            java.util.function.Function<BudgetTracker.CategoryStatus, String> f) {
        TableColumn<BudgetTracker.CategoryStatus, String> c = new TableColumn<>(name);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        c.setPrefWidth(100);
        return c;
    }

    @Override
    protected void refresh(Event e) {
        chart.getData().clear();
        if (e == null) {
            table.getItems().clear();
            alerts.getItems().clear();
            totals.setText("");
            return;
        }
        BudgetTracker.BudgetStatus s = ctx.facade().budgetStatus(e.id());
        limit.setText(String.format("%.0f", s.limit()));
        totals.setText(String.format("   planned $%.0f   spent $%.0f   remaining $%.0f", s.totalPlanned(), s.totalSpent(), s.remaining()));
        table.getItems().setAll(s.categories());
        XYChart.Series<String, Number> planned = new XYChart.Series<>();
        planned.setName("Planned");
        XYChart.Series<String, Number> spent = new XYChart.Series<>();
        spent.setName("Spent");
        for (BudgetTracker.CategoryStatus c : s.categories()) {
            planned.getData().add(new XYChart.Data<>(c.category(), c.planned()));
            spent.getData().add(new XYChart.Data<>(c.category(), c.spent()));
        }
        chart.getData().add(planned);
        chart.getData().add(spent);
        alerts.getItems().setAll(s.alerts());
    }
}
