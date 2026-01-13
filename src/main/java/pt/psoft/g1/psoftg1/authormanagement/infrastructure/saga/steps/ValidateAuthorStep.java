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
import pt.psoft.g1.psoftg1.shared.infrastructure.saga.SagaStep;

/**
 * Step 1: Validate the Author creation request.
 * 
 * Validates:
 * - Name is not null or empty
 * - Name length is within limits
 * - Bio is not null or empty
 * - Bio length is within limits
 * 
 * Compensation: None (no state created during validation)
 */
@Component
@RequiredArgsConstructor
public class ValidateAuthorStep implements SagaStep<CreateAuthorSagaContext> {

    private static final Logger log = LoggerFactory.getLogger(ValidateAuthorStep.class);

    private static final int NAME_MAX_LENGTH = 150;
    private static final int BIO_MAX_LENGTH = 4096;

    @Override
    public boolean execute(CreateAuthorSagaContext context) {
        log.debug("Validating author request for: {}", context.getRequest().getName());

        try {
            // Validate name
            String name = context.getRequest().getName();
            if (name == null || name.isBlank()) {
                context.setErrorMessage("Author name cannot be null or blank");
                context.setValidationFailed(true);
                log.error("Validation failed: Author name cannot be null or blank");
                return false;
            }
            if (name.length() > NAME_MAX_LENGTH) {
                context.setErrorMessage("Author name cannot exceed " + NAME_MAX_LENGTH + " characters");
                context.setValidationFailed(true);
                log.error("Validation failed: Author name exceeds maximum length");
                return false;
            }

            // Validate bio
            String bio = context.getRequest().getBio();
            if (bio == null || bio.isBlank()) {
                context.setErrorMessage("Author bio cannot be null or blank");
                context.setValidationFailed(true);
                log.error("Validation failed: Author bio cannot be null or blank");
                return false;
            }
            if (bio.length() > BIO_MAX_LENGTH) {
                context.setErrorMessage("Author bio cannot exceed " + BIO_MAX_LENGTH + " characters");
                context.setValidationFailed(true);
                log.error("Validation failed: Author bio exceeds maximum length");
                return false;
            }

            log.info("Author validation passed for: {}", name);
            return true;

        } catch (Exception e) {
            context.setErrorMessage("Validation error: " + e.getMessage());
            context.setValidationFailed(true);
            log.error("Validation error for author: {} - {}", context.getRequest().getName(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void compensate(CreateAuthorSagaContext context) {
        // No compensation needed for validation step
        // No state was created during validation
        log.debug("No compensation needed for ValidateAuthorStep");
    }

    @Override
    public String getStepName() {
        return "ValidateAuthorStep";
    }
}
