# Script para deploy no Kubernetes
# Execute com: .\deploy.ps1

param(
    [string]$Action = "apply",  # apply, delete, status
    [switch]$BuildImage = $false
)

$ErrorActionPreference = "Stop"

# Configurações
$ImageName = "lms-books"
$ImageTag = "latest"
$Namespace = "lms-books"

function Write-Header {
    param([string]$Message)
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host $Message -ForegroundColor Cyan
    Write-Host "========================================`n" -ForegroundColor Cyan
}

function Build-DockerImage {
    Write-Header "Building Docker Image"
    
    # Build JAR first
    Write-Host "Building JAR with Maven..." -ForegroundColor Yellow
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Maven build failed!" -ForegroundColor Red
        exit 1
    }
    
    # Build Docker image
    Write-Host "Building Docker image..." -ForegroundColor Yellow
    docker build -t "${ImageName}:${ImageTag}" .
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Docker build failed!" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Docker image built successfully!" -ForegroundColor Green
}

function Deploy-ToKubernetes {
    Write-Header "Deploying to Kubernetes"
    
    # Apply all manifests using kustomize
    Write-Host "Applying Kubernetes manifests..." -ForegroundColor Yellow
    kubectl apply -k k8s/
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Deployment failed!" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "`nWaiting for pods to be ready..." -ForegroundColor Yellow
    kubectl wait --for=condition=ready pod -l app=postgres -n $Namespace --timeout=120s
    kubectl wait --for=condition=ready pod -l app=rabbitmq -n $Namespace --timeout=120s
    kubectl wait --for=condition=ready pod -l app=lms-books -n $Namespace --timeout=180s
    
    Write-Host "`nDeployment completed successfully!" -ForegroundColor Green
    Get-Status
}

function Remove-FromKubernetes {
    Write-Header "Removing from Kubernetes"
    
    kubectl delete -k k8s/
    
    Write-Host "Resources removed!" -ForegroundColor Green
}

function Get-Status {
    Write-Header "Kubernetes Resources Status"
    
    Write-Host "=== Pods ===" -ForegroundColor Yellow
    kubectl get pods -n $Namespace -o wide
    
    Write-Host "`n=== Services ===" -ForegroundColor Yellow
    kubectl get services -n $Namespace
    
    Write-Host "`n=== Deployments ===" -ForegroundColor Yellow
    kubectl get deployments -n $Namespace
    
    # Get service URLs
    Write-Host "`n=== Access URLs ===" -ForegroundColor Yellow
    $lmsBooksPort = kubectl get service lms-books-service -n $Namespace -o jsonpath='{.spec.ports[0].nodePort}' 2>$null
    if ($lmsBooksPort) {
        Write-Host "LMS Books API: http://localhost:$lmsBooksPort" -ForegroundColor Green
    }
    
    Write-Host "`nTo access services locally, use port-forward:" -ForegroundColor Cyan
    Write-Host "  kubectl port-forward service/lms-books-service 8081:8081 -n $Namespace" -ForegroundColor White
    Write-Host "  kubectl port-forward service/rabbitmq-service 15672:15672 -n $Namespace" -ForegroundColor White
}

# Main execution
switch ($Action.ToLower()) {
    "apply" {
        if ($BuildImage) {
            Build-DockerImage
        }
        Deploy-ToKubernetes
    }
    "delete" {
        Remove-FromKubernetes
    }
    "status" {
        Get-Status
    }
    "build" {
        Build-DockerImage
    }
    default {
        Write-Host "Usage: .\deploy.ps1 -Action <apply|delete|status|build> [-BuildImage]" -ForegroundColor Yellow
        Write-Host "  apply   - Deploy to Kubernetes" -ForegroundColor White
        Write-Host "  delete  - Remove from Kubernetes" -ForegroundColor White
        Write-Host "  status  - Show deployment status" -ForegroundColor White
        Write-Host "  build   - Build Docker image only" -ForegroundColor White
        Write-Host "  -BuildImage - Build Docker image before deploying" -ForegroundColor White
    }
}
