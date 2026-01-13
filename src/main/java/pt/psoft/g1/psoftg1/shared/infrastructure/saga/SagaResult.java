/*
 * Copyright (c) 2022-2024 the original author or authors.
 *
 * MIT License
 */
package pt.psoft.g1.psoftg1.shared.infrastructure.saga;

import lombok.Getter;

/**
 * Represents the result of a saga execution.
 * Contains information about success/failure, the final context, and error
 * details.
 *
 * @param <T> The type of context used in the saga
 */
@Getter
public class SagaResult<T> {

    private final boolean success;
    private final T context;
    private final String failedStep;
    private final String errorMessage;

    private SagaResult(boolean success, T context, String failedStep, String errorMessage) {
        this.success = success;
        this.context = context;
        this.failedStep = failedStep;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful saga result.
     * 
     * @param context The final saga context
     * @param <T>     The context type
     * @return A successful result
     */
    public static <T> SagaResult<T> success(T context) {
        return new SagaResult<>(true, context, null, null);
    }

    /**
     * Creates a failed saga result.
     * 
     * @param context      The saga context at the point of failure
     * @param failedStep   The name of the step that failed
     * @param errorMessage The error message
     * @param <T>          The context type
     * @return A failed result
     */
    public static <T> SagaResult<T> failure(T context, String failedStep, String errorMessage) {
        return new SagaResult<>(false, context, failedStep, errorMessage);
    }

    /**
     * Checks if the saga was rolled back (i.e., failed and compensated).
     * 
     * @return true if the saga was rolled back
     */
    public boolean wasRolledBack() {
        return !success;
    }
}
