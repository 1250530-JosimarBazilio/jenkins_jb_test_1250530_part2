package pt.psoft.g1.psoftg1.shared.infrastructure.cqrs;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Central bus for dispatching queries to their appropriate handlers.
 * 
 * The QueryBus acts as a mediator between the caller and the query handlers.
 * It automatically discovers all QueryHandler beans in the application context
 * and routes queries to the appropriate handler based on the query type.
 * 
 * Benefits:
 * - Decouples callers from query handlers
 * - Single point of query dispatch
 * - Easy to add cross-cutting concerns (logging, caching)
 */
@Component
@RequiredArgsConstructor
public class QueryBus {

    private final ApplicationContext applicationContext;

    @SuppressWarnings("rawtypes")
    private final Map<Class<? extends Query>, QueryHandler> handlers = new HashMap<>();

    /**
     * Initialize the query bus by discovering all QueryHandler beans.
     */
    @PostConstruct
    @SuppressWarnings("rawtypes")
    public void init() {
        Map<String, QueryHandler> handlerBeans = applicationContext.getBeansOfType(QueryHandler.class);

        for (QueryHandler handler : handlerBeans.values()) {
            Class<?>[] generics = GenericTypeResolver.resolveTypeArguments(
                    handler.getClass(), QueryHandler.class);

            if (generics != null && generics.length > 0) {
                @SuppressWarnings("unchecked")
                Class<? extends Query> queryType = (Class<? extends Query>) generics[0];
                handlers.put(queryType, handler);
            }
        }
    }

    /**
     * Dispatches a query to its registered handler.
     * 
     * @param query The query to dispatch
     * @param <Q>   The type of the query
     * @param <R>   The type of result expected
     * @return The result from the query handler
     * @throws IllegalArgumentException if no handler is registered for the query
     *                                  type
     */
    @SuppressWarnings("unchecked")
    public <Q extends Query<R>, R> R dispatch(Q query) {
        QueryHandler<Q, R> handler = handlers.get(query.getClass());

        if (handler == null) {
            throw new IllegalArgumentException(
                    "No handler registered for query: " + query.getClass().getName());
        }

        return handler.handle(query);
    }

    /**
     * Checks if a handler is registered for the given query type.
     * 
     * @param queryType The query class to check
     * @return true if a handler is registered, false otherwise
     */
    public boolean hasHandler(Class<? extends Query<?>> queryType) {
        return handlers.containsKey(queryType);
    }
}
