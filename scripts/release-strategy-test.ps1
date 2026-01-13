# ===========================================
# Dark Launch and Kill Switch Test Script (PowerShell)
# ===========================================
# Este script permite testar as estratégias de release:
# - Dark Launch: Features ocultas em produção
# - Kill Switch: Desativação instantânea de features
# - Canary Release: Rollout gradual
#
# Uso:
#   .\release-strategy-test.ps1 -Action status
#   .\release-strategy-test.ps1 -Action kill -Feature "book.ai-summary"
#   .\release-strategy-test.ps1 -Action darklaunch -Feature "book.recommendations"

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("status", "kill", "revive", "darklaunch", "promote", "test", "globalKill", "globalRevive")]
    [string]$Action,
    
    [string]$Feature,
    [string]$Reason = "Manual action via script",
    [string]$User = $env:USERNAME,
    [string]$BaseUrl = "http://localhost:8080"
)

# Cores
function Write-Info { param($msg) Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Success { param($msg) Write-Host "[SUCCESS] $msg" -ForegroundColor Green }
function Write-Warning { param($msg) Write-Host "[WARNING] $msg" -ForegroundColor Yellow }
function Write-Error { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red }
function Write-Feature { param($msg) Write-Host "[FEATURE] $msg" -ForegroundColor Magenta }

$ApiBase = "$BaseUrl/api/release"

# ===========================================
# FUNÇÕES
# ===========================================

function Get-Status {
    Write-Info "============================================"
    Write-Info "RELEASE STRATEGIES STATUS"
    Write-Info "============================================"
    
    try {
        # Get health
        $health = Invoke-RestMethod -Uri "$ApiBase/health" -Method Get
        Write-Info "Status: $($health.status)"
        Write-Info "Global Kill Switch: $(if ($health.globalKillSwitchActive) { '🚨 ACTIVE' } else { '✅ Inactive' })"
        Write-Info "Dark Launch Enabled: $($health.darkLaunchEnabled)"
        Write-Info "Total Features: $($health.totalFeatures)"
        Write-Info "Killed Features: $($health.killedFeatures)"
        
        Write-Host ""
        Write-Info "FEATURE FLAGS:"
        Write-Info "--------------------------------------------"
        
        # Get all features
        $features = Invoke-RestMethod -Uri "$ApiBase/features" -Method Get
        foreach ($feature in $features.PSObject.Properties) {
            $name = $feature.Name
            $state = $feature.Value
            
            $status = if ($state.killed) { "🚨 KILLED" }
            elseif ($state.darkLaunch) { "🌑 DARK LAUNCH" }
            elseif ($state.enabled) { "✅ ENABLED" }
            else { "❌ DISABLED" }
            
            $rollout = "$($state.rolloutPercentage)%"
            
            Write-Feature "$name : $status (Rollout: $rollout)"
        }
        
        Write-Host ""
        Write-Info "KILL SWITCH STATUS:"
        Write-Info "--------------------------------------------"
        
        $killStatus = Invoke-RestMethod -Uri "$ApiBase/killswitch/status" -Method Get
        foreach ($ks in $killStatus.PSObject.Properties) {
            $name = $ks.Name
            $state = $ks.Value
            if ($state.killed) {
                Write-Error "$name : KILLED by $($state.killedBy) - $($state.killReason)"
            }
        }
        
    }
    catch {
        Write-Error "Failed to get status: $($_.Exception.Message)"
        Write-Warning "Make sure the application is running on $BaseUrl"
    }
}

function Invoke-KillFeature {
    param($FeatureName, $Reason, $User)
    
    Write-Warning "============================================"
    Write-Warning "KILLING FEATURE: $FeatureName"
    Write-Warning "============================================"
    
    $body = @{
        reason      = $Reason
        activatedBy = $User
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri "$ApiBase/killswitch/$FeatureName/kill" `
            -Method Post `
            -Body $body `
            -ContentType "application/json"
        
        Write-Error $response.message
        Write-Info "Killed by: $($response.triggeredBy)"
        Write-Info "Reason: $($response.reason)"
        Write-Info "Timestamp: $(Get-Date -UnixTimeSeconds ($response.timestamp / 1000))"
        
    }
    catch {
        Write-Error "Failed to kill feature: $($_.Exception.Message)"
    }
}

function Invoke-ReviveFeature {
    param($FeatureName, $User)
    
    Write-Success "============================================"
    Write-Success "REVIVING FEATURE: $FeatureName"
    Write-Success "============================================"
    
    try {
        $response = Invoke-RestMethod -Uri "$ApiBase/killswitch/$FeatureName/revive?revivedBy=$User" `
            -Method Post
        
        Write-Success $response.message
        
    }
    catch {
        Write-Error "Failed to revive feature: $($_.Exception.Message)"
    }
}

function Invoke-GlobalKill {
    param($Reason, $User)
    
    Write-Error "============================================"
    Write-Error "🚨 ACTIVATING GLOBAL KILL SWITCH 🚨"
    Write-Error "============================================"
    Write-Warning "This will DISABLE ALL FEATURES!"
    
    $confirm = Read-Host "Are you sure? (yes/no)"
    if ($confirm -ne "yes") {
        Write-Info "Cancelled"
        return
    }
    
    $body = @{
        reason      = $Reason
        activatedBy = $User
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri "$ApiBase/killswitch/global/activate" `
            -Method Post `
            -Body $body `
            -ContentType "application/json"
        
        Write-Error $response.message
        
    }
    catch {
        Write-Error "Failed to activate global kill switch: $($_.Exception.Message)"
    }
}

function Invoke-GlobalRevive {
    param($User)
    
    Write-Success "============================================"
    Write-Success "DEACTIVATING GLOBAL KILL SWITCH"
    Write-Success "============================================"
    
    try {
        $response = Invoke-RestMethod -Uri "$ApiBase/killswitch/global/deactivate?deactivatedBy=$User" `
            -Method Post
        
        Write-Success $response.message
        
    }
    catch {
        Write-Error "Failed to deactivate global kill switch: $($_.Exception.Message)"
    }
}

function Enable-DarkLaunch {
    param($FeatureName)
    
    Write-Feature "============================================"
    Write-Feature "ENABLING DARK LAUNCH: $FeatureName"
    Write-Feature "============================================"
    
    try {
        $response = Invoke-RestMethod -Uri "$ApiBase/darklaunch/$FeatureName/enable?shadowPercentage=10" `
            -Method Post
        
        Write-Success $response
        
    }
    catch {
        Write-Error "Failed to enable dark launch: $($_.Exception.Message)"
    }
}

function Invoke-PromoteToProd {
    param($FeatureName)
    
    Write-Success "============================================"
    Write-Success "PROMOTING TO PRODUCTION: $FeatureName"
    Write-Success "============================================"
    
    try {
        $response = Invoke-RestMethod -Uri "$ApiBase/darklaunch/$FeatureName/promote" `
            -Method Post
        
        Write-Success $response
        
    }
    catch {
        Write-Error "Failed to promote feature: $($_.Exception.Message)"
    }
}

function Test-DarkLaunchFeatures {
    Write-Info "============================================"
    Write-Info "TESTING DARK LAUNCH FEATURES"
    Write-Info "============================================"
    
    $testIsbn = "9780134685991"
    
    # Test AI Recommendations
    Write-Info ""
    Write-Info "Testing AI Recommendations..."
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/api/books/experimental/recommendations/$testIsbn" `
            -Method Get `
            -Headers @{"X-User-Id" = "test-user" }
        
        Write-Success "Recommendations: $($response.algorithm) - $($response.message)"
        Write-Info "Dark Launch: $($response.darkLaunch)"
        Write-Info "Count: $($response.recommendations.Count) books"
        
    }
    catch {
        Write-Warning "AI Recommendations: $($_.Exception.Message)"
    }
    
    # Test AI Summary
    Write-Info ""
    Write-Info "Testing AI Summary..."
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/api/books/experimental/ai-summary/$testIsbn" `
            -Method Post `
            -Headers @{"X-User-Id" = "test-user" }
        
        Write-Success "AI Summary Status: $($response.status)"
        
    }
    catch {
        Write-Warning "AI Summary: $($_.Exception.Message)"
    }
    
    # Test Analytics
    Write-Info ""
    Write-Info "Testing Analytics..."
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/api/books/experimental/analytics/$testIsbn" `
            -Method Get `
            -Headers @{"X-User-Id" = "test-user" }
        
        Write-Success "Analytics: $($response.type) - Dark Launch: $($response.darkLaunch)"
        
    }
    catch {
        Write-Warning "Analytics: $($_.Exception.Message)"
    }
    
    Write-Info ""
    Write-Info "============================================"
    Write-Info "TEST COMPLETE"
    Write-Info "============================================"
}

# ===========================================
# MAIN
# ===========================================

switch ($Action) {
    "status" {
        Get-Status
    }
    "kill" {
        if (-not $Feature) {
            Write-Error "Feature name required. Use -Feature <name>"
            exit 1
        }
        Invoke-KillFeature -FeatureName $Feature -Reason $Reason -User $User
    }
    "revive" {
        if (-not $Feature) {
            Write-Error "Feature name required. Use -Feature <name>"
            exit 1
        }
        Invoke-ReviveFeature -FeatureName $Feature -User $User
    }
    "globalKill" {
        Invoke-GlobalKill -Reason $Reason -User $User
    }
    "globalRevive" {
        Invoke-GlobalRevive -User $User
    }
    "darklaunch" {
        if (-not $Feature) {
            Write-Error "Feature name required. Use -Feature <name>"
            exit 1
        }
        Enable-DarkLaunch -FeatureName $Feature
    }
    "promote" {
        if (-not $Feature) {
            Write-Error "Feature name required. Use -Feature <name>"
            exit 1
        }
        Invoke-PromoteToProd -FeatureName $Feature
    }
    "test" {
        Test-DarkLaunchFeatures
    }
}
