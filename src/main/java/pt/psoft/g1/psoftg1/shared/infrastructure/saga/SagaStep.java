/*
 * Copyright (c) 2022-2024 the original author or authors.
 *
 * MIT License
 */
package pt.psoft.g1.psoftg1.shared.infrastructure.saga;

/**
 * Represents a single step in a Saga.
 * Each step has an execute method and a compensate method for rollback.
 *
 * @param <T> The type of context passed between saga steps
 */
public interface SagaStep<T> {

    /**
     * Executes this saga step.
     * 
     * @param context The saga context containing shared data
     * @return true if the step executed successfully, false otherwise
     */
    boolean execute(T context);

    /**
     * Compensates (rolls back) this saga step.
     * Called when a subsequent step fails and we need to undo this step.
     * 
     * @param context The saga context containing shared data
     */
    void compensate(T context);

    /**
     * Gets the name of this saga step for logging and debugging.
     * 
     * @return The step name
     */
    String getStepName();
}
