package com.eventpilot.agent.workflow;

/** A workflow could not produce a usable result; the message is shown to the user. */
public class WorkflowException extends RuntimeException {

    public WorkflowException(String message) {
        super(message);
    }
}
