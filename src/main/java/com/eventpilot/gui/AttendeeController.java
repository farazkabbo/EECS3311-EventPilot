package com.eventpilot.gui;

import com.eventpilot.application.EventPlannerFacade;
import com.eventpilot.domain.Attendee;
import com.eventpilot.infra.AttendeeCsvImporter;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javafx.stage.FileChooser;

/** Attendees and RSVPs (F02), catering (F08) and invitations (F10). */
public class AttendeeController extends BaseController {

    public AttendeeController(GuiContext ctx) {
        super(ctx);
    }

    public void onAddAttendee(String name, String email, Attendee.Role role, String diets, String access) {
        requireEvent().ifPresent(id -> run("Attendee added", () -> ctx.facade().addAttendee(id, name, email, role,
                Arrays.stream(diets.split("[;,]")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                access, Attendee.Rsvp.INVITED)));
    }

    public void onImportCsv() {
        requireEvent().ifPresent(id -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Import attendees (" + AttendeeCsvImporter.EXPECTED_HEADER + ")");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File samples = new File("samples");
            if (samples.isDirectory()) {
                chooser.setInitialDirectory(samples);
            }
            File file = chooser.showOpenDialog(ctx.stage());
            if (file == null) {
                return;
            }
            run("Imported attendees", () -> {
                AttendeeCsvImporter.ImportReport r = ctx.facade().importAttendees(id, file.toPath());
                StringBuilder sb = new StringBuilder(r.summary());
                r.rejected().forEach(x -> sb.append("\nrejected ").append(x));
                r.duplicates().forEach(x -> sb.append("\nduplicate ").append(x));
                info("Import report", sb.toString());
            });
        });
    }

    public void onUpdateRsvp(String attendeeId, Attendee.Rsvp rsvp) {
        requireEvent().ifPresent(id -> run("RSVP updated", () -> ctx.facade().updateRsvp(id, attendeeId, rsvp)));
    }

    public void onRemove(String attendeeId) {
        requireEvent().ifPresent(id -> run("Attendee removed", () -> ctx.facade().removeAttendee(id, attendeeId)));
    }

    public void onPlanCatering() {
        requireEvent().ifPresent(id -> runAsync("Agent is planning the catering",
                () -> ctx.facade().planCatering(id), this::reviewAndApply));
    }

    public void onDraftInvitations(String tone, Runnable done) {
        requireEvent().ifPresent(id -> runAsync("Agent is drafting invitations",
                () -> ctx.facade().draftInvitations(id, tone), list -> {
                    ctx.status().set(list.size() + " invitation(s) drafted - review and edit before sending");
                    done.run();
                }));
    }

    public void onEditInvitation(String email, String subject, String body) {
        requireEvent().ifPresent(id -> run("Invitation saved", () -> ctx.facade().editInvitation(id, email, subject, body)));
    }

    public void onSendInvitations() {
        requireEvent().ifPresent(id -> run("Invitations sent", () -> {
            EventPlannerFacade.DeliveryReport r = ctx.facade().sendInvitations(id);
            List<String> failed = r.failed();
            info("Delivery report", r.summary() + (failed.isEmpty() ? "" : "\n\nFailed:\n" + String.join("\n", failed)));
        }));
    }
}
