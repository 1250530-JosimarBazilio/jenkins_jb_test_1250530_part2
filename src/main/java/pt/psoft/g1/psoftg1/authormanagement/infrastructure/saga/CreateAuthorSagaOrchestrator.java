/*
 * Copyright (c) 2022-2024 the original author or authors.
 *
 * MIT License
 */
package pt.psoft.g1.psoftg1.authormanagement.infrastructure.saga;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.saga.steps.CreateAuthorStep;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.saga.steps.PublishAuthorCreatedEventStep;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.saga.steps.ValidateAuthorStep;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.services.CreateAuthorRequest;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.shared.infrastructure.saga.SagaOrchestrator;
import pt.psoft.g1.psoftg1.shared.infrastructure.saga.SagaResult;

/**
 * Saga Orchestrator for creating an Author.
 * 
 * This orchestrator manages the Author creation process with compensating
 * transactions.
 * 
 * Saga Steps:
 * 1. Validate request (name format, bio length, etc.)
 * 2. Create Author entity
 * 3. Publish AuthorCreated event to Outbox
 * 
 * If any step fails, all previous steps are compensated (rolled back) in
 * reverse order.
 */
@Service
@RequiredArgsConstructor
public class CreateAuthorSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CreateAuthorSagaOrchestrator.class);
    private static final String SAGA_NAME = "CreateAuthorSaga";

    private final ValidateAuthorStep validateAuthorStep;
    private final CreateAuthorStep createAuthorStep;
    private final PublishAuthorCreatedEventStep publishAuthorCreatedEventStep;

    /**
     * Executes the Author creation saga.
     * 
     * @param request  The author creation request
     * @param photoURI The URI of the uploaded photo (can be null)
     * @return The created Author if successful
     * @throws ConflictException if validation fails
     * @throws RuntimeException  if saga execution fails
     */
    @Transactional
    public Author execute(CreateAuthorRequest request, String photoURI) {
        log.info("Starting {} for author: {}", SAGA_NAME, request.getName());

        // Prepare the saga context
        CreateAuthorSagaContext context = CreateAuthorSagaContext.of(request, photoURI);

        // Build and execute the saga
        SagaOrchestrator<CreateAuthorSagaContext> saga = new SagaOrchestrator<CreateAuthorSagaContext>(SAGA_NAME)
                .addStep(validateAuthorStep)
                .addStep(createAuthorStep)
                .addStep(publishAuthorCreatedEventStep);

        SagaResult<CreateAuthorSagaContext> result = saga.execute(context);

        // Handle the result
        if (result.isSuccess()) {
            log.info("Saga {} completed successfully. Author number: {}",
                    SAGA_NAME, result.getContext().getCreatedAuthor().getAuthorNumber());
            return result.getContext().getResult();
        } else {
            log.error("Saga {} failed at step: {} - {}",
                    SAGA_NAME, result.getFailedStep(), result.getErrorMessage());

            // If validation failed, throw a specific exception
            if (result.getContext().isValidationFailed()) {
                throw new ConflictException(result.getContext().getErrorMessage());
            }

            throw new RuntimeException("Failed to create author: " + result.getErrorMessage());
        }
    }
}
