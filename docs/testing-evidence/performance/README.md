# Performance Test Evidence

This folder contains evidence from performance and load tests.

## Folder Structure

```
testing-evidence/performance/
├── local/              # Local development tests (without Kubernetes)
├── kubernetes/         # Kubernetes deployment tests
└── resilience/         # Pod failure and recovery tests
```

## Evidence Types

### Screenshots
- k6 console output with summary
- Grafana dashboards (if available)
- Kubernetes dashboard showing pod metrics

### JSON Results
- k6 JSON output files for detailed analysis
- Can be imported into k6 Cloud or other tools

### Key Metrics to Document

| Test | Metrics | Target |
|------|---------|--------|
| FR-A Create Book | avg, p95, p99 | < 200ms, < 500ms, < 1000ms |
| Get Book | avg, p95 | < 50ms, < 100ms |
| Throughput | req/s | 50 (1 pod), 150 (3 pods) |
| Error Rate | % | < 1% |

## Naming Convention

```
{test-type}-{scenario}-{timestamp}.{ext}

Examples:
- smoke-test-20260103-143022.json
- stress-test-20260103-150000.png
- scaling-before-after-comparison.png
```
