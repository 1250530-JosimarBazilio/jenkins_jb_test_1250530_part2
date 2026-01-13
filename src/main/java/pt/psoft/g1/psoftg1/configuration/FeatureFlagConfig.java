package pt.psoft.g1.psoftg1.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature Flag Configuration for Release Strategies
 * 
 * Supports:
 * - Dark Launch: Test features in production without exposing to end users
 * - Kill Switch: Instantly disable features in case of problems
 * - Canary Release: Gradual rollout to percentage of users
 * 
 * Features can be controlled via:
 * - application.properties
 * - REST API endpoints
 * - Environment variables
 */
@Configuration
@ConfigurationProperties(prefix = "features")
@Getter
@Setter
public class FeatureFlagConfig {

    /**
     * Master kill switch - disables ALL features when true
     */
    private boolean globalKillSwitch = false;

    /**
     * Feature flags map with their states
     * Key: feature name, Value: FeatureState
     */
    private final Map<String, FeatureState> flags = new ConcurrentHashMap<>();

    /**
     * Dark launch configuration
     */
    private DarkLaunchConfig darkLaunch = new DarkLaunchConfig();

    /**
     * Kill switch configuration
     */
    private KillSwitchConfig killSwitch = new KillSwitchConfig();

    /**
     * Initialize default feature flags
     */
    public FeatureFlagConfig() {
        // Book management features
        flags.put("book.create", new FeatureState(true, false, 100));
        flags.put("book.update", new FeatureState(true, false, 100));
        flags.put("book.delete", new FeatureState(true, false, 100));
        flags.put("book.search", new FeatureState(true, false, 100));

        // New features under dark launch
        flags.put("book.recommendations", new FeatureState(false, true, 0));
        flags.put("book.analytics", new FeatureState(false, true, 0));
        flags.put("book.ai-summary", new FeatureState(false, true, 0));

        // Experimental features
        flags.put("book.v2-api", new FeatureState(false, true, 10));
        flags.put("book.batch-import", new FeatureState(false, true, 5));
    }

    /**
     * Check if a feature is enabled
     */
    public boolean isFeatureEnabled(String featureName) {
        if (globalKillSwitch) {
            return false;
        }

        FeatureState state = flags.get(featureName);
        if (state == null) {
            return true; // Unknown features are enabled by default
        }

        return state.isEnabled() && !state.isKilled();
    }

    /**
     * Check if a feature is in dark launch mode
     */
    public boolean isDarkLaunch(String featureName) {
        FeatureState state = flags.get(featureName);
        return state != null && state.isDarkLaunch();
    }

    /**
     * Check if user should see the feature (based on canary percentage)
     */
    public boolean isFeatureEnabledForUser(String featureName, String userId) {
        if (!isFeatureEnabled(featureName)) {
            return false;
        }

        FeatureState state = flags.get(featureName);
        if (state == null || state.getRolloutPercentage() >= 100) {
            return true;
        }

        // Deterministic hash for consistent user experience
        int hash = Math.abs((featureName + userId).hashCode() % 100);
        return hash < state.getRolloutPercentage();
    }

    /**
     * Enable a feature
     */
    public void enableFeature(String featureName) {
        FeatureState state = flags.computeIfAbsent(featureName,
                k -> new FeatureState(false, false, 100));
        state.setEnabled(true);
        state.setKilled(false);
    }

    /**
     * Disable a feature (soft disable)
     */
    public void disableFeature(String featureName) {
        FeatureState state = flags.get(featureName);
        if (state != null) {
            state.setEnabled(false);
        }
    }

    /**
     * Kill a feature (emergency disable)
     */
    public void killFeature(String featureName) {
        FeatureState state = flags.get(featureName);
        if (state != null) {
            state.setKilled(true);
            state.setKilledAt(System.currentTimeMillis());
        }
    }

    /**
     * Revive a killed feature
     */
    public void reviveFeature(String featureName) {
        FeatureState state = flags.get(featureName);
        if (state != null) {
            state.setKilled(false);
            state.setKilledAt(0);
        }
    }

    /**
     * Set rollout percentage for canary release
     */
    public void setRolloutPercentage(String featureName, int percentage) {
        FeatureState state = flags.get(featureName);
        if (state != null) {
            state.setRolloutPercentage(Math.max(0, Math.min(100, percentage)));
        }
    }

    /**
     * Get all feature states
     */
    public Map<String, FeatureState> getAllFeatures() {
        return new HashMap<>(flags);
    }

    /**
     * Feature State class
     */
    @Getter
    @Setter
    public static class FeatureState {
        private boolean enabled;
        private boolean darkLaunch;
        private boolean killed;
        private int rolloutPercentage;
        private long killedAt;
        private String killedBy;
        private String killReason;

        public FeatureState() {
            this.enabled = true;
            this.darkLaunch = false;
            this.rolloutPercentage = 100;
        }

        public FeatureState(boolean enabled, boolean darkLaunch, int rolloutPercentage) {
            this.enabled = enabled;
            this.darkLaunch = darkLaunch;
            this.rolloutPercentage = rolloutPercentage;
        }
    }

    /**
     * Dark Launch Configuration
     */
    @Getter
    @Setter
    public static class DarkLaunchConfig {
        private boolean enabled = true;
        private boolean logShadowRequests = true;
        private boolean compareResponses = false;
        private Set<String> allowedUsers = ConcurrentHashMap.newKeySet();
        private Set<String> allowedIps = ConcurrentHashMap.newKeySet();
        private double shadowTrafficPercentage = 10.0;
    }

    /**
     * Kill Switch Configuration
     */
    @Getter
    @Setter
    public static class KillSwitchConfig {
        private boolean autoKillOnErrors = true;
        private int errorThreshold = 10;
        private int errorWindowSeconds = 60;
        private boolean notifyOnKill = true;
        private String notificationWebhook;
    }
}
