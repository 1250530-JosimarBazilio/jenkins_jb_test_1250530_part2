package pt.psoft.g1.psoftg1.shared.model;

/**
 * Book event types for RabbitMQ messaging.
 */
public final class BookEvents {
    
    public static final String BOOK_CREATED = "book.created";
    public static final String BOOK_UPDATED = "book.updated";
    public static final String BOOK_DELETED = "book.deleted";
    
    private BookEvents() {
        // Prevent instantiation
    }
}
