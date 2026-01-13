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
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga.CreateBookSagaContext;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.saga.SagaStep;

/**
 * Step 2: Create the Book entity.
 * 
 * Creates the Book entity in the database using validated data from the
 * context.
 * 
 * Compensation: Delete the created Book from the database.
 */
@Component
@RequiredArgsConstructor
public class CreateBookStep implements SagaStep<CreateBookSagaContext> {

    private static final Logger log = LoggerFactory.getLogger(CreateBookStep.class);

    private final BookRepository bookRepository;

    @Override
    public boolean execute(CreateBookSagaContext context) {
        log.debug("Creating book with ISBN: {}", context.getIsbn());

        try {
            Book newBook = new Book(
                    context.getIsbn(),
                    context.getRequest().getTitle(),
                    context.getRequest().getDescription(),
                    context.getResolvedGenre(),
                    context.getResolvedAuthors(),
                    context.getPhotoURI());

            Book savedBook = bookRepository.save(newBook);
            context.setCreatedBook(savedBook);

            log.info("Book created successfully with ISBN: {}", savedBook.getIsbn());
            return true;

        } catch (Exception e) {
            context.setErrorMessage("Failed to create book: " + e.getMessage());
            log.error("Failed to create book with ISBN: {} - {}", context.getIsbn(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void compensate(CreateBookSagaContext context) {
        if (context.getCreatedBook() != null) {
            log.info("Compensating: Deleting book with ISBN: {}", context.getCreatedBook().getIsbn());
            try {
                bookRepository.delete(context.getCreatedBook());
                log.info("Book deleted successfully during compensation: {}", context.getCreatedBook().getIsbn());
            } catch (Exception e) {
                log.error("Failed to delete book during compensation: {} - {}",
                        context.getCreatedBook().getIsbn(), e.getMessage(), e);
            }
        }
    }

    @Override
    public String getStepName() {
        return "CreateBookStep";
    }
}
