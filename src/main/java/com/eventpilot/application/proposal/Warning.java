package com.eventpilot.application.proposal;

/** A problem found by the guardrails. HIGH warnings block approval. */
public record Warning(Severity severity, String message) {

    public enum Severity { LOW, MEDIUM, HIGH }

    public static Warning high(String message) {
        return new Warning(Severity.HIGH, message);
    }

    public static Warning medium(String message) {
        return new Warning(Severity.MEDIUM, message);
    }

    public static Warning low(String message) {
        return new Warning(Severity.LOW, message);
    }

    public boolean isBlocking() {
        return severity == Severity.HIGH;
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + message;
    }
}
