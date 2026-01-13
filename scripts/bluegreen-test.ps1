# ===========================================
# Blue/Green Deployment Test Script (Windows PowerShell)
# ===========================================
# Este script permite testar o deployment Blue/Green
# e o mecanismo de rollback automático
#
# Uso:
#   .\bluegreen-test.ps1 -Action deploy -Slot green
#   .\bluegreen-test.ps1 -Action deploy -Slot green -SimulateError
#   .\bluegreen-test.ps1 -Action rollback
#   .\bluegreen-test.ps1 -Action test

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("deploy", "rollback", "test", "status", "switch")]
    [string]$Action,
    
    [ValidateSet("blue", "green")]
    [string]$Slot = "green",
    
    [switch]$SimulateError,
    
    [string]$BlueUrl = "http://localhost:8090",
    [string]$GreenUrl = "http://localhost:8091"
)

# Cores
function Write-Info { param($msg) Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Success { param($msg) Write-Host "[SUCCESS] $msg" -ForegroundColor Green }
function Write-Warning { param($msg) Write-Host "[WARNING] $msg" -ForegroundColor Yellow }
function Write-Error { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red }
function Write-Test { param($msg) Write-Host "[TEST] $msg" -ForegroundColor Magenta }

$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not $ProjectRoot) { $ProjectRoot = (Get-Location).Path }

# ===========================================
# FUNÇÕES
# ===========================================

function Get-CurrentSlot {
    $configFile = Join-Path $ProjectRoot "traefik\dynamic\bluegreen.yml"
    if (Test-Path $configFile) {
        $content = Get-Content $configFile -Raw
        if ($content -match "weight:\s*100") {
            if ($content -match "blue.*weight:\s*100") { return "blue" }
            else { return "green" }
        }
    }
    return "unknown"
}

function Test-HealthEndpoint {
    param($Url, $SlotName)
    
    Write-Test "Testing health endpoint for $SlotName..."
    
    try {
        $response = Invoke-RestMethod -Uri "$Url/api/books/health" -Method Get -TimeoutSec 10
        
        if ($response.status -eq "HEALTHY") {
            Write-Success "Health check passed - Status: HEALTHY"
            return $true
        }
        elseif ($response.status -eq "UNHEALTHY") {
            Write-Error "Health check failed - Status: UNHEALTHY"
            return $false
        }
        else {
            Write-Success "Health check passed"
            return $true
        }
    }
    catch {
        Write-Error "Health check failed - $($_.Exception.Message)"
        return $false
    }
}

function Test-DeploymentInfo {
    param($Url, $ExpectedSlot)
    
    Write-Test "Testing deployment info for $ExpectedSlot..."
    
    try {
        $response = Invoke-RestMethod -Uri "$Url/api/books/deployment-info" -Method Get -TimeoutSec 10
        
        if ($response.slot -eq $ExpectedSlot) {
            Write-Success "Deployment info correct - Slot: $ExpectedSlot, Version: $($response.version)"
            
            if ($response.simulateErrorMode) {
                Write-Warning "Simulate error mode is ACTIVE"
            }
            return $true
        }
        else {
            Write-Error "Deployment slot mismatch - Expected: $ExpectedSlot, Got: $($response.slot)"
            return $false
        }
    }
    catch {
        Write-Warning "Deployment info not available (may be BLUE deployment)"
        return $true
    }
}

function Test-CreateBook {
    param($Url, $SlotName)
    
    Write-Test "Testing book creation on $SlotName..."
    
    $testIsbn = "978-0-13-468599-$(Get-Date -Format 'yyyyMMddHHmmss')"
    $body = @{
        title         = "Smoke Test Book"
        genre         = "Fiction"
        description   = "Test book for smoke testing"
        authorNumbers = @("1")
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri "$Url/api/books/$testIsbn" `
            -Method Put `
            -Body $body `
            -ContentType "application/json" `
            -TimeoutSec 10
        
        if ($response.deploymentSlot -eq "GREEN") {
            Write-Success "Book creation passed - GREEN deployment confirmed"
        }
        else {
            Write-Success "Book creation passed"
        }
        return $true
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 500) {
            Write-Error "Book creation failed - Internal Server Error (possible error simulation)"
        }
        else {
            Write-Error "Book creation failed - HTTP $statusCode"
        }
        return $false
    }
}

