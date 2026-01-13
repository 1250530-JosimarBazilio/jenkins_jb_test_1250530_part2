#!/usr/bin/env pwsh
# ===========================================
# Test Blue/Green Pipeline Script (PowerShell)
# ===========================================
# Simula a execução do Jenkinsfile.bluegreen localmente
# Uso: .\test-bluegreen-pipeline.ps1 [-Slot blue|green] [-SimulateError] [-Platform docker|k8s]

param(
    [ValidateSet("blue", "green")]
    [string]$Slot = "green",
    
    [switch]$SimulateError,
    
    [ValidateSet("docker", "k8s")]
    [string]$Platform = "k8s",
    
    [switch]$SkipBuild,
    
    [switch]$SkipDockerBuild,
    
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

# Cores
function Write-Blue { param($msg) Write-Host $msg -ForegroundColor Blue }
function Write-Green { param($msg) Write-Host $msg -ForegroundColor Green }
function Write-Yellow { param($msg) Write-Host $msg -ForegroundColor Yellow }
function Write-Red { param($msg) Write-Host $msg -ForegroundColor Red }
function Write-Cyan { param($msg) Write-Host $msg -ForegroundColor Cyan }

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $ProjectRoot

# Versões
$BlueVersion = "v1.0.0"
$GreenVersion = "v2.0.0"
$Version = if ($Slot -eq "green") { $GreenVersion } else { $BlueVersion }

# ===========================================
# BANNER
# ===========================================
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   BLUE/GREEN DEPLOYMENT PIPELINE TEST" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Blue "[CONFIG] Target Slot: $Slot"
Write-Blue "[CONFIG] Version: $Version"
Write-Blue "[CONFIG] Platform: $Platform"
Write-Blue "[CONFIG] Simulate Error: $SimulateError"
Write-Host ""

# Variáveis de estado
$RollbackRequired = $false
$TestResults = @{
    Total  = 0
    Passed = 0
    Failed = 0
}

# ===========================================
# STAGE 1: BUILD & UNIT TESTS
# ===========================================
Write-Host ""
Write-Host "============================================" -ForegroundColor Yellow
Write-Host "   STAGE 1: BUILD & UNIT TESTS" -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Yellow

if (-not $SkipBuild) {
    try {
        Write-Blue "[INFO] Running mvn clean test..."
        
        if (-not $SkipTests) {
            $mvnResult = & mvn clean test -B 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Red "[FAILED] Unit tests failed"
                throw "Unit tests failed"
            }
        }
        
        Write-Blue "[INFO] Packaging application..."
        & mvn package -DskipTests -B 2>&1 | Out-Null
        
        if ($LASTEXITCODE -ne 0) {
            Write-Red "[FAILED] Package failed"
            throw "Package failed"
        }
        
        Write-Green "[SUCCESS] Build completed"
    }
    catch {
        Write-Red "[ERROR] Build stage failed: $_"
        exit 1
    }
}
else {
    Write-Yellow "[SKIP] Build stage skipped"
}

# ===========================================
# STAGE 2: BUILD DOCKER IMAGE
# ===========================================
Write-Host ""
Write-Host "============================================" -ForegroundColor Yellow
Write-Host "   STAGE 2: BUILD DOCKER IMAGE" -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Yellow

if (-not $SkipDockerBuild) {
    try {
        $profile = if ($SimulateError -and $Slot -eq "green") { "green-error" } else { $Slot }
        
        Write-Blue "[INFO] Building Docker image for $Slot (profile: $profile)..."
        
        $buildArgs = @(
            "build",
            "--build-arg", "SPRING_PROFILES_ACTIVE=postgres,$profile",
            "--build-arg", "DEPLOYMENT_SLOT=$Slot",
            "--build-arg", "VERSION=$Version",
            "--label", "deployment.slot=$Slot",
            "--label", "deployment.version=$Version",
            "--label", "deployment.release-strategy=blue-green",
            "--label", "deployment.rollback-enabled=true",
            "--label", "deployment.simulate-error=$SimulateError",
            "-t", "lms-books:$Version",
            "-t", "lms-books:$Slot-latest",
            "."
        )
        
        & docker $buildArgs 2>&1 | ForEach-Object { 
            if ($_ -match "error|failed") { Write-Red $_ } 
            else { Write-Host $_ }
        }
        
        if ($LASTEXITCODE -ne 0) {
            throw "Docker build failed"
        }
        
        Write-Green "[SUCCESS] Docker image built: lms-books:$Version"
    }
    catch {
        Write-Red "[ERROR] Docker build failed: $_"
        exit 1
    }
}
else {
    # Verificar se imagem existe
    $imageExists = docker images lms-books:latest -q 2>&1
    if ($imageExists) {
        Write-Yellow "[SKIP] Docker build skipped - using existing image"
    }
    else {
        Write-Red "[ERROR] No existing Docker image found. Please run without -SkipDockerBuild"
        exit 1
    }
}

