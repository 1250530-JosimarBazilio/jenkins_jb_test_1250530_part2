package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Query;

import java.util.List;

/**
 * Query to search books by multiple criteria.
 * 
 * This query allows searching books by title, genre, and/or author name.
 * At least one criterion should be provided.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchBooksQuery implements Query<List<Book>> {

    private String title;
    private String genre;
    private String authorName;

    /**
     * Checks if at least one search criterion is provided.
     */
    public boolean hasAnyCriteria() {
        return title != null || genre != null || authorName != null;
    }
}
