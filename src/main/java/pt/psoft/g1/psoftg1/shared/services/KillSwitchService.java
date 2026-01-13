package pt.psoft.g1.psoftg1.shared.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pt.psoft.g1.psoftg1.configuration.FeatureFlagConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kill Switch Service
 * 
 * Provides instant feature disabling capabilities for emergency situations.
 * 
 * Features:
 * - Manual kill switch activation
 * - Automatic kill based on error thresholds
 * - Circuit breaker pattern integration
 * - Audit logging of all kill/revive operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KillSwitchService {

    private final FeatureFlagConfig featureFlagConfig;

    /**
     * Error counters per feature for auto-kill
     */
    private final Map<String, ErrorCounter> errorCounters = new ConcurrentHashMap<>();

    /**
     * Kill switch activation history
     */
    private final Map<String, KillSwitchEvent> killHistory = new ConcurrentHashMap<>();

    /**
     * Activate global kill switch - disables ALL features
     */
    public void activateGlobalKillSwitch(String reason, String activatedBy) {
        log.error("🚨 GLOBAL KILL SWITCH ACTIVATED by {} - Reason: {}", activatedBy, reason);
        featureFlagConfig.setGlobalKillSwitch(true);

        killHistory.put("GLOBAL", new KillSwitchEvent(
                "GLOBAL",
                reason,
                activatedBy,
                System.currentTimeMillis(),
                KillSwitchEvent.Type.GLOBAL_KILL));
    }

    /**
     * Deactivate global kill switch
     */
    public void deactivateGlobalKillSwitch(String deactivatedBy) {
        log.info("✅ GLOBAL KILL SWITCH DEACTIVATED by {}", deactivatedBy);
        featureFlagConfig.setGlobalKillSwitch(false);
    }

    /**
     * Kill a specific feature
     */
    public void killFeature(String featureName, String reason, String killedBy) {
        log.error("🚨 KILL SWITCH: Feature '{}' KILLED by {} - Reason: {}",
                featureName, killedBy, reason);

        FeatureFlagConfig.FeatureState state = featureFlagConfig.getFlags().get(featureName);
        if (state != null) {
            state.setKilled(true);
            state.setKilledAt(System.currentTimeMillis());
            state.setKilledBy(killedBy);
            state.setKillReason(reason);
        } else {
            // Create new killed state
            FeatureFlagConfig.FeatureState newState = new FeatureFlagConfig.FeatureState(false, false, 0);
            newState.setKilled(true);
            newState.setKilledAt(System.currentTimeMillis());
            newState.setKilledBy(killedBy);
            newState.setKillReason(reason);
            featureFlagConfig.getFlags().put(featureName, newState);
        }

        killHistory.put(featureName, new KillSwitchEvent(
                featureName,
                reason,
                killedBy,
                System.currentTimeMillis(),
                KillSwitchEvent.Type.FEATURE_KILL));
    }

    /**
     * Revive a killed feature
     */
    public void reviveFeature(String featureName, String revivedBy) {
        log.info("✅ KILL SWITCH: Feature '{}' REVIVED by {}", featureName, revivedBy);

        featureFlagConfig.reviveFeature(featureName);

        killHistory.put(featureName + "_revive", new KillSwitchEvent(
                featureName,
                "Feature revived",
                revivedBy,
                System.currentTimeMillis(),
                KillSwitchEvent.Type.FEATURE_REVIVE));
    }

    /**
     * Report an error for a feature (for auto-kill mechanism)
     */
    public void reportError(String featureName, String errorMessage) {
        ErrorCounter counter = errorCounters.computeIfAbsent(featureName,
                k -> new ErrorCounter(featureFlagConfig.getKillSwitch().getErrorWindowSeconds()));

        int errorCount = counter.incrementAndGet();

        log.warn("Feature '{}' error #{}: {}", featureName, errorCount, errorMessage);

        // Check if auto-kill threshold is reached
        if (featureFlagConfig.getKillSwitch().isAutoKillOnErrors() &&
                errorCount >= featureFlagConfig.getKillSwitch().getErrorThreshold()) {

            killFeature(featureName,
                    "Auto-killed: Error threshold reached (" + errorCount + " errors in " +
                            featureFlagConfig.getKillSwitch().getErrorWindowSeconds() + "s)",
                    "AUTO_KILL_SYSTEM");

            // Reset counter after kill
            counter.reset();
        }
    }

    /**
     * Report success for a feature (resets error counter)
     */
    public void reportSuccess(String featureName) {
        ErrorCounter counter = errorCounters.get(featureName);
        if (counter != null) {
            counter.decrementIfPositive();
        }
    }

    /**
     * Check if a feature is killed
     */
    public boolean isFeatureKilled(String featureName) {
        if (featureFlagConfig.isGlobalKillSwitch()) {
            return true;
        }

        FeatureFlagConfig.FeatureState state = featureFlagConfig.getFlags().get(featureName);
        return state != null && state.isKilled();
    }

    /**
     * Get kill switch status for all features
     */
    public Map<String, KillSwitchStatus> getStatus() {
        Map<String, KillSwitchStatus> status = new ConcurrentHashMap<>();

        status.put("GLOBAL", new KillSwitchStatus(
                "GLOBAL",
                featureFlagConfig.isGlobalKillSwitch(),
                0,
                null,
                null));

        featureFlagConfig.getFlags().forEach((name, state) -> {
            status.put(name, new KillSwitchStatus(
                    name,
                    state.isKilled(),
                    state.getKilledAt(),
                    state.getKilledBy(),
                    state.getKillReason()));
        });

        return status;
    }

    /**
     * Get kill history
     */
    public Map<String, KillSwitchEvent> getKillHistory() {
        return new ConcurrentHashMap<>(killHistory);
    }

    /**
     * Error Counter with time window
     */
    private static class ErrorCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private final int windowSeconds;
        private long windowStart;

        public ErrorCounter(int windowSeconds) {
            this.windowSeconds = windowSeconds;
            this.windowStart = System.currentTimeMillis();
        }

        public int incrementAndGet() {
            checkWindow();
            return count.incrementAndGet();
        }

        public void decrementIfPositive() {
            count.updateAndGet(c -> c > 0 ? c - 1 : 0);
        }

        public void reset() {
            count.set(0);
            windowStart = System.currentTimeMillis();
        }

        private void checkWindow() {
            long now = System.currentTimeMillis();
            if (now - windowStart > windowSeconds * 1000L) {
                reset();
            }
        }
    }

    /**
     * Kill Switch Status record
     */
    public record KillSwitchStatus(
            String featureName,
            boolean killed,
            long killedAt,
            String killedBy,
            String killReason) {
    }

    /**
     * Kill Switch Event record
     */
    public record KillSwitchEvent(
            String featureName,
            String reason,
            String triggeredBy,
            long timestamp,
            Type type) {
        public enum Type {
            GLOBAL_KILL,
            GLOBAL_REVIVE,
            FEATURE_KILL,
            FEATURE_REVIVE
        }
    }
}
