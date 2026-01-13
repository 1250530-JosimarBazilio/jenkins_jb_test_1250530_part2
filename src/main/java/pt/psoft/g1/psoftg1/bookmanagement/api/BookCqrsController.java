package pt.psoft.g1.psoftg1.bookmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.commands.CreateBookCommand;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.commands.UpdateBookCommand;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries.GetBookByIsbnQuery;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries.SearchBooksQuery;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.publishers.BookEventsPublisher;
import pt.psoft.g1.psoftg1.bookmanagement.services.CreateBookRequest;
import pt.psoft.g1.psoftg1.bookmanagement.services.UpdateBookRequest;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.CommandBus;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryBus;

import java.util.List;

/**
 * REST Controller for Book Management using CQRS pattern.
 * 
 * This controller uses the CommandBus and QueryBus to dispatch
 * commands and queries to their respective handlers, following
 * the CQRS (Command Query Responsibility Segregation) pattern.
 * 
 * Benefits:
 * - Clear separation between read and write operations
 * - Easier to scale read and write sides independently
 * - Better support for complex domain logic
 * - Simplified testing of individual handlers
 */
@Tag(name = "Books CQRS", description = "Endpoints for managing Books using CQRS pattern")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/books")
public class BookCqrsController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final BookViewMapper bookViewMapper;
    private final BookEventsPublisher bookEventsPublisher;

    /**
     * Creates a new Book using CQRS Command pattern.
     */
    @Operation(summary = "Register a new Book (CQRS)")
    @PutMapping(value = "/{isbn}")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<BookView> create(CreateBookRequest resource, @PathVariable("isbn") String isbn) {
        // Guarantee that the client doesn't provide a link on the body
        resource.setPhotoURI(null);

        // Create the command
        CreateBookCommand command = new CreateBookCommand(
                isbn,
                resource.getTitle(),
                resource.getGenre(),
                resource.getDescription(),
                resource.getAuthors(),
                resource.getPhoto(),
                null);

        Book book;
        try {
            // Dispatch command to handler
            book = commandBus.dispatch(command);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // Publish event to RabbitMQ
        try {
            BookViewAMQP bookViewAMQP = bookViewMapper.toBookViewAMQP(book);
            bookEventsPublisher.sendBookCreated(bookViewAMQP);
        } catch (Exception e) {
            System.err.println("Failed to publish book created event: " + e.getMessage());
        }

        final var newBookUri = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .pathSegment(book.getIsbn())
                .build().toUri();

        return ResponseEntity.created(newBookUri)
                .eTag(Long.toString(book.getVersion()))
                .body(bookViewMapper.toBookView(book));
    }

    /**
     * Gets a Book by ISBN using CQRS Query pattern.
     */
    @Operation(summary = "Gets a specific Book by ISBN (CQRS)")
    @GetMapping(value = "/{isbn}")
    public ResponseEntity<BookView> findByIsbn(@PathVariable final String isbn) {
        // Create and dispatch the query
        GetBookByIsbnQuery query = new GetBookByIsbnQuery(isbn);
        Book book = queryBus.dispatch(query);

        BookView bookView = bookViewMapper.toBookView(book);

        return ResponseEntity.ok()
                .eTag(Long.toString(book.getVersion()))
                .body(bookView);
    }

    /**
     * Updates a Book using CQRS Command pattern.
     */
    @Operation(summary = "Updates a specific Book (CQRS)")
    @PatchMapping(value = "/{isbn}")
    public ResponseEntity<BookView> updateBook(
            @PathVariable final String isbn,
            final WebRequest request,
            @Valid final UpdateBookRequest resource) {

        final String ifMatchValue = request.getHeader("If-Match");
        if (ifMatchValue == null || ifMatchValue.isEmpty() || ifMatchValue.equals("null")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You must issue a conditional PATCH using 'if-match'");
        }

        String version = ifMatchValue.replace("\"", "");

        MultipartFile file = resource.getPhoto();
        if (file != null && !file.isEmpty()) {
            resource.setPhoto(null);
        }

        // Create the command
        UpdateBookCommand command = new UpdateBookCommand(
                isbn,
                resource.getTitle(),
                resource.getGenre(),
                resource.getDescription(),
                resource.getAuthors(),
                resource.getPhoto(),
                resource.getPhotoURI(),
                version);

        Book book;
        try {
            // Dispatch command to handler
            book = commandBus.dispatch(command);
        } catch (Exception e) {
            throw new ConflictException("Could not update book: " + e.getMessage());
        }

        // Publish event to RabbitMQ
        try {
            BookViewAMQP bookViewAMQP = bookViewMapper.toBookViewAMQP(book);
            bookEventsPublisher.sendBookUpdated(bookViewAMQP);
        } catch (Exception e) {
            System.err.println("Failed to publish book updated event: " + e.getMessage());
        }

        return ResponseEntity.ok()
                .eTag(Long.toString(book.getVersion()))
                .body(bookViewMapper.toBookView(book));
    }

    /**
     * Searches Books using CQRS Query pattern.
     */
    @Operation(summary = "Gets Books by title, genre, or author name (CQRS)")
    @GetMapping
    public ResponseEntity<List<BookView>> findBooks(
            @RequestParam(value = "title", required = false) final String title,
            @RequestParam(value = "genre", required = false) final String genre,
            @RequestParam(value = "authorName", required = false) final String authorName) {

        // Create and dispatch the query
        SearchBooksQuery query = new SearchBooksQuery(title, genre, authorName);
        List<Book> books = queryBus.dispatch(query);

        if (books.isEmpty()) {
            throw new NotFoundException("No books found with the provided criteria");
        }

        return ResponseEntity.ok(bookViewMapper.toBookView(books));
    }

    /**
     * Health check endpoint.
     */
    @Operation(summary = "Health check endpoint")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Books Microservice CQRS endpoint is running");
    }
}
