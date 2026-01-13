/*
 * Copyright (c) 2022-2024 the original author or authors.
 *
 * MIT License
 */
package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga.steps;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.api.BookViewAMQP;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga.CreateBookSagaContext;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.configuration.RabbitmqConfig;
import pt.psoft.g1.psoftg1.shared.infrastructure.saga.SagaStep;
import pt.psoft.g1.psoftg1.shared.model.BookEvents;
import pt.psoft.g1.psoftg1.shared.services.OutboxEventService;

/**
 * Step 3: Save BookCreated event to Outbox.
 * 
 * This step saves the BookCreated event to the transactional outbox table.
 * The event will be published asynchronously by the OutboxPublisher.
 * 
 * This implements the Outbox Pattern for reliable event publishing:
 * - Event is saved in the same transaction as Book creation
 * - Guarantees at-least-once delivery
 * - Survives RabbitMQ outages
 * 
 * Compensation: The outbox event remains but the saga failure will be logged
 */
@Component
@RequiredArgsConstructor
public class PublishBookCreatedEventStep implements SagaStep<CreateBookSagaContext> {

    private static final Logger log = LoggerFactory.getLogger(PublishBookCreatedEventStep.class);

    private final OutboxEventService outboxEventService;

    @Override
    public boolean execute(CreateBookSagaContext context) {
        Book book = context.getCreatedBook();

        log.debug("Saving BookCreated event to outbox for book: {}", book.getIsbn());

        try {
            // Build the AMQP view for the event
            BookViewAMQP bookView = new BookViewAMQP(
                    book.getIsbn(),
                    book.getTitle().toString(),
                    book.getDescription() != null ? book.getDescription().toString() : null,
                    book.getGenre().toString(),
                    book.getVersion());

            // Save to outbox (will be published asynchronously by OutboxPublisher)
            outboxEventService.saveEvent(
                    "Book",
                    book.getIsbn(),
                    "BookCreatedEvent",
                    bookView,
                    RabbitmqConfig.EXCHANGE_NAME,
                    BookEvents.BOOK_CREATED);

            log.info("Saved BookCreated event to outbox for book: {}", book.getIsbn());
            return true;

        } catch (Exception e) {
            context.setErrorMessage("Failed to save BookCreated event to outbox: " + e.getMessage());
            log.error("Failed to save BookCreated event to outbox for book: {} - {}",
                    book.getIsbn(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void compensate(CreateBookSagaContext context) {
        // With the Outbox pattern, compensation is simpler:
        // - If the transaction rolls back, the outbox event is also rolled back
        // - No need to worry about already-published events
        if (context.getCreatedBook() != null) {
            log.info("Saga rollback: Outbox event for book {} will be rolled back with the transaction",
                    context.getCreatedBook().getIsbn());
        }
    }

    @Override
    public String getStepName() {
        return "PublishBookCreatedEventStep";
    }
}