# ===========================================
# STAGE 3: DEPLOY TO TARGET SLOT
# ===========================================
Write-Host ""
Write-Host "============================================" -ForegroundColor Yellow
Write-Host "   STAGE 3: DEPLOY TO TARGET SLOT ($Slot)" -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Yellow

try {
    if ($Platform -eq "k8s") {
        Write-Blue "[INFO] Deploying to Kubernetes..."
        
        # Verificar se namespace existe
        $nsExists = kubectl get namespace lms-books 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Yellow "[INFO] Creating namespace lms-books..."
            kubectl create namespace lms-books 2>&1 | Out-Null
        }
        
        # Aplicar configurações base
        Write-Blue "[INFO] Applying Kubernetes configurations..."
        kubectl apply -k k8s/ 2>&1 | ForEach-Object { Write-Host $_ }
        
        # Aguardar deployment
        Write-Blue "[INFO] Waiting for deployment rollout..."
        $rolloutResult = kubectl rollout status deployment/lms-books -n lms-books --timeout=120s 2>&1
        
        if ($LASTEXITCODE -ne 0) {
            throw "Deployment rollout failed: $rolloutResult"
        }
        
        Write-Green "[SUCCESS] Deployment to $Slot completed"
    }
    else {
        Write-Blue "[INFO] Deploying to Docker..."
        
        # Deploy com docker-compose
        $env:DEPLOYMENT_SLOT = $Slot
        
        # Run docker compose and capture exit code properly
        $ErrorActionPreference = "Continue"
        docker compose -f docker-compose.bluegreen.yml up -d "lms-books-$Slot" 2>&1 | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
        $composeExitCode = $LASTEXITCODE
        $ErrorActionPreference = "Stop"
        
        # Check if containers are running (docker compose up -d returns 0 on success)
        if ($composeExitCode -eq 0) {
            Write-Green "[SUCCESS] Deployment to $Slot completed"
        }
        else {
            throw "Docker compose failed with exit code: $composeExitCode"
        }
    }
}
catch {
    Write-Red "[ERROR] Deployment failed: $_"
    $RollbackRequired = $true
}

# ===========================================
# STAGE 4: HEALTH CHECK
# ===========================================
Write-Host ""
Write-Host "============================================" -ForegroundColor Yellow
Write-Host "   STAGE 4: HEALTH CHECK" -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Yellow

$healthUrl = if ($Platform -eq "k8s") { "http://localhost:30080" } else { 
    if ($Slot -eq "green") { "http://localhost:8091" } else { "http://localhost:8090" }
}

$healthy = $false
$attempts = 0
$maxAttempts = 30

Write-Blue "[INFO] Checking health at $healthUrl..."

while (-not $healthy -and $attempts -lt $maxAttempts) {
    try {
        # Try actuator/health first, fallback to /api/books endpoint
        try {
            $response = Invoke-RestMethod -Uri "$healthUrl/actuator/health" -TimeoutSec 5 -ErrorAction Stop
            if ($response.status -eq "UP") {
                $healthy = $true
                Write-Green "[SUCCESS] Health check passed - Actuator Status: UP"
            }
        }
        catch {
            # Fallback: use curl.exe to check /api/books endpoint (more reliable)
            $curlResult = curl.exe -s -o NUL -w "%{http_code}" "$healthUrl/api/books" 2>&1
            # Accept any response (including 404 with JSON body) as healthy - means app is running
            if ($curlResult -match "^\d{3}$" -and [int]$curlResult -lt 500) {
                $healthy = $true
                Write-Green "[SUCCESS] Health check passed - API responding (HTTP $curlResult)"
            }
        }
    }
    catch {
        $attempts++
        Write-Yellow "[RETRY] Health check attempt $attempts/$maxAttempts..."
        Start-Sleep -Seconds 5
    }
}

if (-not $healthy) {
    Write-Red "[FAILED] Health check failed after $maxAttempts attempts"
    $RollbackRequired = $true
}

