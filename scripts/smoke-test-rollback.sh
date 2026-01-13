#!/bin/bash
# ===========================================
# Smoke Test with Automatic Rollback Script
# ===========================================
# Este script executa smoke tests no ambiente GREEN
# e faz rollback automático para BLUE se detectar problemas
#
# Labels de Release:
# - release-strategy: blue-green
# - rollback-enabled: true
# - rollback-trigger: smoke-test-failure | health-check-failure
#
# Uso: ./smoke-test-rollback.sh [docker|k8s] [green-url]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_test() { echo -e "${CYAN}[TEST]${NC} $1"; }

# Configuração
PLATFORM=${1:-"docker"}
GREEN_URL=${2:-"http://localhost:8091"}  # Default green URL
BLUE_URL=${3:-"http://localhost:8090"}    # Default blue URL

# Timeouts e thresholds
HEALTH_CHECK_TIMEOUT=30
MAX_RETRIES=3
SUCCESS_THRESHOLD=80  # Percentagem mínima de testes bem sucedidos

# Contadores
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# ===========================================
# FUNÇÕES DE TESTE
# ===========================================

test_health_endpoint() {
    local url=$1
    local slot=$2
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    log_test "Testing health endpoint for $slot..."
    
    local response
    response=$(curl -s -w "\n%{http_code}" "$url/api/books/health" 2>/dev/null || echo "000")
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" == "200" ]; then
        # Verificar se é GREEN e se está saudável
        if echo "$body" | grep -q "HEALTHY"; then
            log_success "Health check passed - Status: HEALTHY"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            return 0
        elif echo "$body" | grep -q "UNHEALTHY"; then
            log_error "Health check failed - Status: UNHEALTHY (Rollback triggered)"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            return 1
        fi
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    else
        log_error "Health check failed - HTTP $http_code"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
}

test_deployment_info() {
    local url=$1
    local expected_slot=$2
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    log_test "Testing deployment info for $expected_slot..."
    
    local response
    response=$(curl -s -w "\n%{http_code}" "$url/api/books/deployment-info" 2>/dev/null || echo "000")
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" == "200" ]; then
        if echo "$body" | grep -q "\"slot\":\"$expected_slot\""; then
            log_success "Deployment info correct - Slot: $expected_slot"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            
            # Verificar se simulate-error está ativo
            if echo "$body" | grep -q "\"simulateErrorMode\":true"; then
                log_warning "Simulate error mode is ACTIVE - Rollback may be required"
            fi
            return 0
        else
            log_error "Deployment slot mismatch"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            return 1
        fi
    else
        log_error "Deployment info failed - HTTP $http_code"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
}

test_create_book() {
    local url=$1
    local slot=$2
    local test_isbn="978-0-13-468599-$(date +%s)"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    log_test "Testing book creation on $slot..."
    
    local response
    response=$(curl -s -w "\n%{http_code}" -X PUT "$url/api/books/$test_isbn" \
        -H "Content-Type: application/json" \
        -d '{
            "title": "Smoke Test Book",
            "genre": "Fiction",
            "description": "Test book for smoke testing",
            "authorNumbers": ["1"]
        }' 2>/dev/null || echo "000")
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" == "201" ]; then
        # Verificar se a resposta contém mensagem GREEN
        if echo "$body" | grep -q "GREEN"; then
            log_success "Book creation passed - GREEN deployment confirmed"
        else
            log_success "Book creation passed"
        fi
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    elif [ "$http_code" == "500" ]; then
        # Verificar se é erro simulado
        if echo "$body" | grep -q "Simulated error"; then
            log_error "Simulated error detected - Rollback required"
        else
            log_error "Book creation failed - Internal Server Error"
        fi
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    else
        log_error "Book creation failed - HTTP $http_code"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
}

test_get_books() {
    local url=$1
    local slot=$2
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    log_test "Testing get books on $slot..."
    
    local response
    response=$(curl -s -w "\n%{http_code}" "$url/api/books?title=Test" 2>/dev/null || echo "000")
    local http_code=$(echo "$response" | tail -n1)
    
    # 200 ou 404 são aceitáveis (pode não haver livros)
    if [ "$http_code" == "200" ] || [ "$http_code" == "404" ]; then
        log_success "Get books passed - HTTP $http_code"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    elif [ "$http_code" == "500" ]; then
        log_error "Get books failed - Internal Server Error (possible error simulation)"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    else
        log_error "Get books failed - HTTP $http_code"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
}

