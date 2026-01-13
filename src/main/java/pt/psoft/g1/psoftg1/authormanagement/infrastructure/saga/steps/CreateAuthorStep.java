/*
 * Copyright (c) 2022-2024 the original author or authors.
 *
 * MIT License
 */
package pt.psoft.g1.psoftg1.authormanagement.infrastructure.saga.steps;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.saga.CreateAuthorSagaContext;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.saga.SagaStep;

/**
 * Step 2: Create the Author entity.
 * 
 * Creates the Author entity in the database using validated data from the
 * context.
 * 
 * Compensation: Delete the created Author from the database.
 */
@Component
@RequiredArgsConstructor
public class CreateAuthorStep implements SagaStep<CreateAuthorSagaContext> {

    private static final Logger log = LoggerFactory.getLogger(CreateAuthorStep.class);

    private final AuthorRepository authorRepository;

    @Override
    public boolean execute(CreateAuthorSagaContext context) {
        log.debug("Creating author: {}", context.getRequest().getName());

        try {
            Author newAuthor = new Author(
                    context.getRequest().getName(),
                    context.getRequest().getBio(),
                    context.getPhotoURI());

            Author savedAuthor = authorRepository.save(newAuthor);
            context.setCreatedAuthor(savedAuthor);

            log.info("Author created successfully with number: {}", savedAuthor.getAuthorNumber());
            return true;

        } catch (Exception e) {
            context.setErrorMessage("Failed to create author: " + e.getMessage());
            log.error("Failed to create author: {} - {}", context.getRequest().getName(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void compensate(CreateAuthorSagaContext context) {
        if (context.getCreatedAuthor() != null) {
            log.info("Compensating: Deleting author with number: {}", context.getCreatedAuthor().getAuthorNumber());
            try {
                authorRepository.delete(context.getCreatedAuthor());
                log.info("Author deleted successfully during compensation: {}",
                        context.getCreatedAuthor().getAuthorNumber());
            } catch (Exception e) {
                log.error("Failed to delete author during compensation: {} - {}",
                        context.getCreatedAuthor().getAuthorNumber(), e.getMessage(), e);
            }
        }
    }

    @Override
    public String getStepName() {
        return "CreateAuthorStep";
    }
}
