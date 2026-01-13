package pt.psoft.g1.psoftg1.authormanagement.publishers;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.authormanagement.api.AuthorViewAMQP;
import pt.psoft.g1.psoftg1.configuration.RabbitmqConfig;

/**
 * RabbitMQ implementation of AuthorEventsPublisher.
 * 
 * Publishes author events to FanoutExchanges for broadcast to ALL instances.
 * Uses FanoutExchange instead of DirectExchange to ensure all LMS-Books
 * instances receive all events and can sync their local databases.
 */
@Profile("!test")
@Component
public class AuthorEventsRabbitmqPublisher implements AuthorEventsPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.instance-id:default}")
    private String instanceId;

    public AuthorEventsRabbitmqPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void sendAuthorCreated(AuthorViewAMQP authorView) {
        // Publish to FanoutExchange (broadcast to all instances)
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.AUTHOR_FANOUT_EXCHANGE_CREATED,
                "", // Fanout ignores routing key
                authorView);
        // Also publish to DirectExchange for other services
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.EXCHANGE_NAME,
                "author.created",
                authorView);
        System.out.println("[" + instanceId + "] Published AUTHOR_CREATED event for author number: "
                + authorView.getAuthorNumber());
    }

    @Override
    public void sendAuthorUpdated(AuthorViewAMQP authorView) {
        // Publish to FanoutExchange (broadcast to all instances)
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.AUTHOR_FANOUT_EXCHANGE_UPDATED,
                "", // Fanout ignores routing key
                authorView);
        // Also publish to DirectExchange for other services
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.EXCHANGE_NAME,
                "author.updated",
                authorView);
        System.out.println("[" + instanceId + "] Published AUTHOR_UPDATED event for author number: "
                + authorView.getAuthorNumber());
    }

    @Override
    public void sendAuthorDeleted(AuthorViewAMQP authorView) {
        // Publish to FanoutExchange (broadcast to all instances)
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.AUTHOR_FANOUT_EXCHANGE_DELETED,
                "", // Fanout ignores routing key
                authorView);
        // Also publish to DirectExchange for other services
        rabbitTemplate.convertAndSend(
                RabbitmqConfig.EXCHANGE_NAME,
                "author.deleted",
                authorView);
        System.out.println("[" + instanceId + "] Published AUTHOR_DELETED event for author number: "
                + authorView.getAuthorNumber());
    }
}
