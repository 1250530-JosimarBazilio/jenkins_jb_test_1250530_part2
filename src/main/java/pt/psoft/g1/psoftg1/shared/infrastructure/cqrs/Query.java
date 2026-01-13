package pt.psoft.g1.psoftg1.shared.infrastructure.cqrs;

/**
 * Base marker interface for all queries in the CQRS pattern.
 * Queries represent requests to read data from the system without modifying it.
 * They are named using nouns or interrogative phrases (e.g., GetBookByIsbn,
 * FindAuthorsByName).
 * 
 * @param <R> The type of result returned by the query handler
 */
public interface Query<R> {
}
