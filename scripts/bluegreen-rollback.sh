#!/bin/bash
# ===========================================
# Blue/Green Rollback Script
# ===========================================
# Este script permite fazer rollback rápido para o ambiente anterior
# Uso: ./bluegreen-rollback.sh [docker|k8s]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

PLATFORM=${1:-"docker"}

get_opposite_slot() {
    if [ "$1" == "blue" ]; then
        echo "green"
    else
        echo "blue"
    fi
}

# Docker rollback
if [ "$PLATFORM" == "docker" ]; then
    CONFIG_FILE="$PROJECT_ROOT/traefik/dynamic/bluegreen.yml"
    
    if grep -B1 "weight: 100" "$CONFIG_FILE" | grep -q "blue"; then
        CURRENT="blue"
    else
        CURRENT="green"
    fi
    
    TARGET=$(get_opposite_slot "$CURRENT")
    
    log_info "Current slot: $CURRENT"
    log_info "Rolling back to: $TARGET"
    
    # Executar switch
    "$SCRIPT_DIR/bluegreen-switch.sh" "$TARGET" docker
    
# Kubernetes rollback
elif [ "$PLATFORM" == "k8s" ]; then
    CURRENT=$(kubectl get svc lms-books-service -n lms-books -o jsonpath='{.spec.selector.slot}' 2>/dev/null || echo "unknown")
    TARGET=$(get_opposite_slot "$CURRENT")
    
    log_info "Current slot: $CURRENT"
    log_info "Rolling back to: $TARGET"
    
    # Executar switch
    "$SCRIPT_DIR/bluegreen-switch.sh" "$TARGET" k8s
    
else
    log_error "Unknown platform: $PLATFORM"
    exit 1
fi

log_success "Rollback completed!"
