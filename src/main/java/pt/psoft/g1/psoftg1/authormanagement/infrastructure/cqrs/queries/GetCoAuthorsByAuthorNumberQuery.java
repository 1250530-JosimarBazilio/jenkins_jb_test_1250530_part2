package pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Query;

import java.util.List;

/**
 * Query to find co-authors of a specific author.
 * 
 * This query follows the CQRS pattern where queries represent read operations.
 * Queries should never modify the state of the system.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetCoAuthorsByAuthorNumberQuery implements Query<List<Author>> {

    private Long authorNumber;
}
