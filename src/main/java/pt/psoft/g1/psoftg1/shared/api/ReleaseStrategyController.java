package pt.psoft.g1.psoftg1.shared.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.psoft.g1.psoftg1.configuration.FeatureFlagConfig;
import pt.psoft.g1.psoftg1.shared.services.DarkLaunchService;
import pt.psoft.g1.psoftg1.shared.services.KillSwitchService;

import java.util.Map;

/**
 * Release Strategy Controller
 * 
 * Provides REST endpoints for managing:
 * - Feature Flags
 * - Dark Launch features
 * - Kill Switch operations
 * - Canary Release configurations
 */
@Tag(name = "Release Strategies", description = "Endpoints for managing Dark Launch and Kill Switch")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/release")
public class ReleaseStrategyController {

    private final FeatureFlagConfig featureFlagConfig;
    private final KillSwitchService killSwitchService;
    private final DarkLaunchService darkLaunchService;

    // ============================================
    // FEATURE FLAGS
    // ============================================

    @Operation(summary = "Get all feature flags")
    @GetMapping("/features")
    public ResponseEntity<Map<String, FeatureFlagConfig.FeatureState>> getAllFeatures() {
        return ResponseEntity.ok(featureFlagConfig.getAllFeatures());
    }

    @Operation(summary = "Get feature flag status")
    @GetMapping("/features/{featureName}")
    public ResponseEntity<FeatureStatusResponse> getFeatureStatus(
            @PathVariable String featureName) {

        FeatureFlagConfig.FeatureState state = featureFlagConfig.getFlags().get(featureName);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new FeatureStatusResponse(
                featureName,
                state.isEnabled(),
                state.isDarkLaunch(),
                state.isKilled(),
                state.getRolloutPercentage(),
                featureFlagConfig.isFeatureEnabled(featureName)));
    }

    @Operation(summary = "Enable a feature")
    @PostMapping("/features/{featureName}/enable")
    public ResponseEntity<String> enableFeature(@PathVariable String featureName) {
        featureFlagConfig.enableFeature(featureName);
        return ResponseEntity.ok("Feature '" + featureName + "' enabled");
    }

    @Operation(summary = "Disable a feature")
    @PostMapping("/features/{featureName}/disable")
    public ResponseEntity<String> disableFeature(@PathVariable String featureName) {
        featureFlagConfig.disableFeature(featureName);
        return ResponseEntity.ok("Feature '" + featureName + "' disabled");
    }

    @Operation(summary = "Set feature rollout percentage")
    @PostMapping("/features/{featureName}/rollout")
    public ResponseEntity<String> setRolloutPercentage(
            @PathVariable String featureName,
            @RequestParam int percentage) {

        featureFlagConfig.setRolloutPercentage(featureName, percentage);
        return ResponseEntity.ok("Feature '" + featureName + "' rollout set to " + percentage + "%");
    }

    // ============================================
    // KILL SWITCH
    // ============================================

    @Operation(summary = "Get kill switch status for all features")
    @GetMapping("/killswitch/status")
    public ResponseEntity<Map<String, KillSwitchService.KillSwitchStatus>> getKillSwitchStatus() {
        return ResponseEntity.ok(killSwitchService.getStatus());
    }

    @Operation(summary = "Activate global kill switch")
    @PostMapping("/killswitch/global/activate")
    public ResponseEntity<KillSwitchResponse> activateGlobalKillSwitch(
            @RequestBody KillSwitchRequest request) {

        killSwitchService.activateGlobalKillSwitch(request.reason(), request.activatedBy());

        return ResponseEntity.ok(new KillSwitchResponse(
                "GLOBAL",
                true,
                request.reason(),
                request.activatedBy(),
                System.currentTimeMillis(),
                "🚨 GLOBAL KILL SWITCH ACTIVATED - All features disabled"));
    }

    @Operation(summary = "Deactivate global kill switch")
    @PostMapping("/killswitch/global/deactivate")
    public ResponseEntity<KillSwitchResponse> deactivateGlobalKillSwitch(
            @RequestParam String deactivatedBy) {

        killSwitchService.deactivateGlobalKillSwitch(deactivatedBy);

        return ResponseEntity.ok(new KillSwitchResponse(
                "GLOBAL",
                false,
                "Deactivated",
                deactivatedBy,
                System.currentTimeMillis(),
                "✅ GLOBAL KILL SWITCH DEACTIVATED - Features restored"));
    }

    @Operation(summary = "Kill a specific feature")
    @PostMapping("/killswitch/{featureName}/kill")
    public ResponseEntity<KillSwitchResponse> killFeature(
            @PathVariable String featureName,
            @RequestBody KillSwitchRequest request) {

        killSwitchService.killFeature(featureName, request.reason(), request.activatedBy());

        return ResponseEntity.ok(new KillSwitchResponse(
                featureName,
                true,
                request.reason(),
                request.activatedBy(),
                System.currentTimeMillis(),
                "🚨 Feature '" + featureName + "' KILLED"));
    }

    @Operation(summary = "Revive a killed feature")
    @PostMapping("/killswitch/{featureName}/revive")
    public ResponseEntity<KillSwitchResponse> reviveFeature(
            @PathVariable String featureName,
            @RequestParam String revivedBy) {

        killSwitchService.reviveFeature(featureName, revivedBy);

        return ResponseEntity.ok(new KillSwitchResponse(
                featureName,
                false,
                "Revived",
                revivedBy,
                System.currentTimeMillis(),
                "✅ Feature '" + featureName + "' REVIVED"));
    }

    @Operation(summary = "Get kill switch history")
    @GetMapping("/killswitch/history")
    public ResponseEntity<Map<String, KillSwitchService.KillSwitchEvent>> getKillHistory() {
        return ResponseEntity.ok(killSwitchService.getKillHistory());
    }

    // ============================================
    // DARK LAUNCH
    // ============================================

    @Operation(summary = "Get dark launch status")
    @GetMapping("/darklaunch/status")
    public ResponseEntity<DarkLaunchStatusResponse> getDarkLaunchStatus() {
        return ResponseEntity.ok(new DarkLaunchStatusResponse(
                featureFlagConfig.getDarkLaunch().isEnabled(),
                featureFlagConfig.getDarkLaunch().getShadowTrafficPercentage(),
                featureFlagConfig.getDarkLaunch().isLogShadowRequests(),
                featureFlagConfig.getDarkLaunch().isCompareResponses(),
                featureFlagConfig.getDarkLaunch().getAllowedUsers().size(),
                featureFlagConfig.getDarkLaunch().getAllowedIps().size()));
    }

    @Operation(summary = "Enable dark launch for a feature")
    @PostMapping("/darklaunch/{featureName}/enable")
    public ResponseEntity<String> enableDarkLaunch(
            @PathVariable String featureName,
            @RequestParam(defaultValue = "10.0") double shadowPercentage) {

        darkLaunchService.enableDarkLaunch(featureName, shadowPercentage);
        return ResponseEntity.ok("Dark launch enabled for '" + featureName +
                "' with " + shadowPercentage + "% shadow traffic");
    }

    @Operation(summary = "Disable dark launch for a feature")
    @PostMapping("/darklaunch/{featureName}/disable")
    public ResponseEntity<String> disableDarkLaunch(@PathVariable String featureName) {
        darkLaunchService.disableDarkLaunch(featureName);
        return ResponseEntity.ok("Dark launch disabled for '" + featureName + "'");
    }

    @Operation(summary = "Promote dark launch feature to production")
    @PostMapping("/darklaunch/{featureName}/promote")
    public ResponseEntity<String> promoteToProd(@PathVariable String featureName) {
        darkLaunchService.promoteToProd(featureName);
        return ResponseEntity.ok("Feature '" + featureName + "' promoted to production");
    }

    @Operation(summary = "Add user to dark launch allowed list")
    @PostMapping("/darklaunch/users/{userId}")
    public ResponseEntity<String> addDarkLaunchUser(@PathVariable String userId) {
        darkLaunchService.addAllowedUser(userId);
        return ResponseEntity.ok("User '" + userId + "' added to dark launch");
    }

    @Operation(summary = "Remove user from dark launch allowed list")
    @DeleteMapping("/darklaunch/users/{userId}")
    public ResponseEntity<String> removeDarkLaunchUser(@PathVariable String userId) {
        darkLaunchService.removeAllowedUser(userId);
        return ResponseEntity.ok("User '" + userId + "' removed from dark launch");
    }

    @Operation(summary = "Add IP to dark launch allowed list")
    @PostMapping("/darklaunch/ips/{ip}")
    public ResponseEntity<String> addDarkLaunchIp(@PathVariable String ip) {
        darkLaunchService.addAllowedIp(ip);
        return ResponseEntity.ok("IP '" + ip + "' added to dark launch");
    }

    @Operation(summary = "Get dark launch metrics")
    @GetMapping("/darklaunch/metrics")
    public ResponseEntity<Map<String, DarkLaunchService.DarkLaunchMetrics>> getDarkLaunchMetrics() {
        return ResponseEntity.ok(darkLaunchService.getAllMetrics());
    }

    @Operation(summary = "Get shadow execution results")
    @GetMapping("/darklaunch/shadow-results")
    public ResponseEntity<Map<String, DarkLaunchService.ShadowExecutionResult>> getShadowResults() {
        return ResponseEntity.ok(darkLaunchService.getShadowResults());
    }

    // ============================================
    // HEALTH & INFO
    // ============================================

    @Operation(summary = "Get release strategies health status")
    @GetMapping("/health")
    public ResponseEntity<ReleaseHealthResponse> getHealth() {
        return ResponseEntity.ok(new ReleaseHealthResponse(
                "HEALTHY",
                featureFlagConfig.isGlobalKillSwitch(),
                featureFlagConfig.getDarkLaunch().isEnabled(),
                featureFlagConfig.getFlags().size(),
                (int) featureFlagConfig.getFlags().values().stream()
                        .filter(FeatureFlagConfig.FeatureState::isKilled).count()));
    }

    // ============================================
    // DTOs
    // ============================================

    public record FeatureStatusResponse(
            String featureName,
            boolean enabled,
            boolean darkLaunch,
            boolean killed,
            int rolloutPercentage,
            boolean effectivelyEnabled) {
    }

    public record KillSwitchRequest(
            String reason,
            String activatedBy) {
    }

    public record KillSwitchResponse(
            String featureName,
            boolean killed,
            String reason,
            String triggeredBy,
            long timestamp,
            String message) {
    }

    public record DarkLaunchStatusResponse(
            boolean enabled,
            double shadowTrafficPercentage,
            boolean logShadowRequests,
            boolean compareResponses,
            int allowedUsersCount,
            int allowedIpsCount) {
    }

    public record ReleaseHealthResponse(
            String status,
            boolean globalKillSwitchActive,
            boolean darkLaunchEnabled,
            int totalFeatures,
            int killedFeatures) {
    }
}
