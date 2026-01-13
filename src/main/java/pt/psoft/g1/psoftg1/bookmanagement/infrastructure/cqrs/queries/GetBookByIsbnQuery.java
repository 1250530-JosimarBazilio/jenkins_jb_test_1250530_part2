package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Query;

/**
 * Query to retrieve a book by its ISBN.
 * 
 * This query follows the CQRS pattern where queries represent read operations.
 * Queries should never modify the state of the system.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetBookByIsbnQuery implements Query<Book> {

    private String isbn;
}
