package pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Query;

import java.util.Optional;

/**
 * Query to retrieve an author by their author number.
 * 
 * This query follows the CQRS pattern where queries represent read operations.
 * Queries should never modify the state of the system.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetAuthorByNumberQuery implements Query<Optional<Author>> {

    private Long authorNumber;
}