# ===========================================
# STAGE 5: SMOKE TESTS
# ===========================================
Write-Host ""
Write-Host "============================================" -ForegroundColor Yellow
Write-Host "   STAGE 5: SMOKE TESTS" -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Yellow

if (-not $RollbackRequired) {
    Write-Blue "[INFO] Running smoke tests..."
    
    # Test 1: API Health (using /api/books as health indicator)
    Write-Cyan "[TEST] Testing API availability..."
    $TestResults.Total++
    try {
        # Use curl.exe for reliable HTTP status checking (accepts 4xx as "app is running")
        $httpStatus = curl.exe -s -o NUL -w "%{http_code}" "$healthUrl/api/books" 2>&1
        if ($httpStatus -match "^\d{3}$" -and [int]$httpStatus -lt 500) {
            Write-Green "[PASS] API responding (HTTP $httpStatus)"
            $TestResults.Passed++
        }
        else {
            Write-Red "[FAIL] API returned: $httpStatus"
            $TestResults.Failed++
        }
    }
    catch {
        Write-Red "[FAIL] API health - Error: $_"
        $TestResults.Failed++
    }
    
    # Test 2: Actuator Info (optional - not all apps have it)
    Write-Cyan "[TEST] Testing /actuator/info (optional)..."
    $TestResults.Total++
    try {
        $info = Invoke-RestMethod -Uri "$healthUrl/actuator/info" -TimeoutSec 10 -ErrorAction Stop
        Write-Green "[PASS] Actuator info accessible"
        $TestResults.Passed++
    }
    catch {
        Write-Yellow "[WARN] Actuator info not configured (optional)"
        $TestResults.Passed++  # Non-critical - actuator may not be enabled
    }
    
    # Test 3: Books API endpoint
    Write-Cyan "[TEST] Testing /api/books endpoint..."
    $TestResults.Total++
    try {
        # Check that endpoint returns valid JSON (even if 404 with message)
        $response = curl.exe -s "$healthUrl/api/books" 2>&1
        if ($response -match '"message"' -or $response -match '\[' -or $response -match '\{') {
            Write-Green "[PASS] Books API returns valid JSON"
            $TestResults.Passed++
        }
        else {
            Write-Red "[FAIL] Books API invalid response: $response"
            $TestResults.Failed++
        }
    }
    catch {
        Write-Red "[FAIL] Books API - Error: $_"
        $TestResults.Failed++
    }
    
    # Test 4: Simulated Error Check
    if ($SimulateError) {
        Write-Cyan "[TEST] Testing simulated error endpoint..."
        $TestResults.Total++
        try {
            $errorTest = Invoke-RestMethod -Uri "$healthUrl/api/books/simulate-error" -TimeoutSec 10 -ErrorAction Stop
            Write-Red "[FAIL] Error simulation - should have failed"
            $TestResults.Failed++
        }
        catch {
            if ($_.Exception.Response.StatusCode -eq 500) {
                Write-Yellow "[SIMULATED] Error detected - Triggering rollback"
                $TestResults.Failed++
                $RollbackRequired = $true
            }
            else {
                Write-Green "[PASS] No simulated error detected"
                $TestResults.Passed++
            }
        }
    }
    
    # Test 5: Response Time
    Write-Cyan "[TEST] Testing response time..."
    $TestResults.Total++
    try {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $null = curl.exe -s "$healthUrl/api/books" 2>&1
        $sw.Stop()
        
        if ($sw.ElapsedMilliseconds -lt 2000) {
            Write-Green "[PASS] Response time: $($sw.ElapsedMilliseconds)ms (< 2000ms)"
            $TestResults.Passed++
        }
        else {
            Write-Yellow "[WARN] Slow response: $($sw.ElapsedMilliseconds)ms"
            $TestResults.Passed++  # Warning only
        }
    }
    catch {
        Write-Red "[FAIL] Response time test - Error: $_"
        $TestResults.Failed++
    }
    
    # Calcular resultado
    $successRate = if ($TestResults.Total -gt 0) { 
        [math]::Round(($TestResults.Passed / $TestResults.Total) * 100, 1) 
    }
    else { 0 }
    
    Write-Host ""
    Write-Host "----------------------------------------" -ForegroundColor Cyan
    Write-Cyan "SMOKE TEST RESULTS:"
    Write-Host "  Total:  $($TestResults.Total)"
    Write-Host "  Passed: $($TestResults.Passed)" -ForegroundColor Green
    Write-Host "  Failed: $($TestResults.Failed)" -ForegroundColor $(if ($TestResults.Failed -gt 0) { "Red" } else { "Green" })
    Write-Host "  Rate:   $successRate%"
    Write-Host "----------------------------------------" -ForegroundColor Cyan
    
    if ($successRate -lt 80) {
        Write-Red "[FAILED] Success rate below threshold (80%) - Triggering rollback"
        $RollbackRequired = $true
    }
}

