# Relatório de Implementação Blue/Green Deployment
## Books Microservice - LMS System

**Data:** 4 de Janeiro de 2026  
**Versão:** v2.0.0 (GREEN)  
**Autor:** GitHub Copilot

---

## 1. Resumo Executivo

Este relatório documenta a implementação da estratégia de **Blue/Green Deployment** para o microserviço de Books, incluindo:

- ✅ Controlador GREEN com resposta de sucesso "GREEN"
- ✅ Modo de simulação de erro para teste de rollback
- ✅ Labels de release management
- ✅ Mecanismo de rollback automático baseado em testes

### Resultados dos Testes

| Métrica | Valor |
|---------|-------|
| **Total de Testes** | 251 |
| **Testes Passados** | 251 |
| **Testes Falhados** | 0 |
| **Testes Ignorados** | 0 |
| **Taxa de Sucesso** | 100% |
| **Tempo de Execução** | 1 min 36 seg |

---

## 2. Componentes Implementados

### 2.1 BookControllerGreen.java

Novo controlador ativado quando `deployment.slot=green`:

```java
@ConditionalOnProperty(name = "deployment.slot", havingValue = "green")
public class BookControllerGreen {
    
    @Value("${deployment.green.simulate-error:false}")
    private boolean simulateError;
    
    @Value("${deployment.green.version:unknown}")
    private String greenVersion;
}
```

#### Funcionalidades:

| Feature | Descrição |
|---------|-----------|
| **createFromJson()** | Cria livro com resposta "GREEN" |
| **Simulate Error Mode** | Flag para simular erros e testar rollback |
| **Health Check** | Endpoint `/health` com status GREEN |
| **Deployment Info** | Endpoint `/deployment-info` com detalhes |

#### Exemplo de Resposta GREEN (Sucesso):

```json
{
    "book": {
        "isbn": "9780134685991",
        "title": "Test Book",
        "genre": "Fiction"
    },
    "deploymentSlot": "GREEN",
    "deploymentVersion": "v2.0.0",
    "message": "Book created successfully in GREEN deployment"
}
```

#### Exemplo de Resposta GREEN (Erro Simulado):

```json
{
    "status": 500,
    "error": "Internal Server Error",
    "message": "[GREEN ERROR] Simulated error for automatic rollback testing. Version: v2.0.0. Set deployment.green.simulate-error=false to disable."
}
```

### 2.2 BookController.java (BLUE - Modificado)

```java
@ConditionalOnProperty(name = "deployment.slot", havingValue = "blue", matchIfMissing = true)
public class BookController {
    // Controlador BLUE (padrão)
}
```

### 2.3 Labels de Release Management

#### Em Docker:

```yaml
labels:
  - "deployment.slot=green"
  - "deployment.version=${GREEN_VERSION:-v2.0.0}"
  - "deployment.release-strategy=blue-green"
  - "deployment.rollback-enabled=true"
  - "deployment.rollback-target=blue"
```

#### Em Kubernetes:

```yaml
metadata:
  labels:
    app: lms-books
    slot: green
    version: "${GREEN_VERSION}"
```

#### Headers HTTP:

```
X-Deployment-Slot: green
X-Deployment-Version: v2.0.0
X-Rollback-Enabled: true
```

---

## 3. Profiles de Configuração

### 3.1 application-green.properties (Normal)

```properties
deployment.slot=green
deployment.green.version=${GREEN_VERSION:v2.0.0}
deployment.green.simulate-error=false
deployment.rollback.enabled=true
server.port=8091
```

### 3.2 application-green-error.properties (Simulação de Erro)

```properties
deployment.slot=green
deployment.green.version=${GREEN_VERSION:v2.0.0-error}
deployment.green.simulate-error=true  # ATIVA ERROS
deployment.rollback.enabled=true
server.port=8091
```

### 3.3 application-blue.properties

```properties
deployment.slot=blue
deployment.blue.version=${BLUE_VERSION:v1.0.0}
deployment.rollback.enabled=true
server.port=8090
```

---

## 4. Mecanismo de Rollback Automático

### 4.1 Fluxo de Rollback

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Deploy GREEN   │───▶│  Smoke Tests    │───▶│  Switch Traffic │
└─────────────────┘    └────────┬────────┘    └─────────────────┘
                                │
                          ┌─────▼─────┐
                          │  Falha?   │
                          └─────┬─────┘
                                │ Sim
                       ┌────────▼────────┐
                       │ ROLLBACK BLUE   │
                       └─────────────────┘
```

### 4.2 Triggers de Rollback

| Trigger | Descrição |
|---------|-----------|
| `smoke-test-failure` | Testes de smoke falham (< 80% sucesso) |
| `health-check-failure` | Endpoint /health retorna UNHEALTHY |
| `exception` | Erros HTTP 500 durante testes |

### 4.3 Script de Smoke Test (smoke-test-rollback.sh)

```bash
# Testes executados:
1. test_health_endpoint      # Verifica /api/books/health
2. test_deployment_info      # Verifica /api/books/deployment-info
3. test_response_headers     # Verifica X-Deployment-Slot header
4. test_create_book          # Testa criação de livro
5. test_get_books            # Testa listagem de livros

# Threshold de sucesso: 80%
# Se < 80%: ROLLBACK AUTOMÁTICO
```

### 4.4 Script PowerShell (bluegreen-test.ps1)

```powershell
# Deploy GREEN normal
.\scripts\bluegreen-test.ps1 -Action deploy -Slot green

