package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Query;

import java.util.List;

/**
 * Query to find books by genre.
 * 
 * This query retrieves all books that belong to the specified genre.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FindBooksByGenreQuery implements Query<List<Book>> {

    private String genre;
}
