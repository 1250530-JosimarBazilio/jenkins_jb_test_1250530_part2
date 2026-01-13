# BOOKS Microservice - Performance & Load Tests Plan

## Overview

This document outlines the **performance and load testing strategy** for the BOOKS microservice, with focus on:

1. **FR-A Testing**: Create Book + Author + Genre in the same process (Saga pattern)
2. **Kubernetes Deployment Testing**: Scalability, load balancing, pod behavior
3. **Comparison with Monolith**: Response times, throughput, resource usage

**Evaluation Criteria 2.4**: Performance testing comparing microservice vs monolith (30% weight)

---

## Test Environment Requirements

### Local/Development

| Component | Specification |
|-----------|---------------|
| BOOKS Microservice | Port 8081 |
| Monolith (PART-2) | Port 8080 |
| PostgreSQL | Port 5432 |
| RabbitMQ | Port 5672 / 15672 |

### Kubernetes (Minikube/Docker Desktop)

| Component | Replicas | Resources |
|-----------|----------|-----------|
| lms-books | 2-3 pods | 512Mi-1Gi memory, 250m-500m CPU |
| postgres | 1 pod | Persistent storage |
| rabbitmq | 1 pod | - |
| traefik | 1 pod | Load balancer/ingress |

---

## Test Scenarios

### Scenario 1: FR-A - Create Book with Author and Genre (Saga)

**Objective**: Test the Saga orchestrator that creates Book + validates Author + validates Genre atomically.

**Endpoint**: `PUT /api/books/{isbn}`

**Request Payload**:
```json
{
  "title": "Test Book Title",
  "genre": "Ficção",
  "authors": ["Manuel Teixeira Gomes"],
  "description": "Performance test book"
}
```

**Saga Steps Measured**:
1. `ValidateBookStep` - ISBN uniqueness, genre existence, authors validity
2. `CreateBookStep` - Book entity creation
3. `PublishBookCreatedEventStep` - Event to Outbox

**Metrics to Capture**:
| Metric | Target (Single Pod) | Target (3 Pods) |
|--------|---------------------|-----------------|
| Response Time (avg) | < 200ms | < 150ms |
| Response Time (p95) | < 500ms | < 300ms |
| Response Time (p99) | < 1000ms | < 500ms |
| Throughput | 50 req/s | 150 req/s |
| Error Rate | < 1% | < 1% |

---

### Scenario 2: Book Retrieval (Read Performance)

**Objective**: Test read performance under load.

**Endpoint**: `GET /api/books/{isbn}`

**Metrics to Capture**:
| Metric | Target |
|--------|--------|
| Response Time (avg) | < 50ms |
| Response Time (p95) | < 100ms |
| Throughput | 500 req/s |
| Cache Hit Rate | > 80% (if CQRS cache active) |

---

### Scenario 3: Concurrent Users Stress Test

**Objective**: Find the breaking point of the service.

**Ramp-Up Strategy**:
```
Stage 1:  10 users  → 2 minutes  (warmup)
Stage 2:  50 users  → 3 minutes  (normal load)
Stage 3: 100 users  → 3 minutes  (high load)
Stage 4: 200 users  → 3 minutes  (stress)
Stage 5: 500 users  → 2 minutes  (breaking point)
```

**Metrics to Capture**:
- Response time degradation curve
- Error rate at each stage
- Pod CPU/Memory usage
- Auto-scaling trigger points (if HPA configured)

---

### Scenario 4: Kubernetes Pod Scaling Test

**Objective**: Test horizontal scaling behavior under load.

**Test Setup**:
1. Start with 1 replica
2. Apply load (100 concurrent users)
3. Scale to 3 replicas (manual or HPA)
4. Observe load distribution

**Metrics to Capture**:
| Metric | Before Scaling | After Scaling |
|--------|----------------|---------------|
| Avg Response Time | ? | Should decrease ~60% |
| Throughput | ? | Should increase ~3x |
| Error Rate | ? | Should decrease |
| Pod CPU Usage | ? | Should distribute |

---

### Scenario 5: Pod Failure Recovery Test

**Objective**: Test resilience when a pod fails.

**Test Steps**:
1. Run constant load (50 users)
2. Kill one pod: `kubectl delete pod lms-books-xxx --force`
3. Measure recovery time
4. Verify no data loss (Outbox pattern)

**Metrics to Capture**:
- Time to detect failure
- Time to route traffic to remaining pods
- Number of failed requests during recovery
- Event consistency (Outbox replay)

---

### Scenario 6: Database Sync Performance (Multi-Instance)