# Deploy GREEN com erro (testa rollback)
.\scripts\bluegreen-test.ps1 -Action deploy -Slot green -SimulateError

# Rollback manual
.\scripts\bluegreen-test.ps1 -Action rollback

# Ver status
.\scripts\bluegreen-test.ps1 -Action status
```

---

## 5. Resultados dos Testes Unitários

### 5.1 Por Categoria

| Categoria | Testes | Tempo |
|-----------|--------|-------|
| BookControllerIntegrationTest | 6 | 1.1s |
| BookViewMapperTest | 5 | 3.3s |
| BookEventsListenerIntegrationTest | 9 | 7.8s |
| BookTest (Model) | 31 | 0.3s |
| DescriptionTest | 10 | 0.1s |
| IsbnTest | 26 | 0.9s |
| TitleTest | 19 | 0.1s |
| BookRepositoryIntegrationTest | 9 | 2.5s |
| BookServiceImplTest | 14 | 1.4s |
| RabbitmqConfigTest | 6 | 0.1s |
| GenreControllerIntegrationTest | 5 | 0.5s |
| GenreTest | 15 | 0.1s |
| Performance Tests | 27 | 0.3s |
| Outros | 69 | - |

### 5.2 Cobertura de Código

```
[INFO] Analyzed bundle 'lms-books' with 117 classes
```

---

## 6. Configuração de Infraestrutura

### 6.1 Docker Compose (docker-compose.bluegreen.yml)

| Serviço | Porta | Slot |
|---------|-------|------|
| lms-books-blue | 8090 | blue |
| lms-books-green | 8091 | green |
| traefik | 80, 8080 | - |
| postgres_books_prod | 5432 | - |
| rabbitmq_prod | 5672, 15672 | - |

### 6.2 URLs de Acesso

| Ambiente | URL |
|----------|-----|
| BLUE direto | http://blue.lms-books.localhost/api/books |
| GREEN direto | http://green.lms-books.localhost/api/books |
| Produção | http://lms-books.localhost/api/books |
| Traefik Dashboard | http://traefik.localhost:8080 |

---

## 7. Jenkinsfile.bluegreen

### 7.1 Parâmetros

| Parâmetro | Tipo | Valores |
|-----------|------|---------|
| DEPLOYMENT_SLOT | choice | green, blue |
| SIMULATE_ERROR | boolean | true, false |
| SKIP_SMOKE_TESTS | boolean | true, false |
| PLATFORM | choice | docker, k8s |

### 7.2 Stages

```
1. Checkout
2. Build & Unit Tests
3. Package Application
4. Build Docker Image (com labels)
5. Push Docker Image
6. Deploy to Target Slot
7. Health Check
8. Smoke Tests
9. Switch Traffic to GREEN (se sucesso)
10. Automatic Rollback (se falha)
11. Post-Deployment Verification
```

---

## 8. Como Usar

### 8.1 Deploy Normal (GREEN)

```bash
# Via Docker Compose
GREEN_VERSION=v2.0.0 docker compose -f docker-compose.bluegreen.yml up -d lms-books-green

# Via PowerShell
.\scripts\bluegreen-test.ps1 -Action deploy -Slot green
```

### 8.2 Testar Rollback Automático

```bash
# Ativar simulação de erro
SIMULATE_ERROR=true docker compose -f docker-compose.bluegreen.yml up -d lms-books-green

# Via PowerShell
.\scripts\bluegreen-test.ps1 -Action deploy -Slot green -SimulateError

# Os smoke tests vão falhar e triggerar rollback automático
```

### 8.3 Switch Manual de Tráfego

```bash
# Para GREEN
.\scripts\bluegreen-switch.sh green docker

# Para BLUE
.\scripts\bluegreen-switch.sh blue docker
```

---

## 9. Conclusões

### ✅ Objetivos Alcançados

1. **Versão GREEN implementada** com mensagem de sucesso "GREEN" em todas as respostas
2. **Modo de erro simulado** para testar mecanismo de rollback
3. **Labels de release** em Docker, Kubernetes e headers HTTP
4. **Rollback automático** baseado em smoke tests com threshold de 80%
5. **Todos os 251 testes passaram** sem falhas

### 📊 Métricas de Qualidade

| Métrica | Valor |
|---------|-------|
| Testes Unitários | 251 (100% pass) |
| Classes Analisadas | 117 |
| Tempo de Build | 1 min 36 seg |
| Profiles Configurados | 3 (blue, green, green-error) |

### 🔄 Próximos Passos Recomendados

1. Configurar pipeline CI/CD com Jenkinsfile.bluegreen
2. Implementar monitorização com Prometheus/Grafana
3. Adicionar testes de carga no smoke test
4. Configurar alertas para rollback automático

---

## 10. Ficheiros Criados/Modificados

| Ficheiro | Ação | Descrição |
|----------|------|-----------|
| BookControllerGreen.java | Criado | Controller GREEN com simulação de erro |
| BookController.java | Modificado | Adicionado ConditionalOnProperty |
| application-green.properties | Criado | Config GREEN normal |
| application-green-error.properties | Criado | Config GREEN com erro |
| application-blue.properties | Criado | Config BLUE |
| smoke-test-rollback.sh | Criado | Smoke tests com rollback |
| bluegreen-test.ps1 | Criado | Script PowerShell para Windows |
| Jenkinsfile.bluegreen | Criado | Pipeline CI/CD |
| docker-compose.bluegreen.yml | Modificado | Labels e portas separadas |

---

**Relatório gerado automaticamente em 4 de Janeiro de 2026**