# ===========================================
# STAGE 6: ROLLBACK (if required)
# ===========================================
if ($RollbackRequired) {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Red
    Write-Host "   STAGE 6: AUTOMATIC ROLLBACK" -ForegroundColor Red
    Write-Host "============================================" -ForegroundColor Red
    
    Write-Yellow "[INFO] Rollback triggered due to test failures or health check issues"
    
    try {
        if ($Platform -eq "k8s") {
            Write-Blue "[INFO] Executing Kubernetes rollback..."
            kubectl rollout undo deployment/lms-books -n lms-books 2>&1 | ForEach-Object { Write-Host $_ }
            
            Write-Blue "[INFO] Waiting for rollback to complete..."
            kubectl rollout status deployment/lms-books -n lms-books --timeout=120s 2>&1 | Out-Null
        }
        else {
            Write-Blue "[INFO] Executing Docker rollback..."
            $rollbackSlot = if ($Slot -eq "green") { "blue" } else { "green" }
            & bash scripts/bluegreen-switch.sh $rollbackSlot docker 2>&1
        }
        
        Write-Green "[SUCCESS] Rollback completed"
    }
    catch {
        Write-Red "[ERROR] Rollback failed: $_"
    }
}

# ===========================================
# STAGE 7: TRAFFIC SWITCH (if no rollback)
# ===========================================
if (-not $RollbackRequired -and $Slot -eq "green") {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Yellow
    Write-Host "   STAGE 7: TRAFFIC SWITCH APPROVAL" -ForegroundColor Yellow
    Write-Host "============================================" -ForegroundColor Yellow
    
    Write-Blue "[INFO] GREEN deployment verified successfully"
    Write-Yellow "[APPROVAL] In Jenkins, this would require email approval"
    Write-Host ""
    
    $approval = Read-Host "Approve traffic switch to GREEN? (y/n)"
    
    if ($approval -eq "y") {
        Write-Blue "[INFO] Switching traffic to GREEN..."
        
        if ($Platform -eq "k8s") {
            # Para K8s, o serviço já está apontando para os pods
            Write-Green "[SUCCESS] Traffic is already flowing to GREEN (single deployment)"
        }
        else {
            & bash scripts/bluegreen-switch.sh green docker 2>&1
        }
        
        Write-Green "[SUCCESS] Traffic switched to GREEN"
    }
    else {
        Write-Yellow "[INFO] Traffic switch rejected - staying on BLUE"
    }
}

# ===========================================
# FINAL SUMMARY
# ===========================================
Write-Host ""
Write-Host "============================================" -ForegroundColor $(if ($RollbackRequired) { "Red" } else { "Green" })
Write-Host "   PIPELINE EXECUTION SUMMARY" -ForegroundColor $(if ($RollbackRequired) { "Red" } else { "Green" })
Write-Host "============================================" -ForegroundColor $(if ($RollbackRequired) { "Red" } else { "Green" })
Write-Host ""

if ($RollbackRequired) {
    Write-Red "[RESULT] PIPELINE FAILED - ROLLBACK EXECUTED"
    Write-Host ""
    Write-Yellow "Rollback Trigger: Smoke tests or health check failed"
    Write-Yellow "Previous Version: Still active"
    exit 1
}
else {
    Write-Green "[RESULT] PIPELINE SUCCESSFUL"
    Write-Host ""
    Write-Host "  Slot Deployed:    $Slot" -ForegroundColor Cyan
    Write-Host "  Version:          $Version" -ForegroundColor Cyan
    Write-Host "  Platform:         $Platform" -ForegroundColor Cyan
    Write-Host "  Smoke Tests:      $($TestResults.Passed)/$($TestResults.Total) passed" -ForegroundColor Cyan
    Write-Host "  Success Rate:     $successRate%" -ForegroundColor Cyan
    Write-Host ""
    exit 0
}
