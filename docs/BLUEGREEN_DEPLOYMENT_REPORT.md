# Relatório de Implementação: Blue/Green Deployment Strategy

**Data:** 4 de Janeiro de 2026  
**Projeto:** LMS Books Microservice  
**Autor:** GitHub Copilot  

---

## 1. Sumário Executivo

Este relatório documenta a implementação completa da estratégia de deployment **Blue/Green** para o microserviço LMS Books. A implementação abrange tanto ambientes Docker (staging) como Kubernetes (produção), incluindo automação via Jenkins Pipeline, scripts de gestão e configuração de routing dinâmico com Traefik.

---

## 2. Objetivos

- **Zero Downtime Deployment**: Eliminar interrupções de serviço durante deployments
- **Rollback Instantâneo**: Capacidade de reverter para a versão anterior em segundos
- **Validação Pré-Switch**: Testar a nova versão antes de receber tráfego de produção
- **Automação Completa**: Pipeline CI/CD totalmente automatizada

---

## 3. Arquitetura Blue/Green

### 3.1 Conceito

```
                    ┌─────────────────┐
                    │    Traefik      │
                    │  Load Balancer  │
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
    ┌─────────────────┐           ┌─────────────────┐
    │   BLUE Slot     │           │   GREEN Slot    │
    │   (Active)      │           │   (Inactive)    │
    │                 │           │                 │
    │  lms-books:v1   │           │  lms-books:v2   │
    └─────────────────┘           └─────────────────┘
              │                             │
              └──────────────┬──────────────┘
                             │
                    ┌────────┴────────┐
                    │   PostgreSQL    │
                    │   RabbitMQ      │
                    │   (Shared)      │
                    └─────────────────┘
```

### 3.2 Fluxo de Deployment

1. **Identificação**: Determinar qual slot está ativo (Blue ou Green)
2. **Deploy**: Fazer deploy da nova versão no slot inativo
3. **Health Check**: Verificar se a nova versão está saudável
4. **Smoke Tests**: Executar testes básicos contra o novo ambiente
5. **Switch**: Redirecionar o tráfego para o novo slot
6. **Verificação**: Confirmar que o tráfego está a fluir corretamente
7. **Cleanup**: Manter o slot antigo para rollback rápido

---

## 4. Ficheiros Criados/Modificados

### 4.1 Docker Compose Blue/Green

**Ficheiro:** `docker-compose.bluegreen.yml`

```yaml
# Estrutura principal:
services:
  # Infraestrutura partilhada
  - postgres_books_prod      # Base de dados PostgreSQL
  - rabbitmq_prod            # Message broker RabbitMQ
  - traefik                  # Reverse proxy / Load balancer
  
  # Ambientes Blue/Green
  - lms-books-blue           # Slot Blue
  - lms-books-green          # Slot Green
```

**Características:**
- Traefik como reverse proxy com configuração dinâmica
- Health checks para todos os containers
- Labels Traefik para routing automático
- Variáveis de ambiente para versionamento (`BLUE_VERSION`, `GREEN_VERSION`)
- Rede isolada `lms_bluegreen_network`

### 4.2 Configuração Dinâmica Traefik

**Ficheiro:** `traefik/dynamic/bluegreen.yml`

```yaml
http:
  services:
    lms-books-weighted:
      weighted:
        services:
          - name: lms-books-blue
            weight: 100  # Blue ativo
          - name: lms-books-green
            weight: 0    # Green inativo
```

**Funcionalidades:**
- **Weighted routing**: Controlo percentual do tráfego
- **Sticky sessions**: Consistência de sessão via cookie
- **Health checks**: Verificação contínua de cada backend
- **Hot reload**: Alterações aplicadas sem restart

### 4.3 Kubernetes Blue/Green

**Ficheiro:** `k8s/lms-books-bluegreen.yaml`

