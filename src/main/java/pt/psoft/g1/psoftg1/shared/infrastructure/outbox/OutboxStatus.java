package pt.psoft.g1.psoftg1.shared.infrastructure.outbox;

/**
 * Status of an outbox event.
 * 
 * @see OutboxEvent
 */
public enum OutboxStatus {

    /**
     * Event is waiting to be published to the message broker.
     */
    PENDING,

    /**
     * Event was successfully published to the message broker.
     */
    PUBLISHED,

    /**
     * Event failed to publish after maximum retry attempts.
     */
    FAILED
}
