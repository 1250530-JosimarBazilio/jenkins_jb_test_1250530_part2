package pt.psoft.g1.psoftg1.shared.infrastructure.cqrs;

/**
 * Base marker interface for all commands in the CQRS pattern.
 * Commands represent intentions to change the state of the system.
 * They are named using imperative verbs (e.g., CreateBook, UpdateAuthor).
 * 
 * @param <R> The type of result returned by the command handler
 */
public interface Command<R> {
}
