package com.eventpilot.gui;

import com.eventpilot.domain.Event;
import com.eventpilot.domain.Venue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** F03: the chosen venue and the "Recommend venues" action (results open in the review dialog). */
public class VenueView extends BaseView {

    private final Label current = new Label();

    public VenueView(GuiContext ctx, EventController controller) {
        super(ctx);
        TextField needs = new TextField();
        needs.setPromptText("Extra needs (optional), e.g. wheelchair accessible, stage, parking");
        Button recommend = new Button("Recommend venues (AI)");
        recommend.disableProperty().bind(ctx.busy().isEqualTo(true));
        recommend.setOnAction(e -> controller.onRecommendVenues(needs.getText()));
        HBox row = new HBox(6, needs, recommend);
        HBox.setHgrow(needs, Priority.ALWAYS);
        current.setWrapText(true);
        Label help = new Label("The agent calls venue_search and budget_calculator, ranks the top 3 with reasons and "
                + "trade-offs, and you pick one. Venues not returned by the tool are blocked.");
        help.setWrapText(true);
        help.getStyleClass().add("help");
        Label heading = new Label("Venue");
        heading.getStyleClass().add("view-title");
        setCenter(new VBox(10, heading, current, row, help));
    }

    @Override
    protected void refresh(Event e) {
        if (e == null) {
            current.setText("No event selected.");
            return;
        }
        Venue v = e.venue();
        current.setText(v == null ? "No venue chosen yet." : String.format(
                "%s%n%s, %s (%s)%nCapacity %d, $%.0f/day, %s, rating %.1f/5%nFeatures: %s%nRooms: %s",
                v.name(), v.address(), v.city(), v.district(), v.capacity(), v.pricePerDay(),
                v.accessible() ? "wheelchair accessible" : "not wheelchair accessible", v.rating(),
                String.join(", ", v.features()), v.rooms().stream().map(r -> r.name() + " (" + r.capacity() + ")").toList()));
    }
}
