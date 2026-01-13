package pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import pt.psoft.g1.psoftg1.authormanagement.api.AuthorLendingView;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Query;

import java.util.List;

/**
 * Query to get top authors by number of lendings.
 * 
 * This query follows the CQRS pattern where queries represent read operations.
 * Queries should never modify the state of the system.
 */
@Data
@AllArgsConstructor
public class GetTopAuthorsByLendingsQuery implements Query<List<AuthorLendingView>> {

    private int limit;

    public GetTopAuthorsByLendingsQuery() {
        this.limit = 5; // default top 5
    }
}
