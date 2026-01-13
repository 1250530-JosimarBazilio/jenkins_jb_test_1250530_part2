/*
 * Copyright (c) 2022-2024 the original author or authors.
 *
 * MIT License
 */
package pt.psoft.g1.psoftg1.authormanagement.infrastructure.saga;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.services.CreateAuthorRequest;

/**
 * Context object for the Author creation saga.
 * Contains all data needed throughout the saga execution and compensation.
 * 
 * This context is passed between saga steps and holds:
 * - Input data (request, photoURI)
 * - Intermediate results (created entities)
 * - State for compensation (IDs of created entities to delete on rollback)
 */
@Getter
@Setter
@Builder
public class CreateAuthorSagaContext {

    // Input data
    private final CreateAuthorRequest request;
    private final String photoURI;

    // Created entity (for compensation)
    private Author createdAuthor;

    // Error tracking
    private String errorMessage;
    private boolean validationFailed;

    /**
     * Factory method to create a new saga context.
     */
    public static CreateAuthorSagaContext of(CreateAuthorRequest request, String photoURI) {
        return CreateAuthorSagaContext.builder()
                .request(request)
                .photoURI(photoURI)
                .validationFailed(false)
                .build();
    }

    /**
     * Checks if the Author was created.
     */
    public boolean isAuthorCreated() {
        return createdAuthor != null;
    }

    /**
     * Gets the final result of the saga.
     */
    public Author getResult() {
        return createdAuthor;
    }
}
