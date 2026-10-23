package com.eventpilot.gui;

import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

/** UC13: shows the agent's proposal - changes with reasons, guardrail warnings and the tool trace. */
public class ProposalReviewDialog extends Dialog<List<Integer>> {

    public ProposalReviewDialog(PlanProposal proposal) {
        setTitle("Review agent proposal");
        setHeaderText(proposal.title() + (proposal.summary().isBlank() ? "" : "\n" + proposal.summary()));
        setResizable(true);

        VBox box = new VBox(8);
        List<BooleanSupplier> selected = new ArrayList<>();
        ToggleGroup group = new ToggleGroup();
        box.getChildren().add(new Label(proposal.alternatives() ? "Choose one option:" : "Select the changes to apply:"));
        int preferred = firstUnblocked(proposal);
        for (int i = 0; i < proposal.changes().size(); i++) {
            PlanProposal.Change c = proposal.changes().get(i);
            String text = (i + 1) + ". " + c.describe() + (c.reason().isBlank() ? "" : "\n     why: " + c.reason());
            if (proposal.alternatives()) {
                RadioButton r = new RadioButton(text);
                r.setToggleGroup(group);
                r.setSelected(i == preferred);
                r.setWrapText(true);
                box.getChildren().add(r);
                selected.add(r::isSelected);
            } else {
                CheckBox cb = new CheckBox(text);
                cb.setSelected(true);
                cb.setWrapText(true);
                box.getChildren().add(cb);
                selected.add(cb::isSelected);
            }
        }
        if (!proposal.warnings().isEmpty()) {
            box.getChildren().add(new Label("Checks by the guardrails:"));
            for (Warning w : proposal.warnings()) {
                Label l = new Label(w.toString());
                l.setWrapText(true);
                l.getStyleClass().add(w.isBlocking() ? "warning-high" : "warning-low");
                box.getChildren().add(l);
            }
            if (proposal.hasBlockingWarnings()) {
                Label l = new Label("Changes with HIGH problems will be refused when applied.");
                l.getStyleClass().add("warning-high");
                box.getChildren().add(l);
            }
        }
        if (!proposal.trace().isEmpty()) {
            TextArea trace = new TextArea(String.join("\n", proposal.trace()));
            trace.setEditable(false);
            trace.setWrapText(true);
            trace.setPrefRowCount(6);
            TitledPane pane = new TitledPane("Agent tool calls (" + proposal.trace().size() + ")", trace);
            pane.setExpanded(false);
            box.getChildren().add(pane);
        }
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(660, 460);
        getDialogPane().setContent(scroll);

        ButtonType apply = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        ButtonType reject = new ButtonType("Reject", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(apply, reject);
        setResultConverter(button -> {
            if (button != apply) {
                return null;
            }
            List<Integer> chosen = new ArrayList<>();
            for (int i = 0; i < selected.size(); i++) {
                if (selected.get(i).getAsBoolean()) {
                    chosen.add(i);
                }
            }
            return chosen.isEmpty() ? null : chosen;
        });
    }

    private static int firstUnblocked(PlanProposal p) {
        for (int i = 0; i < p.changes().size(); i++) {
            String prefix = "Option " + (i + 1) + ":";
            if (p.warnings().stream().noneMatch(w -> w.isBlocking() && w.message().startsWith(prefix))) {
                return i;
            }
        }
        return 0;
    }
}
