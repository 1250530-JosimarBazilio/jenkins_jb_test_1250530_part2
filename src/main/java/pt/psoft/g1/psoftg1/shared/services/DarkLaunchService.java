package pt.psoft.g1.psoftg1.shared.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pt.psoft.g1.psoftg1.configuration.FeatureFlagConfig;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Dark Launch Service
 * 
 * Enables testing new features in production without exposing them to end
 * users.
 * 
 * Strategies:
 * - Shadow Traffic: Execute new code path alongside old, compare results
 * - Feature Flags: Enable features for specific users/groups
 * - Canary Release: Gradual rollout to percentage of users
 * - A/B Testing: Compare different implementations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DarkLaunchService {

    private final FeatureFlagConfig featureFlagConfig;

    /**
     * Shadow execution results for comparison
     */
    private final Map<String, ShadowExecutionResult> shadowResults = new ConcurrentHashMap<>();

    /**
     * Metrics for dark launch features
     */
    private final Map<String, DarkLaunchMetrics> metrics = new ConcurrentHashMap<>();

    /**
     * Execute with dark launch - runs both old and new implementations
     * Returns result from production code, logs comparison with shadow code
     */
    public <T> T executeWithShadow(
            String featureName,
            Supplier<T> productionCode,
            Supplier<T> shadowCode) {

        // Always execute production code
        T productionResult = productionCode.get();

        // Check if dark launch is enabled for this feature
        if (!isDarkLaunchEnabled(featureName)) {
            return productionResult;
        }

        // Execute shadow code asynchronously
        executeShadowAsync(featureName, productionResult, shadowCode);

        return productionResult;
    }

    /**
     * Execute shadow code asynchronously and compare results
     */
    @Async
    protected <T> void executeShadowAsync(
            String featureName,
            T productionResult,
            Supplier<T> shadowCode) {

        try {
            long startTime = System.currentTimeMillis();
            T shadowResult = shadowCode.get();
            long duration = System.currentTimeMillis() - startTime;

            // Compare results
            boolean match = compareResults(productionResult, shadowResult);

            // Log results
            logShadowExecution(featureName, productionResult, shadowResult, match, duration);

            // Update metrics
            updateMetrics(featureName, match, duration, null);

        } catch (Exception e) {
            log.error("[DARK LAUNCH] Shadow execution failed for '{}': {}",
                    featureName, e.getMessage());
            updateMetrics(featureName, false, 0, e.getMessage());
        }
    }

    /**
     * Check if a feature should use dark launch
     */
    public boolean isDarkLaunchEnabled(String featureName) {
        if (!featureFlagConfig.getDarkLaunch().isEnabled()) {
            return false;
        }

        FeatureFlagConfig.FeatureState state = featureFlagConfig.getFlags().get(featureName);
        if (state == null || !state.isDarkLaunch()) {
            return false;
        }

        // Check shadow traffic percentage
        double percentage = featureFlagConfig.getDarkLaunch().getShadowTrafficPercentage();
        return Math.random() * 100 < percentage;
    }

    /**
     * Check if user is allowed to see dark launch features
     */
    public boolean isUserAllowedForDarkLaunch(String userId, String userIp) {
        FeatureFlagConfig.DarkLaunchConfig config = featureFlagConfig.getDarkLaunch();

        // Check allowed users
        if (config.getAllowedUsers().contains(userId)) {
            return true;
        }

        // Check allowed IPs
        if (userIp != null && config.getAllowedIps().contains(userIp)) {
            return true;
        }

        return false;
    }

    /**
     * Enable dark launch for a feature
     */
    public void enableDarkLaunch(String featureName, double shadowPercentage) {
        FeatureFlagConfig.FeatureState state = featureFlagConfig.getFlags().get(featureName);
        if (state != null) {
            state.setDarkLaunch(true);
            log.info("[DARK LAUNCH] Enabled for feature '{}' with {}% shadow traffic",
                    featureName, shadowPercentage);
        }
    }

    /**
     * Disable dark launch for a feature
     */
    public void disableDarkLaunch(String featureName) {
        FeatureFlagConfig.FeatureState state = featureFlagConfig.getFlags().get(featureName);
        if (state != null) {
            state.setDarkLaunch(false);
            log.info("[DARK LAUNCH] Disabled for feature '{}'", featureName);
        }
    }

    /**
     * Promote dark launch feature to production
     */
    public void promoteToProd(String featureName) {
        FeatureFlagConfig.FeatureState state = featureFlagConfig.getFlags().get(featureName);
        if (state != null) {
            state.setDarkLaunch(false);
            state.setEnabled(true);
            state.setRolloutPercentage(100);
            log.info("[DARK LAUNCH] Feature '{}' PROMOTED to production", featureName);
        }
    }

    /**
     * Add user to dark launch allowed list
     */
    public void addAllowedUser(String userId) {
        featureFlagConfig.getDarkLaunch().getAllowedUsers().add(userId);
        log.info("[DARK LAUNCH] User '{}' added to allowed list", userId);
    }

    /**
     * Remove user from dark launch allowed list
     */
    public void removeAllowedUser(String userId) {
        featureFlagConfig.getDarkLaunch().getAllowedUsers().remove(userId);
    }

    /**
     * Add IP to dark launch allowed list
     */
    public void addAllowedIp(String ip) {
        featureFlagConfig.getDarkLaunch().getAllowedIps().add(ip);
        log.info("[DARK LAUNCH] IP '{}' added to allowed list", ip);
    }

    /**
     * Get dark launch metrics for a feature
     */
    public DarkLaunchMetrics getMetrics(String featureName) {
        return metrics.get(featureName);
    }

    /**
     * Get all dark launch metrics
     */
    public Map<String, DarkLaunchMetrics> getAllMetrics() {
        return new ConcurrentHashMap<>(metrics);
    }

    /**
     * Get shadow execution results
     */
    public Map<String, ShadowExecutionResult> getShadowResults() {
        return new ConcurrentHashMap<>(shadowResults);
    }

    /**
     * Compare results between production and shadow
     */
    private <T> boolean compareResults(T production, T shadow) {
        if (production == null && shadow == null) {
            return true;
        }
        if (production == null || shadow == null) {
            return false;
        }
        return production.equals(shadow);
    }

    /**
     * Log shadow execution results
     */
    private <T> void logShadowExecution(
            String featureName,
            T productionResult,
            T shadowResult,
            boolean match,
            long duration) {

        if (!featureFlagConfig.getDarkLaunch().isLogShadowRequests()) {
            return;
        }

        if (match) {
            log.debug("[DARK LAUNCH] Feature '{}': Results MATCH ({}ms)", featureName, duration);
        } else {
            log.warn("[DARK LAUNCH] Feature '{}': Results MISMATCH ({}ms)", featureName, duration);
            log.warn("[DARK LAUNCH]   Production: {}", productionResult);
            log.warn("[DARK LAUNCH]   Shadow: {}", shadowResult);
        }

        // Store result for analysis
        shadowResults.put(featureName + "_" + System.currentTimeMillis(),
                new ShadowExecutionResult(
                        featureName,
                        String.valueOf(productionResult),
                        String.valueOf(shadowResult),
                        match,
                        duration,
                        System.currentTimeMillis()));
    }

    /**
     * Update metrics for a feature
     */
    private void updateMetrics(String featureName, boolean match, long duration, String error) {
        metrics.compute(featureName, (k, existing) -> {
            if (existing == null) {
                existing = new DarkLaunchMetrics(featureName);
            }
            existing.incrementExecutions();
            if (match) {
                existing.incrementMatches();
            } else {
                existing.incrementMismatches();
            }
            if (error != null) {
                existing.incrementErrors();
            }
            existing.updateAvgDuration(duration);
            return existing;
        });
    }

    /**
     * Shadow Execution Result record
     */
    public record ShadowExecutionResult(
            String featureName,
            String productionResult,
            String shadowResult,
            boolean match,
            long durationMs,
            long timestamp) {
    }

    /**
     * Dark Launch Metrics
     */
    public static class DarkLaunchMetrics {
        private final String featureName;
        private long totalExecutions;
        private long matches;
        private long mismatches;
        private long errors;
        private double avgDurationMs;

        public DarkLaunchMetrics(String featureName) {
            this.featureName = featureName;
        }

        public void incrementExecutions() {
            totalExecutions++;
        }

        public void incrementMatches() {
            matches++;
        }

        public void incrementMismatches() {
            mismatches++;
        }

        public void incrementErrors() {
            errors++;
        }

        public void updateAvgDuration(long duration) {
            if (totalExecutions == 1) {
                avgDurationMs = duration;
            } else {
                avgDurationMs = (avgDurationMs * (totalExecutions - 1) + duration) / totalExecutions;
            }
        }

        public String getFeatureName() {
            return featureName;
        }

        public long getTotalExecutions() {
            return totalExecutions;
        }

        public long getMatches() {
            return matches;
        }

        public long getMismatches() {
            return mismatches;
        }

        public long getErrors() {
            return errors;
        }

        public double getAvgDurationMs() {
            return avgDurationMs;
        }

        public double getMatchRate() {
            return totalExecutions > 0 ? (double) matches / totalExecutions * 100 : 0;
        }
    }
}
