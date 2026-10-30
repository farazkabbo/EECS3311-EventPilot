package com.eventpilot.gui;

import com.eventpilot.application.EventPlannerFacade;
import com.eventpilot.domain.CateringPlan;
import com.eventpilot.domain.Event;
import java.time.format.DateTimeFormatter;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** F08: computed headcount and dietary summary, "Plan catering" and the meal cards. */
public class CateringView extends BaseView {

    private final Label summary = new Label();
    private final TextArea meals = new TextArea();

    public CateringView(GuiContext ctx, AttendeeController controller) {
        super(ctx);
        Button plan = new Button("Plan catering (AI)");
        plan.disableProperty().bind(ctx.busy().isEqualTo(true));
        plan.setOnAction(e -> controller.onPlanCatering());
        meals.setEditable(false);
        meals.setWrapText(true);
        summary.setWrapText(true);
        Label help = new Label("Headcount and dietary counts are computed from RSVPs; the agent designs menus; "
                + "portions, cost and dietary coverage are checked in code.");
        help.getStyleClass().add("help");
        help.setWrapText(true);
        VBox box = new VBox(10, summary, plan, help, meals);
        VBox.setVgrow(meals, Priority.ALWAYS);
        setCenter(box);
    }

    @Override
    protected void refresh(Event e) {
        if (e == null) {
            summary.setText("");
            meals.clear();
            return;
        }
        EventPlannerFacade.CateringSummary s = ctx.facade().cateringSummary(e.id());
        summary.setText("Headcount: " + s.headcount().count()
                + (s.headcount().fromRsvps() ? " confirmed" : " (" + s.headcount().warning() + ")")
                + "\nDietary needs: " + (s.diets().isEmpty() ? "none recorded" : s.diets()));
        CateringPlan plan = e.cateringPlan();
        if (plan == null) {
            meals.setText("No catering plan yet.");
            return;
        }
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        StringBuilder sb = new StringBuilder();
        for (CateringPlan.Meal m : plan.meals()) {
            sb.append(String.format("%s  %s  -  %d portions x $%.2f = $%.2f%n", m.time().format(hm), m.name(),
                    m.portions(), m.costPerPortion(), m.cost()));
            m.menu().forEach(i -> sb.append("     - ").append(i.name())
                    .append(i.suitableFor().isEmpty() ? "" : "  " + i.suitableFor()).append('\n'));
            sb.append('\n');
        }
        sb.append(String.format("Estimated total: $%.2f%n%s", plan.estimatedCost(), plan.notes()));
        meals.setText(sb.toString());
    }
}
