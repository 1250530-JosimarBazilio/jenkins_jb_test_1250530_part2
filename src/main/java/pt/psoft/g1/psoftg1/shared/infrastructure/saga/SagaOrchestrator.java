/*
 * Copyright (c) 2022-2024 the original author or authors.
 *
 * MIT License
 */
package pt.psoft.g1.psoftg1.shared.infrastructure.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic Saga Orchestrator that manages the execution and compensation of saga
 * steps.
 * Implements the Saga pattern for distributed transactions with compensating
 * actions.
 *
 * The orchestrator:
 * 1. Executes steps in order
 * 2. If any step fails, executes compensating transactions in reverse order
 * 3. Provides logging and error handling
 *
 * @param <T> The type of context passed between saga steps
 */
public class SagaOrchestrator<T> {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final List<SagaStep<T>> steps = new ArrayList<>();
    private final String sagaName;

    public SagaOrchestrator(String sagaName) {
        this.sagaName = sagaName;
    }

    /**
     * Adds a step to the saga.
     * Steps are executed in the order they are added.
     * 
     * @param step The step to add
     * @return this orchestrator for fluent API
     */
    public SagaOrchestrator<T> addStep(SagaStep<T> step) {
        steps.add(step);
        return this;
    }

    /**
     * Executes the saga with the given context.
     * 
     * @param context The context containing data for the saga
     * @return SagaResult containing the outcome and any error information
     */
    public SagaResult<T> execute(T context) {
        log.info("Starting saga: {}", sagaName);

        List<SagaStep<T>> completedSteps = new ArrayList<>();

        for (SagaStep<T> step : steps) {
            log.info("Executing saga step: {} in saga: {}", step.getStepName(), sagaName);

            try {
                boolean success = step.execute(context);

                if (!success) {
                    log.error("Saga step failed: {} in saga: {}", step.getStepName(), sagaName);
                    compensate(completedSteps, context);
                    return SagaResult.failure(context, step.getStepName(),
                            "Step execution returned false");
                }

                completedSteps.add(step);
                log.info("Saga step completed: {} in saga: {}", step.getStepName(), sagaName);

            } catch (Exception e) {
                log.error("Saga step threw exception: {} in saga: {} - {}",
                        step.getStepName(), sagaName, e.getMessage(), e);
                compensate(completedSteps, context);
                return SagaResult.failure(context, step.getStepName(), e.getMessage());
            }
        }

        log.info("Saga completed successfully: {}", sagaName);
        return SagaResult.success(context);
    }

    /**
     * Executes compensating transactions for all completed steps in reverse order.
     * 
     * @param completedSteps Steps that were successfully executed
     * @param context        The saga context
     */
    private void compensate(List<SagaStep<T>> completedSteps, T context) {
        log.info("Starting compensation for saga: {} with {} steps to compensate",
                sagaName, completedSteps.size());

        // Compensate in reverse order
        for (int i = completedSteps.size() - 1; i >= 0; i--) {
            SagaStep<T> step = completedSteps.get(i);
            log.info("Compensating step: {} in saga: {}", step.getStepName(), sagaName);

            try {
                step.compensate(context);
                log.info("Compensation completed for step: {} in saga: {}",
                        step.getStepName(), sagaName);
            } catch (Exception e) {
                // Log but continue compensating other steps
                log.error("Compensation failed for step: {} in saga: {} - {}",
                        step.getStepName(), sagaName, e.getMessage(), e);
            }
        }

        log.info("Compensation completed for saga: {}", sagaName);
    }

    /**
     * Gets the name of this saga.
     * 
     * @return The saga name
     */
    public String getSagaName() {
        return sagaName;
    }
}
