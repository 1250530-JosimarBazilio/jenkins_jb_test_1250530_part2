package pt.psoft.g1.psoftg1.authormanagement.publishers;

import pt.psoft.g1.psoftg1.authormanagement.api.AuthorViewAMQP;

/**
 * Interface for publishing author events.
 * 
 * Follows the publisher pattern from reference implementation.
 * This interface abstracts the event publishing mechanism, allowing
 * different implementations (RabbitMQ, Kafka, etc.).
 */
public interface AuthorEventsPublisher {

    /**
     * Publishes an event when a new author is created.
     * 
     * @param authorView The author data to publish
     */
    void sendAuthorCreated(AuthorViewAMQP authorView);

    /**
     * Publishes an event when an author is updated.
     * 
     * @param authorView The updated author data to publish
     */
    void sendAuthorUpdated(AuthorViewAMQP authorView);

    /**
     * Publishes an event when an author is deleted.
     * 
     * @param authorView The deleted author data to publish
     */
    void sendAuthorDeleted(AuthorViewAMQP authorView);
}
