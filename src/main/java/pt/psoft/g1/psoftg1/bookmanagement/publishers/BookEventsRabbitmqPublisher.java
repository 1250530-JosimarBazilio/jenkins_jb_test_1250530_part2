package pt.psoft.g1.psoftg1.bookmanagement.publishers;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.api.BookViewAMQP;
import pt.psoft.g1.psoftg1.configuration.RabbitmqConfig;

/**
 * RabbitMQ implementation of BookEventsPublisher.
 * Publishes book events to FanoutExchanges for broadcast to ALL instances.
 * 
 * Uses FanoutExchange instead of DirectExchange to ensure all LMS-Books
 * instances receive all events and can sync their local databases.
 */
@Profile("!test")
@Component
public class BookEventsRabbitmqPublisher implements BookEventsPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.instance-id:default}")
    private String instanceId;

    public BookEventsRabbitmqPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void sendBookCreated(BookViewAMQP bookView) {
        // Publish to FanoutExchange (broadcast to all instances)
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.FANOUT_EXCHANGE_CREATED,
                "", // Fanout ignores routing key
                bookView);
        // Also publish to DirectExchange for other services (e.g., Lending service)
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.EXCHANGE_NAME,
                "book.created",
                bookView);
        System.out.println("[" + instanceId + "] Published BOOK_CREATED event for ISBN: " + bookView.getIsbn());
    }

    @Override
    public void sendBookUpdated(BookViewAMQP bookView) {
        // Publish to FanoutExchange (broadcast to all instances)
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.FANOUT_EXCHANGE_UPDATED,
                "", // Fanout ignores routing key
                bookView);
        // Also publish to DirectExchange for other services
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.EXCHANGE_NAME,
                "book.updated",
                bookView);
        System.out.println("[" + instanceId + "] Published BOOK_UPDATED event for ISBN: " + bookView.getIsbn());
    }

    @Override
    public void sendBookDeleted(BookViewAMQP bookView) {
        // Publish to FanoutExchange (broadcast to all instances)
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.FANOUT_EXCHANGE_DELETED,
                "", // Fanout ignores routing key
                bookView);
        // Also publish to DirectExchange for other services
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.EXCHANGE_NAME,
                "book.deleted",
                bookView);
        System.out.println("[" + instanceId + "] Published BOOK_DELETED event for ISBN: " + bookView.getIsbn());
    }
}
