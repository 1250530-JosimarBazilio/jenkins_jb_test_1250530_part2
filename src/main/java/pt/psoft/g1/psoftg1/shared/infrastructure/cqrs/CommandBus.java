package pt.psoft.g1.psoftg1.shared.infrastructure.cqrs;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Central bus for dispatching commands to their appropriate handlers.
 * 
 * The CommandBus acts as a mediator between the caller and the command
 * handlers.
 * It automatically discovers all CommandHandler beans in the application
 * context
 * and routes commands to the appropriate handler based on the command type.
 * 
 * Benefits:
 * - Decouples callers from command handlers
 * - Single point of command dispatch
 * - Easy to add cross-cutting concerns (logging, validation, transactions)
 */
@Component
@RequiredArgsConstructor
public class CommandBus {

    private final ApplicationContext applicationContext;

    @SuppressWarnings("rawtypes")
    private final Map<Class<? extends Command>, CommandHandler> handlers = new HashMap<>();

    /**
     * Initialize the command bus by discovering all CommandHandler beans.
     */
    @PostConstruct
    @SuppressWarnings("rawtypes")
    public void init() {
        Map<String, CommandHandler> handlerBeans = applicationContext.getBeansOfType(CommandHandler.class);

        for (CommandHandler handler : handlerBeans.values()) {
            Class<?>[] generics = GenericTypeResolver.resolveTypeArguments(
                    handler.getClass(), CommandHandler.class);

            if (generics != null && generics.length > 0) {
                @SuppressWarnings("unchecked")
                Class<? extends Command> commandType = (Class<? extends Command>) generics[0];
                handlers.put(commandType, handler);
            }
        }
    }

    /**
     * Dispatches a command to its registered handler.
     * 
     * @param command The command to dispatch
     * @param <C>     The type of the command
     * @param <R>     The type of result expected
     * @return The result from the command handler
     * @throws IllegalArgumentException if no handler is registered for the command
     *                                  type
     */
    @SuppressWarnings("unchecked")
    public <C extends Command<R>, R> R dispatch(C command) {
        CommandHandler<C, R> handler = handlers.get(command.getClass());

        if (handler == null) {
            throw new IllegalArgumentException(
                    "No handler registered for command: " + command.getClass().getName());
        }

        return handler.handle(command);
    }

    /**
     * Checks if a handler is registered for the given command type.
     * 
     * @param commandType The command class to check
     * @return true if a handler is registered, false otherwise
     */
    public boolean hasHandler(Class<? extends Command<?>> commandType) {
        return handlers.containsKey(commandType);
    }
}
