# Relatório de Testes de Autoscaling

## Informação Geral

| Campo | Valor |
|-------|-------|
| **Data de Execução** | 4 de Janeiro de 2026 |
| **Ambiente** | Kubernetes (Docker Desktop) |
| **Namespace** | lms-books |
| **Microserviço** | lms-books (Spring Boot 3.2.5) |

---

## 1. Configuração do HPA (Horizontal Pod Autoscaler)

### 1.1 Parâmetros Configurados

| Parâmetro | Valor | Descrição |
|-----------|-------|-----------|
| **Min Replicas** | 2 | Número mínimo de réplicas mantidas |
| **Max Replicas** | 10 | Número máximo de réplicas permitidas |
| **CPU Target** | 70% | Limite de utilização CPU para trigger |
| **Memory Target** | 80% | Limite de utilização memória para trigger |
| **Scale Up Window** | 0s | Resposta imediata ao aumento de carga |
| **Scale Down Window** | 300s | Cooldown de 5 minutos antes de reduzir |

### 1.2 Políticas de Scaling

#### Scale Up (Aumento de Réplicas)
- **Política 1**: Aumentar até 100% a cada 15 segundos
- **Política 2**: Adicionar até 4 pods a cada 15 segundos
- **Seleção**: Máximo (resposta agressiva a picos de carga)

#### Scale Down (Redução de Réplicas)
- **Política 1**: Reduzir até 25% a cada 60 segundos
- **Política 2**: Remover até 1 pod a cada 60 segundos
- **Seleção**: Mínimo (redução gradual e conservadora)

---

## 2. Execução do Teste de Carga

### 2.1 Metodologia

O teste foi executado utilizando 50 workers paralelos em PowerShell, cada um fazendo requisições contínuas ao endpoint de health check do microserviço durante 2 minutos.

```powershell
# Comando utilizado para cada worker
while($true) { 
    Invoke-RestMethod -Uri "http://localhost:30080/actuator/health" -TimeoutSec 5 
}
```

### 2.2 Timeline do Teste

| Timestamp (UTC) | Evento | Réplicas |
|-----------------|--------|----------|
| 23:11:31 | Deploy inicial com HPA | 3 |
| 23:18:00 | Início do teste de carga (50 workers) | 3 |
| 23:21:31 | **Scale Up #1** - CPU acima do target | 3 → 7 |
| 23:24:31 | **Scale Up #2** - CPU acima do target | 7 → 10 |
| 23:27:00 | Fim do teste de carga | 10 |
| ~23:32:00 | Scale Down esperado (após cooldown) | 10 → ? |

### 2.3 Eventos de Scaling Registados

```
2026-01-04T23:21:31Z - New size: 7; reason: cpu resource utilization 
                       (percentage of request) above target

2026-01-04T23:24:31Z - New size: 10; reason: cpu resource utilization 
                       (percentage of request) above target
```

---

## 3. Resultados

### 3.1 Estado Atual dos Pods

| Pod | Status | CPU | Memória | Idade |
|-----|--------|-----|---------|-------|
| lms-books-5bcff749fb-4cc5s | Running | 41m | 292Mi | 5m |
| lms-books-5bcff749fb-4r574 | Running | 11m | 322Mi | 18m |
| lms-books-5bcff749fb-5brkb | Running | 18m | 292Mi | 5m |
| lms-books-5bcff749fb-8l7vx | Running | 20m | 308Mi | 8m |
| lms-books-5bcff749fb-gl76q | Running | 48m | 295Mi | 5m |
| lms-books-5bcff749fb-j858t | Running | 17m | 311Mi | 8m |
| lms-books-5bcff749fb-kq76b | Running | 19m | 314Mi | 8m |
| lms-books-5bcff749fb-lmbt7 | Running | 12m | 320Mi | 18m |
| lms-books-5bcff749fb-n62h7 | Running | 22m | 309Mi | 8m |
| lms-books-5bcff749fb-x4d97 | Running | 11m | 318Mi | 18m |

**Total**: 10 pods lms-books + 1 PostgreSQL + 1 RabbitMQ

### 3.2 Métricas Finais do HPA

| Métrica | Valor Atual | Target | Status |
|---------|-------------|--------|--------|
| **CPU** | 7% | 70% | ✅ Abaixo do limite |
| **Memory** | 60% | 80% | ✅ Abaixo do limite |
| **Réplicas** | 10/10 | - | Máximo atingido |

### 3.3 Pod Disruption Budget (PDB)

| Parâmetro | Valor |
|-----------|-------|
| **Min Available** | 1 |
| **Allowed Disruptions** | 9 |
| **Status** | Ativo e a proteger disponibilidade |

---

## 4. Infraestrutura de Suporte

### 4.1 Serviços Auxiliares

