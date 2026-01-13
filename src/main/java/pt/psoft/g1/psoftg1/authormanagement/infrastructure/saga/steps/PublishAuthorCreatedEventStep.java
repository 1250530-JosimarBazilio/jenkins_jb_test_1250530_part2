/*
 * Copyright (c) 2022-2024 the original author or authors.
 *
 * MIT License
 */
package pt.psoft.g1.psoftg1.authormanagement.infrastructure.saga.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.authormanagement.api.AuthorViewAMQP;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.saga.CreateAuthorSagaContext;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.configuration.RabbitmqConfig;
import pt.psoft.g1.psoftg1.shared.infrastructure.outbox.OutboxEvent;
import pt.psoft.g1.psoftg1.shared.infrastructure.outbox.OutboxEventRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.saga.SagaStep;

/**
 * Step 3: Publish AuthorCreated event to Outbox.
 * 
 * Saves an AuthorCreated event to the outbox table in the same transaction.
 * The OutboxPublisher will pick it up and publish to RabbitMQ asynchronously.
 * 
 * Compensation: Event is rolled back with the transaction (no explicit action
 * needed)
 */
@Component
@RequiredArgsConstructor
public class PublishAuthorCreatedEventStep implements SagaStep<CreateAuthorSagaContext> {

    private static final Logger log = LoggerFactory.getLogger(PublishAuthorCreatedEventStep.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public boolean execute(CreateAuthorSagaContext context) {
        log.debug("Publishing AuthorCreated event for author: {}", context.getCreatedAuthor().getAuthorNumber());

        try {
            Author author = context.getCreatedAuthor();

            // Create AMQP view for the event
            AuthorViewAMQP authorView = new AuthorViewAMQP(
                    author.getAuthorNumber(),
                    author.getName(),
                    author.getBio(),
                    author.getPhotoURI(),
                    author.getVersion());

            // Serialize to JSON
            String payload = objectMapper.writeValueAsString(authorView);

            // Create outbox event
            OutboxEvent event = new OutboxEvent(
                    "Author",
                    author.getAuthorNumber().toString(),
                    "AuthorCreatedEvent",
                    payload,
                    RabbitmqConfig.AUTHOR_FANOUT_EXCHANGE_CREATED,
                    "author.created");

            // Save to outbox (will be published asynchronously by OutboxPublisher)
            outboxEventRepository.save(event);

            log.info("Saved AuthorCreated event to outbox for author: {}", author.getAuthorNumber());
            return true;

        } catch (JsonProcessingException e) {
            context.setErrorMessage("Failed to serialize author event: " + e.getMessage());
            log.error("Failed to serialize AuthorCreated event: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            context.setErrorMessage("Failed to save author event to outbox: " + e.getMessage());
            log.error("Failed to save AuthorCreated event to outbox: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void compensate(CreateAuthorSagaContext context) {
        // The outbox event is saved in the same transaction as the saga
        // When the transaction rolls back, the outbox event is also rolled back
        // No explicit compensation needed
        log.debug(
                "No explicit compensation needed for PublishAuthorCreatedEventStep - event will be rolled back with transaction");
    }

    @Override
    public String getStepName() {
        return "PublishAuthorCreatedEventStep";
    }
}
