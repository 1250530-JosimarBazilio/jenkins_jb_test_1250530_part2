package pt.psoft.g1.psoftg1.bookmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
 * GREEN Version - REST Controller for Book Management Microservice.
 * This controller is activated when deployment.slot=green
 * 
 * Labels for Release Management:
 * - slot: green
 * - version: ${GREEN_VERSION}
 * - release-strategy: blue-green
 * - rollback-enabled: true
 * 
 * Features:
 * - SUCCESS response includes "GREEN" message for identification
 * - Error simulation mode for rollback testing
 */
@Tag(name = "Books (GREEN)", description = "GREEN Deployment - Endpoints for managing Books")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/books")
@ConditionalOnProperty(name = "deployment.slot", havingValue = "green")
public class BookControllerGreen {

    private final BookService bookService;
    private final BookViewMapper bookViewMapper;
    private final BookEventsPublisher bookEventsPublisher;

    /**
     * Feature flag to simulate errors for rollback testing
     * Set to true to test automatic rollback mechanism
     */
    @Value("${deployment.green.simulate-error:false}")
    private boolean simulateError;

    /**
     * GREEN version identifier
     */
    @Value("${deployment.green.version:unknown}")
    private String greenVersion;

    /**
     * GREEN VERSION - Register a new Book with JSON
     * 
     * Release Labels:
     * - deployment.slot: green
     * - deployment.version: ${GREEN_VERSION}
     * - deployment.feature: createFromJson
     * - rollback.trigger: exception | health-check-failure
     */
    // @Operation(summary = "[GREEN] Register a new Book with JSON")
    // @PutMapping(value = "/{isbn}", consumes = MediaType.APPLICATION_JSON_VALUE)
    // @ResponseStatus(HttpStatus.CREATED)
    // public ResponseEntity<BookViewGreen> createFromJson(@Valid @RequestBody CreateBookRequest resource,
    //         @PathVariable("isbn") String isbn) {

    //     // ============================================
    //     // ERROR SIMULATION MODE - For Rollback Testing
    //     // ============================================
    //     if (simulateError) {
    //         throw new ResponseStatusException(
    //                 HttpStatus.INTERNAL_SERVER_ERROR,
    //                 "[GREEN ERROR] Simulated error for automatic rollback testing. " +
    //                         "Version: " + greenVersion + ". " +
    //                         "Set deployment.green.simulate-error=false to disable.");
    //     }

    //     return doCreate(resource, isbn);
    // }

