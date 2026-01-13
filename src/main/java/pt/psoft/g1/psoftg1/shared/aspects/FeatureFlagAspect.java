package pt.psoft.g1.psoftg1.shared.aspects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.configuration.FeatureFlagConfig;
import pt.psoft.g1.psoftg1.shared.annotations.FeatureFlag;
import pt.psoft.g1.psoftg1.shared.services.KillSwitchService;

import java.lang.reflect.Method;

/**
 * Aspect that handles @FeatureFlag annotation processing.
 * 
 * Provides:
 * - Feature flag checks before method execution
 * - Kill switch enforcement
 * - Error tracking for auto-kill
 * - Fallback method execution
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagAspect {

    private final FeatureFlagConfig featureFlagConfig;
    private final KillSwitchService killSwitchService;

    @Around("@annotation(featureFlag)")
    public Object handleFeatureFlag(ProceedingJoinPoint joinPoint, FeatureFlag featureFlag) throws Throwable {
        String featureName = featureFlag.name();

        log.debug("[FEATURE FLAG] Checking feature '{}' for method '{}'",
                featureName, joinPoint.getSignature().getName());

        // Check if feature is enabled
        if (!featureFlagConfig.isFeatureEnabled(featureName)) {
            log.info("[FEATURE FLAG] Feature '{}' is DISABLED", featureName);
            return handleDisabledFeature(joinPoint, featureFlag);
        }

        // Check if feature is killed
        if (killSwitchService.isFeatureKilled(featureName)) {
            log.warn("[KILL SWITCH] Feature '{}' is KILLED", featureName);
            return handleDisabledFeature(joinPoint, featureFlag);
        }

        // Execute the method
        try {
            Object result = joinPoint.proceed();

            // Report success for auto-kill tracking
            if (featureFlag.trackErrors()) {
                killSwitchService.reportSuccess(featureName);
            }

            return result;

        } catch (Exception e) {
            // Report error for auto-kill tracking
            if (featureFlag.trackErrors()) {
                killSwitchService.reportError(featureName, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Handle disabled feature - execute fallback or throw exception
     */
    private Object handleDisabledFeature(ProceedingJoinPoint joinPoint, FeatureFlag featureFlag) throws Throwable {
        String fallbackMethod = featureFlag.fallback();

        if (!fallbackMethod.isEmpty()) {
            return executeFallback(joinPoint, fallbackMethod);
        }

        throw new FeatureDisabledException(featureFlag.name(), featureFlag.disabledMessage());
    }

    /**
     * Execute fallback method
     */
    private Object executeFallback(ProceedingJoinPoint joinPoint, String fallbackMethodName) throws Throwable {
        Object target = joinPoint.getTarget();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?>[] paramTypes = signature.getParameterTypes();

        try {
            Method fallbackMethod = target.getClass().getMethod(fallbackMethodName, paramTypes);
            log.debug("[FEATURE FLAG] Executing fallback method '{}'", fallbackMethodName);
            return fallbackMethod.invoke(target, joinPoint.getArgs());
        } catch (NoSuchMethodException e) {
            // Try without parameters
            try {
                Method fallbackMethod = target.getClass().getMethod(fallbackMethodName);
                return fallbackMethod.invoke(target);
            } catch (NoSuchMethodException e2) {
                log.error("[FEATURE FLAG] Fallback method '{}' not found", fallbackMethodName);
                throw new FeatureDisabledException(
                        ((FeatureFlag) signature.getMethod().getAnnotation(FeatureFlag.class)).name(),
                        "Feature disabled and fallback not available");
            }
        }
    }

    /**
     * Exception thrown when a feature is disabled
     */
    public static class FeatureDisabledException extends RuntimeException {
        private final String featureName;

        public FeatureDisabledException(String featureName, String message) {
            super(message);
            this.featureName = featureName;
        }

        public String getFeatureName() {
            return featureName;
        }
    }
}
