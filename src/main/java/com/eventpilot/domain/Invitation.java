package com.eventpilot.domain;

/** An invitation drafted for one attendee, and its delivery status. */
public class Invitation {

    public enum Status { DRAFT, SENT, FAILED }

    private String attendeeId;
    private String email;
    private String group;
    private String subject;
    private String body;
    private Status status;
    private String error;

    private Invitation() {
    }

    public Invitation(String attendeeId, String email, String group, String subject, String body) {
        this.attendeeId = attendeeId;
        this.email = email;
        this.group = group;
        this.subject = subject;
        this.body = body;
        this.status = Status.DRAFT;
    }

    public void edit(String newSubject, String newBody) {
        if (status == Status.SENT) {
            throw new DomainException("Invitation to " + email + " was already sent");
        }
        this.subject = newSubject;
        this.body = newBody;
    }

    public void markSent() {
        this.status = Status.SENT;
        this.error = null;
    }

    public void markFailed(String reason) {
        this.status = Status.FAILED;
        this.error = reason;
    }

    public String attendeeId() { return attendeeId; }
    public String email() { return email; }
    public String group() { return group; }
    public String subject() { return subject; }
    public String body() { return body; }
    public Status status() { return status; }
    public String error() { return error; }
}
