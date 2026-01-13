package pt.psoft.g1.psoftg1.bookmanagement.publishers;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.api.BookViewAMQP;

/**
 * No-operation implementation of BookEventsPublisher.
 * Used in test profile when RabbitMQ is not available.
 */
@Profile("test")
@Component
public class BookEventsNoOpPublisher implements BookEventsPublisher {

    @Override
    public void sendBookCreated(BookViewAMQP bookView) {
        // No-op: Do nothing in test environment
    }

    @Override
    public void sendBookUpdated(BookViewAMQP bookView) {
        // No-op: Do nothing in test environment
    }

    @Override
    public void sendBookDeleted(BookViewAMQP bookView) {
        // No-op: Do nothing in test environment
    }
}
