package com.eventpilot.domain;

/** Thrown when an operation would break a business rule (shown to the user as-is). */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