```yaml
# Recursos criados:
- Deployment: lms-books-blue      # 3 replicas
- Deployment: lms-books-green     # 3 replicas
- Service: lms-books-service      # Service principal (selector: slot)
- Service: lms-books-blue-service # Acesso direto ao Blue
- Service: lms-books-green-service# Acesso direto ao Green
- IngressRoute: Traefik routing
```

**Características:**
- Probes HTTP para liveness e readiness
- Resource limits e requests definidos
- Init containers para aguardar dependências
- Labels para identificação de slot e versão

### 4.4 Scripts de Gestão

#### 4.4.1 Switch Script

**Ficheiro:** `scripts/bluegreen-switch.sh`

```bash
# Uso:
./bluegreen-switch.sh [blue|green] [docker|k8s]

# Funcionalidades:
- Verificação de health antes do switch
- Suporte para Docker e Kubernetes
- Logging colorido
- Validação de argumentos
```

#### 4.4.2 Rollback Script

**Ficheiro:** `scripts/bluegreen-rollback.sh`

```bash
# Uso:
./bluegreen-rollback.sh [docker|k8s]

# Funcionalidades:
- Identificação automática do slot anterior
- Execução do switch reverso
- Suporte para Docker e Kubernetes
```

### 4.5 Jenkins Pipeline

**Ficheiro:** `Jenkinsfile`

#### Stages para Staging (Docker):

| Stage | Descrição |
|-------|-----------|
| `Determine Target Slot (Staging)` | Identifica slot inativo |
| `Build Docker Image (Staging)` | Cria imagem com tag única |
| `Deploy to Inactive Slot (Staging)` | Deploy no slot inativo |
| `Health Check (Staging)` | Aguarda container healthy |
| `Smoke Tests (Staging)` | Testes básicos |
| `Switch Traffic (Staging)` | Alterna tráfego |
| `Verify Deployment (Staging)` | Confirmação final |

#### Stages para Production (Kubernetes):

| Stage | Descrição |
|-------|-----------|
| `Determine Target Slot (Production)` | Identifica slot via kubectl |
| `Build & Push Docker Image (Production)` | Push para registry |
| `Deploy to Inactive Slot (Production)` | kubectl set image |
| `Health Check (Production)` | Verifica replicas ready |
| `Smoke Tests (Production)` | Testes no cluster |
| `Approval for Traffic Switch` | Aprovação manual |
| `Switch Traffic (Production)` | kubectl patch service |
| `Verify Deployment (Production)` | Confirmação final |

---

## 5. Variáveis de Ambiente

| Variável | Descrição | Valor Default |
|----------|-----------|---------------|
| `DOCKER_REGISTRY` | Registry Docker | `localhost:5000` |
| `BLUE_VERSION` | Versão do slot Blue | `latest` |
| `GREEN_VERSION` | Versão do slot Green | `latest` |
| `BUILD_VERSION` | Versão do build | `BUILD_NUMBER-GIT_COMMIT` |
| `CURRENT_SLOT` | Slot atualmente ativo | Determinado dinamicamente |
| `TARGET_SLOT` | Slot alvo do deployment | Oposto do CURRENT_SLOT |

---

## 6. Endpoints de Acesso

### Docker (Staging)

| Endpoint | Descrição |
|----------|-----------|
| `http://lms-books.localhost` | Produção (slot ativo) |
| `http://blue.lms-books.localhost` | Acesso direto Blue |
| `http://green.lms-books.localhost` | Acesso direto Green |
| `http://traefik.localhost` | Dashboard Traefik |

### Kubernetes (Production)

| Service | Descrição |
|---------|-----------|
| `lms-books-service:8081` | Produção (slot ativo) |
| `lms-books-blue-service:8081` | Acesso direto Blue |
| `lms-books-green-service:8081` | Acesso direto Green |

---

## 7. Procedimentos Operacionais

### 7.1 Switch Manual (Docker)

```bash
# Verificar slot atual
grep -B1 "weight: 100" traefik/dynamic/bluegreen.yml

# Switch para Green
./scripts/bluegreen-switch.sh green docker

# Switch para Blue
./scripts/bluegreen-switch.sh blue docker
```

