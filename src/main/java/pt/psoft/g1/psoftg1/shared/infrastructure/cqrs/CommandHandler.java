package pt.psoft.g1.psoftg1.shared.infrastructure.cqrs;

/**
 * Handler interface for processing commands in the CQRS pattern.
 * Each command should have exactly one handler responsible for executing it.
 * 
 * Command handlers are responsible for:
 * - Validating the command
 * - Executing business logic
 * - Persisting changes
 * - Publishing domain events if necessary
 * 
 * @param <C> The type of command this handler can process
 * @param <R> The type of result returned after processing the command
 */
public interface CommandHandler<C extends Command<R>, R> {

    /**
     * Handles the given command and returns the result.
     * 
     * @param command The command to handle
     * @return The result of handling the command
     */
    R handle(C command);
}
