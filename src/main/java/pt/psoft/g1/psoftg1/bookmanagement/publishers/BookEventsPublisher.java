package pt.psoft.g1.psoftg1.bookmanagement.publishers;

import pt.psoft.g1.psoftg1.bookmanagement.api.BookViewAMQP;

/**
 * Interface for publishing book events.
 * Follows the publisher pattern from reference implementation.
 */
public interface BookEventsPublisher {
    
    void sendBookCreated(BookViewAMQP bookView);
    
    void sendBookUpdated(BookViewAMQP bookView);
    
    void sendBookDeleted(BookViewAMQP bookView);
}
