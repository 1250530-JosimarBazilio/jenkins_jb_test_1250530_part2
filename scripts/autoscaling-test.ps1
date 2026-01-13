# ===========================================
# Autoscaling Test Script for LMS Books
# ===========================================
# Este script testa o autoscaling do microserviço:
# 1. Aplica HPA configuration
# 2. Gera carga com k6/hey
# 3. Monitoriza scaling events
# 4. Gera relatório

param(
    [Parameter(Mandatory = $false)]
    [ValidateSet("apply", "test", "monitor", "report", "cleanup")]
    [string]$Action = "test",
    
    [int]$Duration = 300,  # 5 minutes
    [int]$VirtualUsers = 100,
    [string]$Namespace = "lms-books",
    [string]$ServiceUrl = "http://localhost:8081"
)

# Colors
function Write-Info { param($msg) Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Success { param($msg) Write-Host "[SUCCESS] $msg" -ForegroundColor Green }
function Write-Warning { param($msg) Write-Host "[WARNING] $msg" -ForegroundColor Yellow }
function Write-Error { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red }

$ResultsDir = "autoscaling-test-results-$(Get-Date -Format 'yyyyMMdd_HHmmss')"

# ===========================================
# FUNCTIONS
# ===========================================

function Apply-HPAConfig {
    Write-Info "============================================"
    Write-Info "APPLYING HPA CONFIGURATION"
    Write-Info "============================================"
    
    try {
        # Apply HPA
        kubectl apply -f k8s/lms-books-hpa.yaml
        Write-Success "HPA configuration applied"
        
        # Wait for HPA to be ready
        Write-Info "Waiting for HPA to be ready..."
        Start-Sleep -Seconds 10
        
        # Show HPA status
        kubectl get hpa -n $Namespace
        
    }
    catch {
        Write-Error "Failed to apply HPA: $($_.Exception.Message)"
    }
}

function Get-CurrentReplicas {
    $replicas = kubectl get deployment lms-books -n $Namespace -o jsonpath='{.status.readyReplicas}' 2>$null
    return [int]$replicas
}

function Get-HPAStatus {
    kubectl get hpa lms-books-hpa -n $Namespace -o wide 2>$null
}

function Start-LoadTest {
    Write-Info "============================================"
    Write-Info "STARTING LOAD TEST FOR AUTOSCALING"
    Write-Info "============================================"
    Write-Info "Duration: $Duration seconds"
    Write-Info "Virtual Users: $VirtualUsers"
    Write-Info "Target: $ServiceUrl"
    
    # Create results directory
    New-Item -ItemType Directory -Force -Path $ResultsDir | Out-Null
    
    # Record initial state
    $initialReplicas = Get-CurrentReplicas
    Write-Info "Initial Replicas: $initialReplicas"
    
    # Start monitoring in background
    $monitorJob = Start-Job -ScriptBlock {
        param($Namespace, $Duration, $OutputFile)
        
        $endTime = (Get-Date).AddSeconds($Duration)
        $samples = @()
        
        while ((Get-Date) -lt $endTime) {
            $timestamp = Get-Date -Format "HH:mm:ss"
            $replicas = kubectl get deployment lms-books -n $Namespace -o jsonpath='{.status.readyReplicas}' 2>$null
            $cpu = kubectl top pods -n $Namespace --no-headers 2>$null | ForEach-Object { ($_ -split '\s+')[1] }
            
            $samples += [PSCustomObject]@{
                Time     = $timestamp
                Replicas = $replicas
                CPU      = ($cpu -join ',')
            }
            
            Start-Sleep -Seconds 10
        }
        
        $samples | Export-Csv -Path $OutputFile -NoTypeInformation
    } -ArgumentList $Namespace, $Duration, "$ResultsDir/scaling-metrics.csv"
    
    # Generate load with hey (if available) or PowerShell
    $heyPath = Get-Command hey -ErrorAction SilentlyContinue
    
    if ($heyPath) {
        Write-Info "Using 'hey' for load generation..."
        
        # Concurrent requests to multiple endpoints
        $endpoints = @(
            "/api/books/health",
            "/api/books",
            "/api/books/9780134685991"
        )
        
        foreach ($endpoint in $endpoints) {
            Start-Job -ScriptBlock {
                param($url, $duration, $concurrency)
                hey -z "${duration}s" -c $concurrency -q 10 $url 2>&1
            } -ArgumentList "$ServiceUrl$endpoint", ($Duration / 3), ($VirtualUsers / 3)
        }
        
    }
    else {
        Write-Warning "'hey' not found. Using PowerShell for load generation..."
        Write-Info "Install hey: go install github.com/rakyll/hey@latest"
        
        # PowerShell-based load generation
        $jobs = @()
        
        for ($i = 0; $i -lt $VirtualUsers; $i++) {
            $jobs += Start-Job -ScriptBlock {
                param($url, $duration)
                
                $endTime = (Get-Date).AddSeconds($duration)
                $count = 0
                $errors = 0
                
                while ((Get-Date) -lt $endTime) {
                    try {
                        Invoke-RestMethod -Uri "$url/api/books/health" -TimeoutSec 5 | Out-Null
                        $count++
                    }
                    catch {
                        $errors++
                    }
                    Start-Sleep -Milliseconds 100
                }
                
                return @{ Requests = $count; Errors = $errors }
            } -ArgumentList $ServiceUrl, $Duration
        }
    }
    
    Write-Info "Load test started. Monitoring for $Duration seconds..."
    
    # Progress monitoring
    $startTime = Get-Date
    while (((Get-Date) - $startTime).TotalSeconds -lt $Duration) {
        $elapsed = [math]::Round(((Get-Date) - $startTime).TotalSeconds)
        $replicas = Get-CurrentReplicas
        
        Write-Host "`r[$(Get-Date -Format 'HH:mm:ss')] Elapsed: ${elapsed}s / ${Duration}s | Replicas: $replicas    " -NoNewline
        
        Start-Sleep -Seconds 5
    }
    
    Write-Host ""
    Write-Success "Load test completed!"
    
    # Wait for monitoring job
    Receive-Job -Job $monitorJob -Wait
    
    # Get final state
    $finalReplicas = Get-CurrentReplicas
    Write-Info "Final Replicas: $finalReplicas"
    
    # Show HPA events
    Write-Info ""
    Write-Info "HPA Events:"
    kubectl describe hpa lms-books-hpa -n $Namespace | Select-String -Pattern "Events:" -Context 0, 20
    
    return @{
        InitialReplicas = $initialReplicas
        FinalReplicas   = $finalReplicas
        ScaledUp        = $finalReplicas -gt $initialReplicas
    }
}

function Show-MonitorDashboard {
    Write-Info "============================================"
    Write-Info "AUTOSCALING MONITOR (Ctrl+C to stop)"
    Write-Info "============================================"
    
    while ($true) {
        Clear-Host
        
        Write-Host "==================== AUTOSCALING MONITOR ====================" -ForegroundColor Cyan
        Write-Host "Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray
        Write-Host ""
        
        # HPA Status
        Write-Host "--- HPA Status ---" -ForegroundColor Yellow
        Get-HPAStatus
        Write-Host ""
        
        # Pod Status
        Write-Host "--- Pods ---" -ForegroundColor Yellow
        kubectl get pods -n $Namespace -l app=lms-books --no-headers 2>$null | ForEach-Object {
            $parts = $_ -split '\s+'
            $status = $parts[2]
            $color = if ($status -eq "Running") { "Green" } elseif ($status -eq "Pending") { "Yellow" } else { "Red" }
            Write-Host $_ -ForegroundColor $color
        }
        Write-Host ""
        
        # Resource Usage
        Write-Host "--- Resource Usage ---" -ForegroundColor Yellow
        kubectl top pods -n $Namespace -l app=lms-books 2>$null
        Write-Host ""
        
        # Recent Events
        Write-Host "--- Recent Scaling Events ---" -ForegroundColor Yellow
        kubectl get events -n $Namespace --sort-by='.lastTimestamp' 2>$null | 
        Select-String -Pattern "Scaled|SuccessfulRescale" | 
        Select-Object -Last 5
        
        Start-Sleep -Seconds 5
    }
}

function Generate-Report {
    Write-Info "============================================"
    Write-Info "GENERATING AUTOSCALING REPORT"
    Write-Info "============================================"
    
    $reportPath = "$ResultsDir/autoscaling-report.md"
    
    $report = @"
# Autoscaling Test Report

**Date:** $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
**Namespace:** $Namespace
**Duration:** $Duration seconds
**Virtual Users:** $VirtualUsers

## HPA Configuration

``````yaml
$(kubectl get hpa lms-books-hpa -n $Namespace -o yaml 2>$null)
``````

## Test Results

### Pod Scaling Timeline

| Time | Replicas | CPU Usage |
|------|----------|-----------|
$(if (Test-Path "$ResultsDir/scaling-metrics.csv") {
    Import-Csv "$ResultsDir/scaling-metrics.csv" | ForEach-Object {
        "| $($_.Time) | $($_.Replicas) | $($_.CPU) |"
    }
} else {
    "| N/A | N/A | N/A |"
})

### HPA Events

``````
$(kubectl describe hpa lms-books-hpa -n $Namespace 2>$null | Select-String -Pattern "Events:" -Context 0,30)
``````

### Final State

``````
$(kubectl get pods -n $Namespace -l app=lms-books -o wide 2>$null)
``````

## Recommendations

1. **CPU Threshold:** Current: 70% - Adjust based on application profile
2. **Memory Threshold:** Current: 80% - Monitor for memory leaks
3. **Scale Down Delay:** Current: 300s - Prevents thrashing during traffic fluctuations
4. **Max Replicas:** Current: 10 - Increase for higher load requirements

"@

    $report | Out-File -FilePath $reportPath -Encoding UTF8
    Write-Success "Report generated: $reportPath"
}

function Cleanup-HPAConfig {
    Write-Info "============================================"
    Write-Info "CLEANING UP HPA CONFIGURATION"
    Write-Info "============================================"
    
    kubectl delete -f k8s/lms-books-hpa.yaml --ignore-not-found
    Write-Success "HPA configuration removed"
}

# ===========================================
# MAIN
# ===========================================

switch ($Action) {
    "apply" {
        Apply-HPAConfig
    }
    "test" {
        Apply-HPAConfig
        Start-Sleep -Seconds 5
        $result = Start-LoadTest
        Generate-Report
        
        Write-Info ""
        Write-Info "============================================"
        if ($result.ScaledUp) {
            Write-Success "AUTOSCALING TEST PASSED!"
            Write-Success "Pods scaled from $($result.InitialReplicas) to $($result.FinalReplicas)"
        }
        else {
            Write-Warning "Autoscaling may not have triggered (load may have been insufficient)"
            Write-Info "Initial: $($result.InitialReplicas), Final: $($result.FinalReplicas)"
        }
        Write-Info "============================================"
    }
    "monitor" {
        Show-MonitorDashboard
    }
    "report" {
        Generate-Report
    }
    "cleanup" {
        Cleanup-HPAConfig
    }
}