**Objective**: Test database synchronization across multiple BOOKS instances.

**Test Steps**:
1. Run 3 replicas
2. Create book on Instance A
3. Measure time until book is readable on Instance B and C

**Metrics**:
- Sync latency: < 100ms (target)
- Event delivery: 100% (via RabbitMQ fanout)

---

## Performance Test Tools

### Option A: JMeter (Recommended)

**Installation**:
```powershell
choco install jmeter
# Or download from: https://jmeter.apache.org/download_jmeter.cgi
```

**Test Plan Structure**:
```
BOOKS_Performance_Test.jmx
├── Thread Group (Users)
│   ├── HTTP Request Defaults (baseUrl: http://localhost:8081)
│   ├── CSV Data Set Config (books.csv - ISBNs, titles, etc.)
│   ├── Create Book Request (PUT /api/books/{isbn})
│   │   └── JSON Extractor (version, isbn)
│   ├── Get Book Request (GET /api/books/{isbn})
│   └── Update Book Request (PATCH /api/books/{isbn})
├── Aggregate Report
├── Response Time Graph
└── Transactions Per Second
```

### Option B: k6 (Modern, Scriptable)

**Installation**:
```powershell
choco install k6
# Or download from: https://k6.io/docs/get-started/installation/
```

**Sample k6 Script** (`books-load-test.js`):
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// Custom metrics
const bookCreationTime = new Trend('book_creation_time');
const sagaExecutionTime = new Trend('saga_execution_time');
const errors = new Counter('errors');

