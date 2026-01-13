package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Query;

import java.util.List;

/**
 * Query to find books by title.
 * 
 * This query retrieves all books matching or containing the specified title.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FindBooksByTitleQuery implements Query<List<Book>> {

    private String title;
}
