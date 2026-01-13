# BOOKS → Shared RabbitMQ Setup

## Problem
BOOKS microservice needs to use the Monolith's RabbitMQ (not its own) so events reach the Monolith.

---

## Step 1: Start Monolith (owns RabbitMQ)
```powershell
cd MEI-ARCSOFT-2025-2026-1191577-1250530-PART-2
docker compose up -d
```

Wait 30 seconds, then verify:
```powershell
docker ps --format "table {{.Names}}\t{{.Status}}" | Select-String "lms_lending|rabbitmq|postgres"
```

---

## Step 2: Start BOOKS PostgreSQL only
```powershell
cd MEI-ARCSOFT-2025-2026-1191577-1250530-BOOKS
docker compose up -d postgres_books
```

---

## Step 3: Connect postgres_books to Monolith network
```powershell
docker network connect mei-arcsoft-2025-2026-1191577-1250530-part-2_lms_network postgres_books
```

---

## Step 4: Run BOOKS manually on Monolith network
```powershell
docker run -d --name lms_books `
  --network mei-arcsoft-2025-2026-1191577-1250530-part-2_lms_network `
  -p 8090:8090 `
  -e SPRING_PROFILES_ACTIVE=postgres,bootstrap `
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres_books:5432/booksdb `
  -e SPRING_RABBITMQ_HOST=rabbitmq_lms `
  -e SERVER_PORT=8090 `
  mei-arcsoft-2025-2026-1191577-1250530-books-lms-books
```

---

## Step 5: Verify BOOKS connects to RabbitMQ
```powershell
docker logs lms_books --tail 10
```

Look for: `Created new connection: rabbitConnectionFactory`

---

## Step 6: Test Event Flow
```powershell
# Create a book
curl -X PUT "http://localhost:8090/api/books/9781234567890" `
  -H "Content-Type: application/json" `
  -d '{"title":"Test Book","genre":"Fantasia","description":"Test","authors":[1]}'

# Check Monolith received the event
docker logs lms_lending --tail 10 | Select-String "BOOK_CREATED|BookCache"
```

---

## Cleanup
```powershell
docker stop lms_books postgres_books
docker rm lms_books postgres_books
```

---

## Key Points

| Config | Value | Reason |
|--------|-------|--------|
| `SPRING_RABBITMQ_HOST` | `rabbitmq_lms` | Uses Monolith's RabbitMQ container |
| Network | `mei-arcsoft-2025-2026-1191577-1250530-part-2_lms_network` | Same network as Monolith |
| Port | `8090` | Avoids conflict with Monolith (8080) |

---

## Alternative: Using docker-compose.yml (Simplified)

If you modified the `docker-compose.yml` as recommended, you can use:

```powershell
# 1. Start Monolith first
cd MEI-ARCSOFT-2025-2026-1191577-1250530-PART-2
docker compose up -d

# 2. Start BOOKS (postgres + app only, uses external RabbitMQ)
cd ../MEI-ARCSOFT-2025-2026-1191577-1250530-BOOKS
docker compose up -d postgres_books lms-books
```

This works because:
- Both use the same network name `lms_network`
- BOOKS connects to `rabbitmq_lms` (Monolith's container)
- No need for manual `docker run` commands
