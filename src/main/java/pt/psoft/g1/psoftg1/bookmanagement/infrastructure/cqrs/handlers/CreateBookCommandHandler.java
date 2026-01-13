package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.commands.CreateBookCommand;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga.CreateBookSagaOrchestrator;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.services.CreateBookRequest;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.CommandHandler;

/**
 * Command handler for creating a new book.
 * 
 * This handler processes CreateBookCommand by delegating to the Saga
 * orchestrator,
 * which ensures atomic operations with compensating transactions.
 * 
 * Responsibilities:
 * - Transform command into the appropriate request format
 * - Delegate to saga orchestrator for atomic execution
 * - Return the created book entity
 */
@Component
@RequiredArgsConstructor
public class CreateBookCommandHandler implements CommandHandler<CreateBookCommand, Book> {

    private static final Logger log = LoggerFactory.getLogger(CreateBookCommandHandler.class);

    private final CreateBookSagaOrchestrator createBookSagaOrchestrator;

    @Override
    public Book handle(CreateBookCommand command) {
        log.info("Handling CreateBookCommand for ISBN: {}", command.getIsbn());

        // Transform command into CreateBookRequest
        CreateBookRequest request = new CreateBookRequest();
        request.setDescription(command.getDescription());

        // Use reflection or setter if available for @NotBlank fields
        setTitle(request, command.getTitle());
        setGenre(request, command.getGenre());
        setAuthors(request, command.getAuthorNumbers());

        // Handle photo
        MultipartFile photo = command.getPhoto();
        String photoURI = command.getPhotoURI();
        if (photo == null && photoURI != null || photo != null && photoURI == null) {
            request.setPhoto(null);
            request.setPhotoURI(null);
        } else {
            request.setPhoto(photo);
            request.setPhotoURI(photoURI);
        }

        // Delegate to Saga Orchestrator for atomic operation with compensation
        return createBookSagaOrchestrator.execute(request, command.getIsbn(), photoURI);
    }

    private void setTitle(CreateBookRequest request, String title) {
        try {
            var field = CreateBookRequest.class.getDeclaredField("title");
            field.setAccessible(true);
            field.set(request, title);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set title", e);
        }
    }

    private void setGenre(CreateBookRequest request, String genre) {
        try {
            var field = CreateBookRequest.class.getDeclaredField("genre");
            field.setAccessible(true);
            field.set(request, genre);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set genre", e);
        }
    }

    private void setAuthors(CreateBookRequest request, java.util.List<Long> authors) {
        try {
            var field = CreateBookRequest.class.getDeclaredField("authors");
            field.setAccessible(true);
            field.set(request, authors);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set authors", e);
        }
    }
}
