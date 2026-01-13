package pt.psoft.g1.psoftg1.bookmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.publishers.BookEventsPublisher;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookService;
import pt.psoft.g1.psoftg1.bookmanagement.services.CreateBookRequest;
import pt.psoft.g1.psoftg1.bookmanagement.services.UpdateBookRequest;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BLUE Version - REST Controller for Book Management Microservice.
 * Handles CRUD operations for books.
 * 
 * Labels for Release Management:
 * - slot: blue
 * - version: ${BLUE_VERSION}
 * - release-strategy: blue-green
 * - rollback-enabled: true
 * 
 * This controller is activated when deployment.slot=blue or not specified
 * (default)
 */
@Tag(name = "Books (BLUE)", description = "BLUE Deployment - Endpoints for managing Books")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/books")
@ConditionalOnProperty(name = "deployment.slot", havingValue = "blue", matchIfMissing = true)
public class BookController {

    private final BookService bookService;
    private final BookViewMapper bookViewMapper;
    private final BookEventsPublisher bookEventsPublisher;

    @Operation(summary = "Register a new Book with JSON")
    @PutMapping(value = "/{isbn}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<BookView> createFromJson(@Valid @RequestBody CreateBookRequest resource,
            @PathVariable("isbn") String isbn) {
        return doCreate(resource, isbn);
    }

    @Operation(summary = "Register a new Book with form data")
    @PutMapping(value = "/{isbn}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<BookView> createFromForm(@Valid CreateBookRequest resource,
            @PathVariable("isbn") String isbn) {
        return doCreate(resource, isbn);
    }

    private ResponseEntity<BookView> doCreate(CreateBookRequest resource, String isbn) {
        // Guarantee that the client doesn't provide a link on the body
        resource.setPhotoURI(null);

        Book book;
        try {
            book = bookService.create(resource, isbn);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // Publish event to RabbitMQ
        try {
            BookViewAMQP bookViewAMQP = bookViewMapper.toBookViewAMQP(book);
            bookEventsPublisher.sendBookCreated(bookViewAMQP);
        } catch (Exception e) {
            // Log but don't fail the request if event publishing fails
            System.err.println("Failed to publish book created event: " + e.getMessage());
        }

        final var newBookUri = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .pathSegment(book.getIsbn())
                .build().toUri();

        return ResponseEntity.created(newBookUri)
                .eTag(Long.toString(book.getVersion()))
                .body(bookViewMapper.toBookView(book));
    }

    @Operation(summary = "Gets a specific Book by ISBN")
    @GetMapping(value = "/{isbn}")
    public ResponseEntity<BookView> findByIsbn(@PathVariable final String isbn) {
        System.out.println("Received request to find book with ISBN: " + isbn);
        final var book = bookService.findByIsbn(isbn);
        BookView bookView = bookViewMapper.toBookView(book);

        return ResponseEntity.ok()
                .eTag(Long.toString(book.getVersion()))
                .body(bookView);
    }

    @Operation(summary = "Updates a specific Book with JSON")
    @PatchMapping(value = "/{isbn}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookView> updateBookFromJson(@PathVariable final String isbn,
            final WebRequest request,
            @Valid @RequestBody final UpdateBookRequest resource) {
        return doUpdateBook(isbn, request, resource);
    }

    @Operation(summary = "Updates a specific Book with form data")
    @PatchMapping(value = "/{isbn}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookView> updateBookFromForm(@PathVariable final String isbn,
            final WebRequest request,
            @Valid final UpdateBookRequest resource) {
        return doUpdateBook(isbn, request, resource);
    }

    private ResponseEntity<BookView> doUpdateBook(String isbn, WebRequest request, UpdateBookRequest resource) {
        final String ifMatchValue = request.getHeader("If-Match");
        if (ifMatchValue == null || ifMatchValue.isEmpty() || ifMatchValue.equals("null")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You must issue a conditional PATCH using 'if-match'");
        }

        // Clean the ETag value (remove quotes if present)
        String version = ifMatchValue.replace("\"", "");

        MultipartFile file = resource.getPhoto();
        if (file != null && !file.isEmpty()) {
            // For now, we don't handle file uploads in the microservice
            resource.setPhoto(null);
        }

        Book book;
        resource.setIsbn(isbn);
        try {
            book = bookService.update(resource, version);
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

    @Operation(summary = "Gets Books by title, genre, or author name")
    @GetMapping
    public ResponseEntity<List<BookView>> findBooks(
            @RequestParam(value = "title", required = false) final String title,
            @RequestParam(value = "genre", required = false) final String genre,
            @RequestParam(value = "authorName", required = false) final String authorName) {

        // This method does an 'OR' join of the criteria
        List<Book> booksByTitle = null;
        if (title != null) {
            booksByTitle = bookService.findByTitle(title);
        }

        List<Book> booksByGenre = null;
        if (genre != null) {
            booksByGenre = bookService.findByGenre(genre);
        }

        List<Book> booksByAuthorName = null;
        if (authorName != null) {
            booksByAuthorName = bookService.findByAuthorName(authorName);
        }

        Set<Book> bookSet = new HashSet<>();
        if (booksByTitle != null) {
            bookSet.addAll(booksByTitle);
        }
        if (booksByGenre != null) {
            bookSet.addAll(booksByGenre);
        }
        if (booksByAuthorName != null) {
            bookSet.addAll(booksByAuthorName);
        }

        List<Book> books = bookSet.stream()
                .sorted(Comparator.comparing(b -> b.getTitle().toString()))
                .collect(Collectors.toList());

        if (books.isEmpty()) {
            throw new NotFoundException("No books found with the provided criteria");
        }

        return ResponseEntity.ok(bookViewMapper.toBookView(books));
    }

    @Operation(summary = "Health check endpoint")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Books Microservice is running");
    }
}