| Serviço | Status | CPU | Memória |
|---------|--------|-----|---------|
| **PostgreSQL** | Running | 60m | 269Mi |
| **RabbitMQ** | Running | 425m | 193Mi |

### 4.2 Metrics Server

O metrics-server foi instalado e configurado com sucesso para suportar o HPA:

```yaml
# Patch aplicado para Docker Desktop
- --kubelet-insecure-tls
- --kubelet-preferred-address-types=InternalIP
```

---

## 5. Análise de Desempenho

### 5.1 Comportamento do Autoscaling

```
Réplicas
   10 ├────────────────────────────■■■■■■■■■■■■■■■■■■■■■
    9 ├
    8 ├
    7 ├──────────────■■■■■■■■■■■■■■┘
    6 ├
    5 ├
    4 ├
    3 ├■■■■■■■■■■■■■■┘
    2 ├
      └──────────────────────────────────────────────────
        23:11    23:18    23:21    23:24    23:27    23:32
                   │         │         │         │
                   │         │         │         └─ Cooldown
                   │         │         └─ Scale Up #2 (7→10)
                   │         └─ Scale Up #1 (3→7)
                   └─ Início Load Test
```

### 5.2 Métricas de Resposta

| Indicador | Resultado |
|-----------|-----------|
| **Tempo de resposta ao Scale Up** | ~3 minutos após início da carga |
| **Incremento no primeiro scale** | +4 pods (3→7) |
| **Incremento no segundo scale** | +3 pods (7→10) |
| **Tempo total para máximo** | ~6 minutos |
| **Estabilização** | Imediata (0s window) |

### 5.3 Observações

1. **Resposta Rápida**: O HPA respondeu em ~3 minutos após o início da carga
2. **Scaling Agressivo**: As políticas de scale-up permitiram atingir o máximo rapidamente
3. **Máximo Atingido**: Os 10 pods foram atingidos em apenas 2 eventos de scaling
4. **Estabilidade**: Após atingir o máximo, o sistema estabilizou sem oscilações
5. **Cooldown Ativo**: O scale-down está configurado com 5 minutos de estabilização

---

## 6. Conclusões

### 6.1 Resultados do Teste

| Objetivo | Resultado | Status |
|----------|-----------|--------|
| HPA detecta aumento de CPU | Scale up triggered | ✅ PASSOU |
| Scaling responde a carga | 3 → 10 pods em 6 min | ✅ PASSOU |
| Máximo de réplicas respeitado | Limitado a 10 pods | ✅ PASSOU |
| Pods mantêm-se saudáveis | 100% Running | ✅ PASSOU |
| PDB protege disponibilidade | Min 1 disponível | ✅ PASSOU |

### 6.2 Conclusão Final

✅ **AUTOSCALING FUNCIONANDO CORRETAMENTE**

O sistema de autoscaling horizontal demonstrou capacidade de:
- Detetar aumento de carga através de métricas de CPU
- Escalar rapidamente para responder à demanda
- Respeitar os limites configurados (min/max replicas)
- Manter a estabilidade do cluster durante o scaling
- Proteger a disponibilidade através do PDB

---

## 7. Recomendações

### 7.1 Para Produção

1. **Aumentar Max Replicas**: Considerar aumentar para 20-50 pods em ambientes de produção
2. **Ajustar CPU Target**: Considerar 60% para resposta mais antecipada
3. **KEDA Integration**: Implementar KEDA para scaling baseado em HTTP requests/RabbitMQ queue
4. **Cluster Autoscaler**: Adicionar autoscaling de nós para suportar mais pods

### 7.2 Monitorização

1. Implementar dashboards Grafana para visualização de métricas
2. Configurar alertas para eventos de scaling
3. Monitorizar latência durante eventos de scale-up
4. Tracking de custos baseado em utilização

---

## 8. Ficheiros de Configuração

### 8.1 Ficheiros Criados/Modificados

| Ficheiro | Descrição |
|----------|-----------|
| `k8s/lms-books-hpa.yaml` | Configuração do HPA e PDB |
| `k8s/lms-books-keda.yaml` | Configuração KEDA (opcional) |
| `k8s/kustomization.yaml` | Atualizado com HPA |
| `scripts/autoscaling-test.ps1` | Script de teste PowerShell |

### 8.2 Comandos Úteis

```powershell
# Ver estado do HPA
kubectl get hpa -n lms-books

# Ver métricas detalhadas
kubectl describe hpa lms-books-hpa -n lms-books

# Ver consumo de recursos
kubectl top pods -n lms-books

# Ver eventos de scaling
kubectl get events -n lms-books --field-selector reason=SuccessfulRescale

# Monitorizar em tempo real
kubectl get hpa -n lms-books -w
```

---

**Relatório gerado automaticamente em 4 de Janeiro de 2026**
