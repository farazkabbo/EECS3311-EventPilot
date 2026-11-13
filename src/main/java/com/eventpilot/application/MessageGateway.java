package com.eventpilot.application;

import com.eventpilot.domain.Invitation;

/** Port for delivering invitations (the "Email Gateway" actor of the use-case diagram). */
public interface MessageGateway {

    /** Delivers one invitation; throws {@link DeliveryException} when it cannot be delivered. */
    void send(Invitation invitation) throws DeliveryException;

    class DeliveryException extends Exception {
        public DeliveryException(String message) {
            super(message);
        }
    }
}