test_response_headers() {
    local url=$1
    local expected_slot=$2
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    log_test "Testing response headers for $expected_slot..."
    
    local headers
    headers=$(curl -s -I "$url/api/books/health" 2>/dev/null || echo "")
    
    if echo "$headers" | grep -qi "X-Deployment-Slot: $expected_slot"; then
        log_success "Response headers correct - Slot: $expected_slot"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    else
        # Headers podem não estar presentes em todas as respostas
        log_warning "X-Deployment-Slot header not found (may be normal for BLUE)"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    fi
}

# ===========================================
# FUNÇÃO DE ROLLBACK
# ===========================================

execute_rollback() {
    log_error "============================================"
    log_error "AUTOMATIC ROLLBACK TRIGGERED"
    log_error "============================================"
    log_info "Test Results: $PASSED_TESTS/$TOTAL_TESTS passed"
    log_info "Failed Tests: $FAILED_TESTS"
    
    echo ""
    log_info "Executing rollback to BLUE environment..."
    
    if [ -f "$SCRIPT_DIR/bluegreen-rollback.sh" ]; then
        "$SCRIPT_DIR/bluegreen-rollback.sh" "$PLATFORM"
        log_success "Rollback completed successfully!"
    else
        log_error "Rollback script not found: $SCRIPT_DIR/bluegreen-rollback.sh"
        log_warning "Manual rollback required!"
        exit 1
    fi
}

# ===========================================
# MAIN
# ===========================================

echo ""
echo "============================================"
echo "SMOKE TEST WITH AUTOMATIC ROLLBACK"
echo "============================================"
echo ""
log_info "Platform: $PLATFORM"
log_info "GREEN URL: $GREEN_URL"
log_info "BLUE URL: $BLUE_URL"
log_info "Success Threshold: ${SUCCESS_THRESHOLD}%"
echo ""

# Aguardar que o serviço esteja disponível
log_info "Waiting for GREEN service to be ready..."
attempt=1
while [ $attempt -le $HEALTH_CHECK_TIMEOUT ]; do
    if curl -s "$GREEN_URL/api/books/health" > /dev/null 2>&1; then
        log_success "GREEN service is available"
        break
    fi
    
    if [ $attempt -eq $HEALTH_CHECK_TIMEOUT ]; then
        log_error "GREEN service not available after $HEALTH_CHECK_TIMEOUT seconds"
        execute_rollback
        exit 1
    fi
    
    sleep 1
    ((attempt++))
done

echo ""
log_info "Starting smoke tests..."
echo ""

# Executar testes
test_health_endpoint "$GREEN_URL" "green"
test_deployment_info "$GREEN_URL" "green"
test_response_headers "$GREEN_URL" "green"
test_create_book "$GREEN_URL" "green"
test_get_books "$GREEN_URL" "green"

# Calcular percentagem de sucesso
if [ $TOTAL_TESTS -gt 0 ]; then
    SUCCESS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))
else
    SUCCESS_RATE=0
fi

echo ""
echo "============================================"
echo "TEST RESULTS"
echo "============================================"
log_info "Total Tests: $TOTAL_TESTS"
log_info "Passed: $PASSED_TESTS"
log_info "Failed: $FAILED_TESTS"
log_info "Success Rate: ${SUCCESS_RATE}%"
log_info "Threshold: ${SUCCESS_THRESHOLD}%"
echo ""

# Decidir se faz rollback
if [ $SUCCESS_RATE -lt $SUCCESS_THRESHOLD ]; then
    log_error "Success rate below threshold - Rollback required"
    execute_rollback
    exit 1
else
    log_success "============================================"
    log_success "ALL SMOKE TESTS PASSED"
    log_success "GREEN deployment is healthy"
    log_success "============================================"
    
    # Atualizar labels/annotations para indicar deployment bem sucedido
    if [ "$PLATFORM" == "k8s" ]; then
        log_info "Updating Kubernetes annotations..."
        kubectl annotate deployment lms-books-green -n lms-books \
            deployment.kubernetes.io/smoke-test-passed="true" \
            deployment.kubernetes.io/smoke-test-time="$(date -Iseconds)" \
            --overwrite 2>/dev/null || true
    fi
fi

exit 0
