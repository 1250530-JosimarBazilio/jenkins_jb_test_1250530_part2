/*
 * Copyright (c) 2022-2024 the original author or authors.
 *
 * MIT License
 */
package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga.steps.CreateBookStep;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga.steps.PublishBookCreatedEventStep;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga.steps.ValidateBookStep;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.services.CreateBookRequest;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.shared.infrastructure.saga.SagaOrchestrator;
import pt.psoft.g1.psoftg1.shared.infrastructure.saga.SagaResult;

/**
 * Saga Orchestrator for creating a Book.
 * 
 * This orchestrator manages the Book creation process with compensating
 * transactions.
 * 
 * Saga Steps:
 * 1. Validate request (ISBN uniqueness, genre existence, authors validity)
 * 2. Create Book entity
 * 3. Publish BookCreated event to Outbox
 * 
 * If any step fails, all previous steps are compensated (rolled back) in
 * reverse order.
 */
@Service
@RequiredArgsConstructor
public class CreateBookSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CreateBookSagaOrchestrator.class);
    private static final String SAGA_NAME = "CreateBookSaga";

    private final ValidateBookStep validateBookStep;
    private final CreateBookStep createBookStep;
    private final PublishBookCreatedEventStep publishBookCreatedEventStep;

    /**
     * Executes the Book creation saga.
     * 
     * @param request  The book creation request
     * @param isbn     The ISBN for the new book
     * @param photoURI The URI of the uploaded photo (can be null)
     * @return The created Book if successful
     * @throws ConflictException if validation fails
     * @throws RuntimeException  if saga execution fails
     */
    @Transactional
    public Book execute(CreateBookRequest request, String isbn, String photoURI) {
        log.info("Starting {} for ISBN: {}", SAGA_NAME, isbn);

        // Prepare the saga context
        CreateBookSagaContext context = CreateBookSagaContext.of(request, isbn, photoURI);

        // Build and execute the saga
        SagaOrchestrator<CreateBookSagaContext> saga = new SagaOrchestrator<CreateBookSagaContext>(SAGA_NAME)
                .addStep(validateBookStep)
                .addStep(createBookStep)
                .addStep(publishBookCreatedEventStep);

        SagaResult<CreateBookSagaContext> result = saga.execute(context);

        // Handle the result
        if (result.isSuccess()) {
            log.info("Saga {} completed successfully. ISBN: {}",
                    SAGA_NAME, result.getContext().getCreatedBook().getIsbn());
            return result.getContext().getResult();
        } else {
            log.error("Saga {} failed at step: {} - {}",
                    SAGA_NAME, result.getFailedStep(), result.getErrorMessage());

            // If validation failed, throw a specific exception
            if (result.getContext().isValidationFailed()) {
                throw new ConflictException(result.getContext().getErrorMessage());
            }

            throw new RuntimeException("Failed to create book: " + result.getErrorMessage());
        }
    }
}