    @Operation(summary = "[GREEN] Register a new Book with form data")
    @PutMapping(value = "/{isbn}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<BookViewGreen> createFromForm(@Valid CreateBookRequest resource,
            @PathVariable("isbn") String isbn) {

        if (simulateError) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "[GREEN ERROR] Simulated error for automatic rollback testing.");
        }

        return doCreate(resource, isbn);
    }

    private ResponseEntity<BookViewGreen> doCreate(CreateBookRequest resource, String isbn) {
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
            System.err.println("[GREEN] Failed to publish book created event: " + e.getMessage());
        }

        final var newBookUri = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .pathSegment(book.getIsbn())
                .build().toUri();

        // Create GREEN-specific response with success message
        BookViewGreen greenResponse = new BookViewGreen(
                bookViewMapper.toBookView(book),
                "GREEN",
                greenVersion,
                "Book created successfully in GREEN deployment");

        return ResponseEntity.created(newBookUri)
                .eTag(Long.toString(book.getVersion()))
                .header("X-Deployment-Slot", "green")
                .header("X-Deployment-Version", greenVersion)
                .header("X-Rollback-Enabled", "true")
                .body(greenResponse);
    }

    @Operation(summary = "[GREEN] Gets a specific Book by ISBN")
    @GetMapping(value = "/{isbn}")
    public ResponseEntity<BookViewGreen> findByIsbn(@PathVariable final String isbn) {
        System.out.println("[GREEN] Received request to find book with ISBN: " + isbn);

        if (simulateError) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "[GREEN ERROR] Simulated error for automatic rollback testing.");
        }

        final var book = bookService.findByIsbn(isbn);
        BookView bookView = bookViewMapper.toBookView(book);

        BookViewGreen greenResponse = new BookViewGreen(
                bookView,
                "GREEN",
                greenVersion,
                "Book retrieved successfully from GREEN deployment");

        return ResponseEntity.ok()
                .eTag(Long.toString(book.getVersion()))
                .header("X-Deployment-Slot", "green")
                .header("X-Deployment-Version", greenVersion)
                .body(greenResponse);
    }

    @Operation(summary = "[GREEN] Updates a specific Book with JSON")
    @PatchMapping(value = "/{isbn}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookViewGreen> updateBookFromJson(@PathVariable final String isbn,
            final WebRequest request,
            @Valid @RequestBody final UpdateBookRequest resource) {
        return doUpdateBook(isbn, request, resource);
    }

    @Operation(summary = "[GREEN] Updates a specific Book with form data")
    @PatchMapping(value = "/{isbn}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookViewGreen> updateBookFromForm(@PathVariable final String isbn,
            final WebRequest request,
            @Valid final UpdateBookRequest resource) {
        return doUpdateBook(isbn, request, resource);
    }

    private ResponseEntity<BookViewGreen> doUpdateBook(String isbn, WebRequest request, UpdateBookRequest resource) {
        if (simulateError) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "[GREEN ERROR] Simulated error for automatic rollback testing.");
        }

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
            System.err.println("[GREEN] Failed to publish book updated event: " + e.getMessage());
        }

        BookViewGreen greenResponse = new BookViewGreen(
                bookViewMapper.toBookView(book),
                "GREEN",
                greenVersion,
                "Book updated successfully in GREEN deployment");

        return ResponseEntity.ok()
                .eTag(Long.toString(book.getVersion()))
                .header("X-Deployment-Slot", "green")
                .header("X-Deployment-Version", greenVersion)
                .body(greenResponse);
    }

    @Operation(summary = "[GREEN] Gets Books by title, genre, or author name")
    @GetMapping
    public ResponseEntity<List<BookViewGreen>> findBooks(
            @RequestParam(value = "title", required = false) final String title,
            @RequestParam(value = "genre", required = false) final String genre,
            @RequestParam(value = "authorName", required = false) final String authorName) {

        if (simulateError) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "[GREEN ERROR] Simulated error for automatic rollback testing.");
        }

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

        List<BookViewGreen> greenResponses = bookViewMapper.toBookView(books).stream()
                .map(bv -> new BookViewGreen(bv, "GREEN", greenVersion, "Book retrieved from GREEN deployment"))
                .collect(Collectors.toList());

        return ResponseEntity.ok()
                .header("X-Deployment-Slot", "green")
                .header("X-Deployment-Version", greenVersion)
                .body(greenResponses);
    }

    @Operation(summary = "[GREEN] Health check endpoint")
    @GetMapping("/health")
    public ResponseEntity<HealthCheckResponse> health() {
        if (simulateError) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Deployment-Slot", "green")
                    .header("X-Rollback-Trigger", "health-check-failure")
                    .body(new HealthCheckResponse(
                            "GREEN",
                            greenVersion,
                            "UNHEALTHY",
                            "Simulated error mode is active - Rollback should be triggered",
                            true));
        }

        return ResponseEntity.ok()
                .header("X-Deployment-Slot", "green")
                .header("X-Deployment-Version", greenVersion)
                .body(new HealthCheckResponse(
                        "GREEN",
                        greenVersion,
                        "HEALTHY",
                        "Books Microservice GREEN is running successfully",
                        false));
    }

    @Operation(summary = "[GREEN] Deployment info endpoint")
    @GetMapping("/deployment-info")
    public ResponseEntity<DeploymentInfo> getDeploymentInfo() {
        return ResponseEntity.ok()
                .header("X-Deployment-Slot", "green")
                .header("X-Deployment-Version", greenVersion)
                .body(new DeploymentInfo(
                        "green",
                        greenVersion,
                        simulateError,
                        "blue-green",
                        true,
                        System.currentTimeMillis()));
    }

    // Inner classes for GREEN-specific responses

    public record BookViewGreen(
            BookView book,
            String deploymentSlot,
            String deploymentVersion,
            String message) {
    }

    public record HealthCheckResponse(
            String deploymentSlot,
            String version,
            String status,
            String message,
            boolean rollbackRequired) {
    }

    public record DeploymentInfo(
            String slot,
            String version,
            boolean simulateErrorMode,
            String releaseStrategy,
            boolean rollbackEnabled,
            long timestamp) {
    }
}
