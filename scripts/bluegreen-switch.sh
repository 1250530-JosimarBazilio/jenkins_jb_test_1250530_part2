#!/bin/bash
# ===========================================
# Blue/Green Deployment Switch Script
# ===========================================
# Este script permite alternar entre os ambientes Blue e Green
# Uso: ./bluegreen-switch.sh [blue|green] [docker|k8s]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Funções de log
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Validar argumentos
TARGET_SLOT=${1:-""}
PLATFORM=${2:-"docker"}

if [[ ! "$TARGET_SLOT" =~ ^(blue|green)$ ]]; then
    echo "Uso: $0 [blue|green] [docker|k8s]"
    echo ""
    echo "Argumentos:"
    echo "  blue|green  - Ambiente para ativar"
    echo "  docker|k8s  - Plataforma (default: docker)"
    exit 1
fi

# ===========================================
# FUNÇÕES DOCKER
# ===========================================

get_current_slot_docker() {
    local config_file="$PROJECT_ROOT/traefik/dynamic/bluegreen.yml"
    if grep -q "weight: 100" "$config_file" 2>/dev/null; then
        if grep -B1 "weight: 100" "$config_file" | grep -q "blue"; then
            echo "blue"
        else
            echo "green"
        fi
    else
        echo "unknown"
    fi
}

switch_docker() {
    local target=$1
    local config_file="$PROJECT_ROOT/traefik/dynamic/bluegreen.yml"
    
    log_info "Switching Docker environment to: $target"
    
    # Verificar se o ambiente alvo está saudável
    local container="lms_books_$target"
    if ! docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null | grep -q "healthy"; then
        log_error "Container $container is not healthy. Aborting switch."
        exit 1
    fi
    
    # Atualizar configuração do Traefik
    if [ "$target" == "blue" ]; then
        sed -i 's/name: lms-books-blue/name: lms-books-blue/g; s/weight: [0-9]*/weight: 100/1; s/weight: [0-9]*/weight: 0/2' "$config_file"
    else
        sed -i 's/name: lms-books-blue/name: lms-books-blue/g; s/weight: [0-9]*/weight: 0/1; s/weight: [0-9]*/weight: 100/2' "$config_file"
    fi
    
    log_success "Switched to $target environment"
    log_info "Traefik will automatically reload the configuration"
}

health_check_docker() {
    local slot=$1
    local container="lms_books_$slot"
    local max_attempts=30
    local attempt=1
    
    log_info "Waiting for $container to be healthy..."
    
    while [ $attempt -le $max_attempts ]; do
        local status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "not_found")
        
        if [ "$status" == "healthy" ]; then
            log_success "$container is healthy"
            return 0
        fi
        
        log_info "Attempt $attempt/$max_attempts - Status: $status"
        sleep 5
        ((attempt++))
    done
    
    log_error "$container failed health check after $max_attempts attempts"
    return 1
}

# ===========================================
# FUNÇÕES KUBERNETES
# ===========================================

get_current_slot_k8s() {
    kubectl get svc lms-books-service -n lms-books -o jsonpath='{.spec.selector.slot}' 2>/dev/null || echo "unknown"
}

switch_k8s() {
    local target=$1
    
    log_info "Switching Kubernetes environment to: $target"
    
    # Verificar se o deployment alvo está pronto
    local ready=$(kubectl get deployment "lms-books-$target" -n lms-books -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
    local desired=$(kubectl get deployment "lms-books-$target" -n lms-books -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")
    
    if [ "$ready" != "$desired" ] || [ "$ready" == "0" ]; then
        log_error "Deployment lms-books-$target is not ready ($ready/$desired). Aborting switch."
        exit 1
    fi
    
    # Atualizar o Service para apontar para o novo slot
    kubectl patch svc lms-books-service -n lms-books -p "{\"spec\":{\"selector\":{\"slot\":\"$target\"}}}"
    
    log_success "Switched to $target environment"
}

health_check_k8s() {
    local slot=$1
    local max_attempts=30
    local attempt=1
    
    log_info "Waiting for lms-books-$slot deployment to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        local ready=$(kubectl get deployment "lms-books-$slot" -n lms-books -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
        local desired=$(kubectl get deployment "lms-books-$slot" -n lms-books -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")
        
        if [ "$ready" == "$desired" ] && [ "$ready" != "0" ]; then
            log_success "lms-books-$slot is ready ($ready/$desired replicas)"
            return 0
        fi
        
        log_info "Attempt $attempt/$max_attempts - Ready: $ready/$desired"
        sleep 5
        ((attempt++))
    done
    
    log_error "lms-books-$slot failed readiness check after $max_attempts attempts"
    return 1
}

# ===========================================
# MAIN
# ===========================================

log_info "Blue/Green Deployment Switch"
log_info "Target: $TARGET_SLOT | Platform: $PLATFORM"
echo ""

if [ "$PLATFORM" == "docker" ]; then
    CURRENT_SLOT=$(get_current_slot_docker)
    log_info "Current active slot: $CURRENT_SLOT"
    
    if [ "$CURRENT_SLOT" == "$TARGET_SLOT" ]; then
        log_warning "$TARGET_SLOT is already active. Nothing to do."
        exit 0
    fi
    
    health_check_docker "$TARGET_SLOT"
    switch_docker "$TARGET_SLOT"
    
elif [ "$PLATFORM" == "k8s" ]; then
    CURRENT_SLOT=$(get_current_slot_k8s)
    log_info "Current active slot: $CURRENT_SLOT"
    
    if [ "$CURRENT_SLOT" == "$TARGET_SLOT" ]; then
        log_warning "$TARGET_SLOT is already active. Nothing to do."
        exit 0
    fi
    
    health_check_k8s "$TARGET_SLOT"
    switch_k8s "$TARGET_SLOT"
    
else
    log_error "Unknown platform: $PLATFORM"
    exit 1
fi

echo ""
log_success "Blue/Green switch completed successfully!"
log_info "Previous: $CURRENT_SLOT -> New: $TARGET_SLOT"
