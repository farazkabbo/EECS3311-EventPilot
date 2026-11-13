package com.eventpilot.infra;

import com.eventpilot.application.MessageGateway;
import com.eventpilot.domain.Invitation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Delivers invitations by writing one .eml-style file per message into an outbox
 * folder (no real e-mail is sent). Addresses on the reserved ".invalid" domain
 * are rejected, which lets the demo show a failed delivery.
 */
public class OutboxFileGateway implements MessageGateway {

    private final Path outboxDir;

    public OutboxFileGateway(Path outboxDir) {
        this.outboxDir = outboxDir;
    }

    @Override
    public void send(Invitation invitation) throws DeliveryException {
        String to = invitation.email();
        if (to == null || to.toLowerCase(Locale.ROOT).endsWith(".invalid")) {
            throw new DeliveryException("mailbox does not exist: " + to);
        }
        String fileName = to.replaceAll("[^a-zA-Z0-9._-]", "_") + ".eml";
        String content = "To: " + to + "\n"
                + "Subject: " + invitation.subject() + "\n"
                + "Date: " + LocalDateTime.now() + "\n"
                + "X-Group: " + invitation.group() + "\n\n"
                + invitation.body() + "\n";
        try {
            Files.createDirectories(outboxDir);
            Files.writeString(outboxDir.resolve(fileName), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DeliveryException("could not write to outbox: " + e.getMessage());
        }
    }

    public Path outboxDir() {
        return outboxDir;
    }
}
