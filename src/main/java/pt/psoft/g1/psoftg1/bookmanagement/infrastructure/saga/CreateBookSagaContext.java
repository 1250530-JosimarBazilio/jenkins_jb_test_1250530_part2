/*
 * Copyright (c) 2022-2024 the original author or authors.
 *
 * MIT License
 */
package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.services.CreateBookRequest;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;

import java.util.List;

/**
 * Context object for the Book creation saga.
 * Contains all data needed throughout the saga execution and compensation.
 * 
 * This context is passed between saga steps and holds:
 * - Input data (request, isbn, photoURI)
 * - Intermediate results (created entities)
 * - State for compensation (IDs of created entities to delete on rollback)
 */
@Getter
@Setter
@Builder
public class CreateBookSagaContext {

    // Input data
    private final CreateBookRequest request;
    private final String isbn;
    private final String photoURI;

    // Resolved entities
    private Genre resolvedGenre;
    private List<Author> resolvedAuthors;

    // Created entity (for compensation)
    private Book createdBook;

    // Error tracking
    private String errorMessage;
    private boolean validationFailed;

    /**
     * Factory method to create a new saga context.
     */
    public static CreateBookSagaContext of(CreateBookRequest request, String isbn, String photoURI) {
        return CreateBookSagaContext.builder()
                .request(request)
                .isbn(isbn)
                .photoURI(photoURI)
                .validationFailed(false)
                .build();
    }

    /**
     * Checks if the Book was created.
     */
    public boolean isBookCreated() {
        return createdBook != null;
    }

    /**
     * Gets the final result of the saga.
     */
    public Book getResult() {
        return createdBook;
    }
}
