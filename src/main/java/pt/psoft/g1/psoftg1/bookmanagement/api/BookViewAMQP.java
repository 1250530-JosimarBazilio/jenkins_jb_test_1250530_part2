package pt.psoft.g1.psoftg1.bookmanagement.api;

/**
 * DTO for Book data in AMQP messages.
 * Used for publishing book events to RabbitMQ.
 */
public class BookViewAMQP {
    
    private String isbn;
    private String title;
    private String description;
    private String genre;
    private Long version;
    
    public BookViewAMQP() {
    }
    
    public BookViewAMQP(String isbn, String title, String description, String genre, Long version) {
        this.isbn = isbn;
        this.title = title;
        this.description = description;
        this.genre = genre;
        this.version = version;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