// Test configuration
export const options = {
  stages: [
    { duration: '2m', target: 10 },   // Warmup
    { duration: '3m', target: 50 },   // Normal load
    { duration: '3m', target: 100 },  // High load
    { duration: '3m', target: 200 },  // Stress
    { duration: '2m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests < 500ms
    errors: ['count<100'],              // Less than 100 errors
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export default function () {
  // Generate unique ISBN
  const isbn = `978${Date.now()}${Math.floor(Math.random() * 1000)}`.substring(0, 13);
  
  // FR-A: Create Book (Saga)
  const createPayload = JSON.stringify({
    title: `Load Test Book ${isbn}`,
    genre: 'Ficção',
    authors: ['Manuel Teixeira Gomes'],
    description: 'Performance test book'
  });

  const createRes = http.put(`${BASE_URL}/api/books/${isbn}`, createPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  bookCreationTime.add(createRes.timings.duration);

  const createSuccess = check(createRes, {
    'book created': (r) => r.status === 201,
  });

  if (!createSuccess) {
    errors.add(1);
    console.log(`Failed to create book: ${createRes.status} - ${createRes.body}`);
  }

  sleep(1);

  // Read the created book
  const getRes = http.get(`${BASE_URL}/api/books/${isbn}`);
  
  check(getRes, {
    'book retrieved': (r) => r.status === 200,
    'correct isbn': (r) => JSON.parse(r.body).isbn === isbn,
  });

  sleep(0.5);
}
```

**Run k6 Tests**:
```powershell
# Against local BOOKS
k6 run books-load-test.js

# Against Kubernetes (via ingress)
k6 run -e BASE_URL=http://lms-books.local books-load-test.js

# With HTML report
k6 run --out json=results.json books-load-test.js
```

---

## Kubernetes Test Commands

### Deploy to Kubernetes

```powershell
# Navigate to k8s directory (from project root)
cd k8s

# Build Docker image
docker build -t lms-books:latest ..

# Deploy
kubectl apply -f namespace.yaml
kubectl apply -f .

# Verify pods
kubectl get pods -n lms-books -w
```

### Scale Pods

```powershell
# Manual scaling
kubectl scale deployment lms-books -n lms-books --replicas=3

# Verify scaling
kubectl get pods -n lms-books
```

### Monitor During Tests

```powershell
# Watch pod resources
kubectl top pods -n lms-books --watch

# Watch pod logs (all replicas)
kubectl logs -f -l app=lms-books -n lms-books --all-containers

# Port forward for local access
kubectl port-forward svc/lms-books-service 8081:8081 -n lms-books
```

### Simulate Pod Failure

```powershell
# Get pod name
$pod = kubectl get pods -n lms-books -l app=lms-books -o jsonpath='{.items[0].metadata.name}'

# Kill pod during load test
kubectl delete pod $pod -n lms-books --force --grace-period=0
```

---

## Expected Results: Microservice vs Monolith

| Metric | Monolith | BOOKS (1 Pod) | BOOKS (3 Pods) |
|--------|----------|---------------|----------------|
| **FR-A Create Book (avg)** | ~100ms | ~180ms | ~120ms |
| **FR-A Create Book (p95)** | ~200ms | ~350ms | ~200ms |
| **Get Book (avg)** | ~30ms | ~35ms | ~25ms |
| **Throughput (create)** | 100 req/s | 60 req/s | 180 req/s |
| **Throughput (read)** | 500 req/s | 400 req/s | 1200 req/s |
| **Memory Usage** | ~800MB | ~400MB per pod | ~400MB × 3 |
| **Startup Time** | ~15s | ~10s | ~10s per pod |

**Trade-offs**:
- Microservice: Slightly higher per-request latency (saga overhead)
- Microservice: Much better horizontal scalability
- Microservice: Independent deployment and failure isolation

---

## Step-by-Step Execution Plan

### Phase 1: Local Performance (Without Kubernetes)

| Step | Action | Duration |
|------|--------|----------|
| 1.1 | Start BOOKS locally (`mvn spring-boot:run`) | 2 min |
| 1.2 | Start Monolith locally | 2 min |
| 1.3 | Run baseline tests (10 users) | 5 min |
| 1.4 | Compare BOOKS vs Monolith | 10 min |
| 1.5 | Document results | 5 min |

### Phase 2: Kubernetes Single Pod

| Step | Action | Duration |
|------|--------|----------|
| 2.1 | Deploy BOOKS to Kubernetes (1 replica) | 5 min |
| 2.2 | Run load test (50 users) | 5 min |
| 2.3 | Capture pod metrics | 5 min |
| 2.4 | Document results | 5 min |

### Phase 3: Kubernetes Multi-Pod Scaling

| Step | Action | Duration |
|------|--------|----------|
| 3.1 | Scale to 3 replicas | 2 min |
| 3.2 | Run load test (100 users) | 10 min |
| 3.3 | Verify load distribution | 5 min |
| 3.4 | Document results | 5 min |

### Phase 4: Ramp-Up Stress Test

| Step | Action | Duration |
|------|--------|----------|
| 4.1 | Run full ramp-up scenario | 15 min |
| 4.2 | Identify breaking point | 5 min |
| 4.3 | Document degradation curve | 10 min |

### Phase 5: Resilience Testing

| Step | Action | Duration |
|------|--------|----------|
| 5.1 | Run constant load | Start |
| 5.2 | Kill pod mid-test | During |
| 5.3 | Measure recovery | 5 min |
| 5.4 | Verify event consistency (Outbox) | 5 min |

**Total Estimated Time**: ~2-3 hours

---

## Evidence Documentation

### Required Screenshots/Exports

1. **JMeter/k6 Reports**:
   - Response time distribution graph
   - Throughput over time
   - Error rate graph

2. **Kubernetes Dashboard**:
   - Pod CPU/Memory usage during load
   - Pod scaling events
   - Service endpoints

3. **RabbitMQ**:
   - Message rates during load
   - Queue depths

### Evidence Storage

```
MEI-ARCSOFT-2025-2026-1191577-1250530-BOOKS/
└── Docs/
    └── testing-evidence/
        └── performance/
            ├── local/
            │   ├── books-vs-monolith-comparison.png
            │   └── jmeter-aggregate-report.png
            ├── kubernetes/
            │   ├── single-pod-load.png
            │   ├── multi-pod-scaling.png
            │   ├── pod-metrics.png
            │   └── ramp-up-stress-test.png
            └── resilience/
                ├── pod-failure-recovery.png
                └── event-consistency.png
```

---

## Quick Start Commands Reference

### Start Infrastructure
```powershell
# Run from project root directory
docker-compose up -d postgres rabbitmq
```

### Run BOOKS Locally
```powershell
mvn spring-boot:run
```

### Run Unit Performance Tests
```powershell
mvn test -Dgroups=performance
```

### Deploy to Kubernetes
```powershell
cd k8s
kubectl apply -f .
```

### Run k6 Load Test
```powershell
k6 run books-load-test.js
```

### Monitor Kubernetes
```powershell
kubectl top pods -n lms-books
kubectl logs -f -l app=lms-books -n lms-books
```

---

## Related Files

| File | Purpose |
|------|---------|
| `k8s/lms-books-deployment.yaml` | Kubernetes deployment |
| `k8s/lms-books-statefulset.yaml` | Alternative StatefulSet |
| `bookmanagement/infrastructure/saga/` | Saga implementation (FR-A) |
| `src/test/java/.../performance/` | Performance test classes |

---

*Document created: January 2, 2026*
*Author: André (for Josimar)*
*Deadline: January 4, 2026*