### 7.2 Switch Manual (Kubernetes)

```bash
# Verificar slot atual
kubectl get svc lms-books-service -n lms-books -o jsonpath='{.spec.selector.slot}'

# Switch para Green
kubectl patch svc lms-books-service -n lms-books -p '{"spec":{"selector":{"slot":"green"}}}'

# Switch para Blue
kubectl patch svc lms-books-service -n lms-books -p '{"spec":{"selector":{"slot":"blue"}}}'
```

### 7.3 Rollback de Emergência

```bash
# Docker
./scripts/bluegreen-rollback.sh docker

# Kubernetes
./scripts/bluegreen-rollback.sh k8s
```

---

## 8. Rollback Automático

A pipeline inclui rollback automático em caso de falha:

```groovy
post {
    failure {
        script {
            if (env.CURRENT_SLOT && env.TARGET_SLOT) {
                // Reverter para slot anterior
                if (env.BRANCH_NAME == 'staging') {
                    sh "./scripts/bluegreen-rollback.sh docker"
                } else if (env.BRANCH_NAME == 'production') {
                    sh "kubectl patch svc lms-books-service -n lms-books -p '{\"spec\":{\"selector\":{\"slot\":\"${env.CURRENT_SLOT}\"}}}'"
                }
            }
        }
    }
}
```

---

## 9. Plugins Jenkins Necessários

| Plugin | Função |
|--------|--------|
| JUnit Plugin | Publicar resultados de testes |
| JaCoCo Plugin | Relatórios de cobertura |
| HTML Publisher Plugin | Publicar relatórios HTML |
| Pipeline Utility Steps | Funcionalidades adicionais |
| Kubernetes CLI | Comandos kubectl |
| Docker Pipeline | Operações Docker |

---

## 10. Benefícios da Implementação

### 10.1 Operacionais

- ✅ **Zero Downtime**: Sem interrupção durante deployments
- ✅ **Rollback Instantâneo**: Reversão em segundos
- ✅ **Validação Prévia**: Testes antes do switch de tráfego
- ✅ **Ambientes Idênticos**: Blue e Green são réplicas exatas

### 10.2 Desenvolvimento

- ✅ **Pipeline Automatizada**: CI/CD completo
- ✅ **Testes Integrados**: Unit, Integration, Smoke tests
- ✅ **Aprovação Manual**: Gate para produção
- ✅ **Versionamento**: Cada build tem versão única

### 10.3 Monitorização

- ✅ **Health Checks**: Verificação contínua
- ✅ **Logging**: Output detalhado na pipeline
- ✅ **Métricas**: Dashboard Traefik disponível

---

## 11. Considerações de Segurança

- Secrets geridos via Kubernetes Secrets ou variáveis de ambiente Jenkins
- Comunicação interna via rede isolada
- Aprovação manual obrigatória para produção
- Rollback automático em caso de falha

---

## 12. Próximos Passos Recomendados

1. **Canary Deployment**: Implementar release gradual (10% → 50% → 100%)
2. **Observabilidade**: Integrar Prometheus/Grafana para métricas
3. **Database Migrations**: Estratégia para migrações compatíveis com Blue/Green
4. **Feature Flags**: Controlo granular de funcionalidades
5. **Chaos Engineering**: Testes de resiliência

---

## 13. Conclusão

A implementação da estratégia Blue/Green Deployment proporciona uma base sólida para deployments seguros e reversíveis. A automação via Jenkins Pipeline, combinada com scripts de gestão e configuração de routing dinâmico, permite operações de deployment com zero downtime e capacidade de rollback instantâneo.

---

**Ficheiros Criados:**
- `docker-compose.bluegreen.yml`
- `traefik/dynamic/bluegreen.yml`
- `k8s/lms-books-bluegreen.yaml`
- `scripts/bluegreen-switch.sh`
- `scripts/bluegreen-rollback.sh`

**Ficheiros Modificados:**
- `Jenkinsfile`
