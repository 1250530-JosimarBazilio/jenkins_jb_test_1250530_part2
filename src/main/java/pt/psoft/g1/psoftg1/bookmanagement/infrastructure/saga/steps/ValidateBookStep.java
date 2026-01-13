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
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga.CreateBookSagaContext;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.saga.SagaStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Step 1: Validate book data before creation.
 * 
 * Validates:
 * - ISBN uniqueness
 * - Genre existence
 * - At least one valid author
 * 
 * Compensation: None (no state created)
 */
@Component
@RequiredArgsConstructor
public class ValidateBookStep implements SagaStep<CreateBookSagaContext> {

    private static final Logger log = LoggerFactory.getLogger(ValidateBookStep.class);

    private final BookRepository bookRepository;
    private final GenreRepository genreRepository;
    private final AuthorRepository authorRepository;

    @Override
    public boolean execute(CreateBookSagaContext context) {
        log.debug("Validating book creation request for ISBN: {}", context.getIsbn());

        // Check if ISBN already exists
        if (bookRepository.findByIsbn(context.getIsbn()).isPresent()) {
            context.setValidationFailed(true);
            context.setErrorMessage("Book with ISBN " + context.getIsbn() + " already exists");
            log.warn("Validation failed: ISBN {} already exists", context.getIsbn());
            return false;
        }

        // Validate and resolve genre
        Optional<Genre> genreOpt = genreRepository.findByString(context.getRequest().getGenre());
        if (genreOpt.isEmpty()) {
            context.setValidationFailed(true);
            context.setErrorMessage("Genre not found: " + context.getRequest().getGenre());
            log.warn("Validation failed: Genre not found: {}", context.getRequest().getGenre());
            return false;
        }
        context.setResolvedGenre(genreOpt.get());

        // Validate and resolve authors
        List<Long> authorNumbers = context.getRequest().getAuthors();
        if (authorNumbers == null || authorNumbers.isEmpty()) {
            context.setValidationFailed(true);
            context.setErrorMessage("At least one author is required");
            log.warn("Validation failed: No authors provided");
            return false;
        }

        List<Author> authors = new ArrayList<>();
        for (Long authorNumber : authorNumbers) {
            Optional<Author> authorOpt = authorRepository.findByAuthorNumber(authorNumber);
            if (authorOpt.isPresent()) {
                authors.add(authorOpt.get());
            }
        }

        if (authors.isEmpty()) {
            context.setValidationFailed(true);
            context.setErrorMessage("No valid authors found for the provided author numbers");
            log.warn("Validation failed: No valid authors found");
            return false;
        }
        context.setResolvedAuthors(authors);

        log.info("Book validation passed for ISBN: {}", context.getIsbn());
        return true;
    }

    @Override
    public void compensate(CreateBookSagaContext context) {
        // No state created during validation, nothing to compensate
        log.debug("No compensation needed for ValidateBookStep");
    }

    @Override
    public String getStepName() {
        return "ValidateBookStep";
    }
}