function Invoke-Deploy {
    param($Slot, $SimulateError)
    
    Write-Info "============================================"
    Write-Info "DEPLOYING $($Slot.ToUpper()) ENVIRONMENT"
    Write-Info "============================================"
    
    $env:SIMULATE_ERROR = if ($SimulateError) { "true" } else { "false" }
    $env:DEPLOYMENT_SLOT = $Slot
    
    if ($SimulateError) {
        Write-Warning "Simulate Error Mode: ENABLED"
        Write-Warning "This will trigger automatic rollback"
    }
    
    Push-Location $ProjectRoot
    try {
        # Build and deploy
        Write-Info "Building and deploying $Slot..."
        
        $composeCmd = "docker compose -f docker-compose.bluegreen.yml up -d --build lms-books-$Slot"
        Invoke-Expression $composeCmd
        
        # Wait for health
        Write-Info "Waiting for service to be healthy..."
        $url = if ($Slot -eq "green") { $GreenUrl } else { $BlueUrl }
        $attempts = 0
        $maxAttempts = 30
        
        while ($attempts -lt $maxAttempts) {
            try {
                $response = Invoke-WebRequest -Uri "$url/actuator/health" -TimeoutSec 5 -UseBasicParsing
                if ($response.StatusCode -eq 200) {
                    Write-Success "Service is healthy!"
                    break
                }
            }
            catch {
                $attempts++
                Write-Info "Waiting... ($attempts/$maxAttempts)"
                Start-Sleep -Seconds 5
            }
        }
        
        if ($attempts -ge $maxAttempts) {
            Write-Error "Service failed to become healthy"
            Invoke-Rollback
            return
        }
        
        # Run smoke tests
        Write-Info "Running smoke tests..."
        $passed = 0
        $failed = 0
        
        if (Test-HealthEndpoint -Url $url -SlotName $Slot) { $passed++ } else { $failed++ }
        if (Test-DeploymentInfo -Url $url -ExpectedSlot $Slot) { $passed++ } else { $failed++ }
        if (Test-CreateBook -Url $url -SlotName $Slot) { $passed++ } else { $failed++ }
        
        $total = $passed + $failed
        $successRate = [math]::Round(($passed / $total) * 100, 0)
        
        Write-Info "============================================"
        Write-Info "TEST RESULTS"
        Write-Info "============================================"
        Write-Info "Passed: $passed / $total ($successRate%)"
        
        if ($successRate -lt 80) {
            Write-Error "Success rate below 80% - Triggering rollback"
            Invoke-Rollback
        }
        else {
            Write-Success "All tests passed! Switching traffic to $Slot..."
            Invoke-Switch -Slot $Slot
        }
        
    }
    finally {
        Pop-Location
    }
}

function Invoke-Rollback {
    Write-Error "============================================"
    Write-Error "AUTOMATIC ROLLBACK TRIGGERED"
    Write-Error "============================================"
    
    $current = Get-CurrentSlot
    $target = if ($current -eq "blue") { "green" } else { "blue" }
    
    Write-Info "Current slot: $current"
    Write-Info "Rolling back to: $target"
    
    Invoke-Switch -Slot $target
    
    Write-Success "Rollback completed!"
}

function Invoke-Switch {
    param($Slot)
    
    Write-Info "Switching traffic to $Slot..."
    
    $configFile = Join-Path $ProjectRoot "traefik\dynamic\bluegreen.yml"
    
    if (Test-Path $configFile) {
        $content = Get-Content $configFile -Raw
        
        if ($Slot -eq "blue") {
            # Blue gets weight 100, Green gets weight 0
            $content = $content -replace '(lms-books-blue:\s*weight:\s*)\d+', '${1}100'
            $content = $content -replace '(lms-books-green:\s*weight:\s*)\d+', '${1}0'
        }
        else {
            # Green gets weight 100, Blue gets weight 0
            $content = $content -replace '(lms-books-blue:\s*weight:\s*)\d+', '${1}0'
            $content = $content -replace '(lms-books-green:\s*weight:\s*)\d+', '${1}100'
        }
        
        Set-Content -Path $configFile -Value $content
        Write-Success "Traffic switched to $Slot"
    }
    else {
        Write-Error "Traefik config file not found"
    }
}

function Get-Status {
    Write-Info "============================================"
    Write-Info "BLUE/GREEN DEPLOYMENT STATUS"
    Write-Info "============================================"
    
    $currentSlot = Get-CurrentSlot
    Write-Info "Active Slot: $currentSlot"
    
    Write-Info ""
    Write-Info "BLUE Status:"
    $blueHealthy = Test-HealthEndpoint -Url $BlueUrl -SlotName "blue"
    
    Write-Info ""
    Write-Info "GREEN Status:"
    $greenHealthy = Test-HealthEndpoint -Url $GreenUrl -SlotName "green"
    Test-DeploymentInfo -Url $GreenUrl -ExpectedSlot "green"
}

function Invoke-Tests {
    Write-Info "============================================"
    Write-Info "RUNNING ALL SMOKE TESTS"
    Write-Info "============================================"
    
    $results = @()
    
    # Test Blue
    Write-Info ""
    Write-Info "Testing BLUE deployment..."
    $results += @{
        Slot   = "blue"
        Health = Test-HealthEndpoint -Url $BlueUrl -SlotName "blue"
        Create = Test-CreateBook -Url $BlueUrl -SlotName "blue"
    }
    
    # Test Green
    Write-Info ""
    Write-Info "Testing GREEN deployment..."
    $results += @{
        Slot           = "green"
        Health         = Test-HealthEndpoint -Url $GreenUrl -SlotName "green"
        DeploymentInfo = Test-DeploymentInfo -Url $GreenUrl -ExpectedSlot "green"
        Create         = Test-CreateBook -Url $GreenUrl -SlotName "green"
    }
    
    Write-Info ""
    Write-Info "============================================"
    Write-Info "TEST SUMMARY"
    Write-Info "============================================"
    
    foreach ($r in $results) {
        $allPassed = $r.Health -and $r.Create
        $status = if ($allPassed) { "PASSED" } else { "FAILED" }
        $color = if ($allPassed) { "Green" } else { "Red" }
        Write-Host "$($r.Slot.ToUpper()): $status" -ForegroundColor $color
    }
}

# ===========================================
# MAIN
# ===========================================

switch ($Action) {
    "deploy" {
        Invoke-Deploy -Slot $Slot -SimulateError $SimulateError
    }
    "rollback" {
        Invoke-Rollback
    }
    "switch" {
        Invoke-Switch -Slot $Slot
    }
    "test" {
        Invoke-Tests
    }
    "status" {
        Get-Status
    }
}
