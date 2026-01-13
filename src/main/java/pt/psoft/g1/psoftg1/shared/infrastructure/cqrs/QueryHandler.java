package pt.psoft.g1.psoftg1.shared.infrastructure.cqrs;

/**
 * Handler interface for processing queries in the CQRS pattern.
 * Each query should have exactly one handler responsible for executing it.
 * 
 * Query handlers are responsible for:
 * - Retrieving data from the read model
 * - Transforming data into the appropriate response format
 * 
 * Query handlers should NEVER modify the state of the system.
 * 
 * @param <Q> The type of query this handler can process
 * @param <R> The type of result returned after processing the query
 */
public interface QueryHandler<Q extends Query<R>, R> {

    /**
     * Handles the given query and returns the result.
     * 
     * @param query The query to handle
     * @return The result of handling the query
     */
    R handle(Q query);
}
