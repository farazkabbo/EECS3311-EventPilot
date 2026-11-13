package com.eventpilot.agent.workflow;

/** Turns the LLM's final text into a typed result, or explains what is wrong with it. */
@FunctionalInterface
public interface ResponseParser<T> {

    T parse(String text) throws ParseException;

    class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }
}
