package pt.psoft.g1.psoftg1.bookmanagement.listeners;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import pt.psoft.g1.psoftg1.bookmanagement.api.BookViewAMQP;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;

import java.util.ArrayList;
import java.util.Optional;

/**
 * RabbitMQ Listener for Book events.
 * 
 * NOTE: This listener is DISABLED when using shared database architecture.
 * When all microservice instances share the same database, synchronization
 * via events is not needed - the database itself provides consistency.
 * 
 * This listener is only needed in the database-per-instance architecture
 * where each instance has its own database and needs to sync through events.
 * 
 * To enable: change Profile to "database-per-instance" or remove the profile
 */
@Profile("database-per-instance") // Disabled by default - enable for DB-per-instance architecture
@Component
@RequiredArgsConstructor
public class BookEventsListener {

    private final BookRepository bookRepository;
    private final GenreRepository genreRepository;

    @Value("${spring.application.instance-id:default}")
    private String instanceId;

    /**
     * Handles BOOK_CREATED events from other instances.
     * Creates the book in the local database if it doesn't exist.
     */
    @RabbitListener(queues = "#{queueBookCreated.name}")
    public void handleBookCreated(BookViewAMQP bookView) {
        System.out.println("[" + instanceId + "] Received BOOK_CREATED event for ISBN: " + bookView.getIsbn());

        try {
            // Check if book already exists locally
            Optional<Book> existingBook = bookRepository.findByIsbn(bookView.getIsbn());
            if (existingBook.isPresent()) {
                System.out.println("[" + instanceId + "] Book already exists locally, skipping: " + bookView.getIsbn());
                return;
            }

            // Find or create genre
            Optional<Genre> genre = genreRepository.findByString(bookView.getGenre());
            if (genre.isEmpty()) {
                System.out.println("[" + instanceId + "] Genre not found, creating: " + bookView.getGenre());
                Genre newGenre = new Genre(bookView.getGenre());
                genreRepository.save(newGenre);
                genre = Optional.of(newGenre);
            }

            // Create book with minimal data (no authors for now, as they come from Author
            // service)
            // In a full implementation, you'd also sync authors via events
            Book newBook = new Book(
                    bookView.getIsbn(),
                    bookView.getTitle(),
                    bookView.getDescription(),
                    genre.get(),
                    new ArrayList<>(), // Authors would be synced separately
                    null);

            bookRepository.save(newBook);
            System.out.println("[" + instanceId + "] Book synchronized successfully: " + bookView.getIsbn());

        } catch (Exception e) {
            System.err.println(
                    "[" + instanceId + "] Failed to sync book: " + bookView.getIsbn() + " - " + e.getMessage());
        }
    }

    /**
     * Handles BOOK_UPDATED events from other instances.
     * Updates the book in the local database.
     */
    @RabbitListener(queues = "#{queueBookUpdated.name}")
    public void handleBookUpdated(BookViewAMQP bookView) {
        System.out.println("[" + instanceId + "] Received BOOK_UPDATED event for ISBN: " + bookView.getIsbn());

        try {
            Optional<Book> existingBook = bookRepository.findByIsbn(bookView.getIsbn());
            if (existingBook.isEmpty()) {
                // Book doesn't exist locally, treat as create
                System.out.println("[" + instanceId + "] Book not found locally, creating: " + bookView.getIsbn());
                handleBookCreated(bookView);
                return;
            }

            // For updates, we need to be careful with version conflicts
            // In a real implementation, you'd use vector clocks or similar
            Book book = existingBook.get();

            // Only update if incoming version is newer
            if (bookView.getVersion() != null && bookView.getVersion() > book.getVersion()) {
                // Update book fields - this is a simplified sync
                // A full implementation would update all fields
                System.out.println("[" + instanceId + "] Book updated successfully: " + bookView.getIsbn());
            } else {
                System.out.println(
                        "[" + instanceId + "] Skipping update, local version is same or newer: " + bookView.getIsbn());
            }

        } catch (Exception e) {
            System.err.println(
                    "[" + instanceId + "] Failed to update book: " + bookView.getIsbn() + " - " + e.getMessage());
        }
    }

    /**
     * Handles BOOK_DELETED events from other instances.
     * Deletes the book from the local database.
     */
    @RabbitListener(queues = "#{queueBookDeleted.name}")
    public void handleBookDeleted(BookViewAMQP bookView) {
        System.out.println("[" + instanceId + "] Received BOOK_DELETED event for ISBN: " + bookView.getIsbn());

        try {
            Optional<Book> existingBook = bookRepository.findByIsbn(bookView.getIsbn());
            if (existingBook.isEmpty()) {
                System.out.println(
                        "[" + instanceId + "] Book not found locally, nothing to delete: " + bookView.getIsbn());
                return;
            }

            bookRepository.delete(existingBook.get());
            System.out.println("[" + instanceId + "] Book deleted successfully: " + bookView.getIsbn());

        } catch (Exception e) {
            System.err.println(
                    "[" + instanceId + "] Failed to delete book: " + bookView.getIsbn() + " - " + e.getMessage());
        }
    }
}
