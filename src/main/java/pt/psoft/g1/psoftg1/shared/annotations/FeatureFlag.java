package pt.psoft.g1.psoftg1.shared.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that are protected by a feature flag.
 * 
 * When applied, the method will:
 * - Check if the feature is enabled before execution
 * - Respect kill switch status
 * - Support dark launch mode
 * - Track errors for auto-kill mechanism
 * 
 * Usage:
 * 
 * <pre>
 * {@code
 * @FeatureFlag(name = "book.recommendations", fallback = "getDefaultRecommendations")
 * public List<Book> getRecommendations() {
 *     // New feature code
 * }
 * 
 * public List<Book> getDefaultRecommendations() {
 *     // Fallback code
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureFlag {

    /**
     * The name of the feature flag
     */
    String name();

    /**
     * Fallback method name to call if feature is disabled
     * If empty, throws FeatureDisabledException
     */
    String fallback() default "";

    /**
     * Whether this feature supports dark launch (shadow execution)
     */
    boolean darkLaunch() default false;

    /**
     * Whether to track errors for auto-kill mechanism
     */
    boolean trackErrors() default true;

    /**
     * Custom message when feature is disabled
     */
    String disabledMessage() default "Feature is currently disabled";
}
