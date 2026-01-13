# Book Creation Saga Implementation Guide

## Overview

This document describes the implementation of the **Saga Pattern** for Book creation in the BOOKS microservice, following the same architecture as the AUTHNUSERS microservice.

## Functional Requirement

> "As a librarian, I want to create a new book with its details (ISBN, title, genre, authors, description), ensuring data consistency and notifying other services."

This requirement involves:
1. Validating book data (ISBN uniqueness, genre existence, author validity)
2. Creating the Book entity
3. Publishing a BookCreated event for other microservices

## Saga Pattern Architecture

### Why Saga Pattern?

In a microservice architecture, we cannot use traditional ACID transactions across service boundaries. The Saga pattern provides:

- **Atomicity across steps** - Either all steps succeed or all are compensated
- **Eventual consistency** - The system reaches a consistent state
- **Failure resilience** - Each step has a compensating action for rollback

### Saga Components

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CreateBookSagaOrchestrator                       │
├─────────────────────────────────────────────────────────────────────┤
│  Step 1: ValidateBookStep                                           │
│    ├── Validate ISBN uniqueness                                     │
│    ├── Check genre existence                                        │
│    ├── Check author validity                                        │
│    └── Compensation: None (no state created)                        │
├─────────────────────────────────────────────────────────────────────┤
│  Step 2: CreateBookStep                                             │
│    ├── Create Book entity                                           │
│    ├── Persist to database                                          │
│    └── Compensation: Delete Book from database                      │
├─────────────────────────────────────────────────────────────────────┤
│  Step 3: PublishBookCreatedEventStep                                │
│    ├── Save BookCreated event to Outbox                             │
│    ├── Event published asynchronously by OutboxPublisher            │
│    └── Compensation: Event rolls back with transaction              │
└─────────────────────────────────────────────────────────────────────┘
```

## File Structure

```
src/main/java/pt/psoft/g1/psoftg1/
├── shared/
│   ├── infrastructure/
│   │   ├── outbox/
│   │   │   ├── OutboxEvent.java           # Outbox entity
│   │   │   ├── OutboxEventRepository.java # Repository
│   │   │   ├── OutboxPublisher.java       # Scheduled publisher
│   │   │   └── OutboxStatus.java          # PENDING/PUBLISHED/FAILED
│   │   └── saga/
│   │       ├── SagaStep.java              # Interface for saga steps
│   │       ├── SagaOrchestrator.java      # Generic orchestrator
│   │       └── SagaResult.java            # Result container
│   └── services/
│       └── OutboxEventService.java        # High-level outbox API
├── bookmanagement/
│   ├── publishers/
│   │   ├── BookEventsPublisher.java       # Event publishing interface
│   │   └── BookEventsRabbitmqPublisher.java  # RabbitMQ implementation
│   ├── infrastructure/
│   │   └── saga/
│   │       ├── CreateBookSagaContext.java     # Saga context
│   │       ├── CreateBookSagaOrchestrator.java  # Book creation orchestrator
│   │       └── steps/
│   │           ├── ValidateBookStep.java
│   │           ├── CreateBookStep.java
│   │           └── PublishBookCreatedEventStep.java
│   └── services/
│       └── BookServiceImpl.java           # Uses saga orchestrator
```

## Sequence Diagram

### Successful Flow

```
Client          BookController     BookServiceImpl    SagaOrchestrator    Steps              Outbox
  │                   │                    │                │              │                    │
  │  POST /books      │                    │                │              │                    │
  ├──────────────────►│                    │                │              │                    │
  │                   │   create()         │                │              │                    │
  │                   ├───────────────────►│                │              │                    │
  │                   │                    │   execute()    │              │                    │
  │                   │                    ├───────────────►│              │                    │
  │                   │                    │                │  validate    │                    │
  │                   │                    │                ├─────────────►│                    │
  │                   │                    │                │◄─────────────┤ (success)          │
  │                   │                    │                │  createBook  │                    │
  │                   │                    │                ├─────────────►│                    │
  │                   │                    │                │◄─────────────┤ (success)          │
  │                   │                    │                │  saveEvent   │                    │
  │                   │                    │                ├─────────────►│──────────────────►│
  │                   │                    │                │◄─────────────┤ (success)          │
  │                   │                    │◄───────────────┤              │                    │
  │                   │◄───────────────────┤ Book           │              │                    │
  │◄──────────────────┤ 201 Created        │                │              │                    │
  │                   │                    │                │              │                    │
```

### Failure Flow with Compensation

```
Client          BookController     BookServiceImpl    SagaOrchestrator    Steps              Outbox
  │                   │                    │                │              │                    │
  │  POST /books      │                    │                │              │                    │
  ├──────────────────►│                    │                │              │                    │
  │                   │   create()         │                │              │                    │
  │                   ├───────────────────►│                │              │                    │
  │                   │                    │   execute()    │              │                    │
  │                   │                    ├───────────────►│              │                    │
  │                   │                    │                │  validate    │                    │
  │                   │                    │                ├─────────────►│                    │
  │                   │                    │                │◄─────────────┤ (success)          │
  │                   │                    │                │  createBook  │                    │
  │                   │                    │                ├─────────────►│                    │
  │                   │                    │                │◄─────────────┤ (success)          │
  │                   │                    │                │  saveEvent   │                    │
  │                   │                    │                ├─────────────►│                    │
  │                   │                    │                │◄─────────────┤ (FAILURE!)         │
  │                   │                    │                │              │                    │
  │                   │                    │                │ COMPENSATE   │                    │
  │                   │                    │                ├─────────────►│                    │
  │                   │                    │                │ deleteBook() │                    │
  │                   │                    │                │◄─────────────┤                    │
  │                   │                    │◄───────────────┤ Error        │                    │
  │                   │◄───────────────────┤ Exception      │              │                    │
  │◄──────────────────┤ 500 Error          │                │              │                    │
```

## Usage Example

### Creating a Book via API

```bash
curl -X POST http://localhost:8081/api/books/9780000000001 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Clean Architecture",
    "genre": "Technology",
    "description": "A guide to software architecture",
    "authors": [1, 2]
  }'
```

### Expected Response (Success)

```json
{
  "isbn": "9780000000001",
  "title": "Clean Architecture",
  "genre": "Technology",
  "description": "A guide to software architecture",
  "authors": ["Robert C. Martin"],
  "version": 0
}
```

### Expected Log Messages

```
INFO  BookServiceImpl     : Creating book with ISBN: 9780000000001 using Saga Pattern
INFO  SagaOrchestrator    : Starting saga: CreateBookSaga
INFO  SagaOrchestrator    : Executing saga step: ValidateBookStep in saga: CreateBookSaga
INFO  ValidateBookStep    : Book validation passed for ISBN: 9780000000001
INFO  SagaOrchestrator    : Saga step completed: ValidateBookStep in saga: CreateBookSaga
INFO  SagaOrchestrator    : Executing saga step: CreateBookStep in saga: CreateBookSaga
INFO  CreateBookStep      : Book created successfully with ISBN: 9780000000001
INFO  SagaOrchestrator    : Saga step completed: CreateBookStep in saga: CreateBookSaga
INFO  SagaOrchestrator    : Executing saga step: PublishBookCreatedEventStep in saga: CreateBookSaga
INFO  PublishBookCreatedEventStep : Saved BookCreated event to outbox for book: 9780000000001
INFO  SagaOrchestrator    : Saga step completed: PublishBookCreatedEventStep in saga: CreateBookSaga
INFO  SagaOrchestrator    : Saga completed successfully: CreateBookSaga
```

## Outbox Pattern Integration

### How Events Are Published

1. **Step 3** saves the event to the `outbox_events` table in the same transaction
2. **OutboxPublisher** polls every 100ms for PENDING events
3. Publisher sends to RabbitMQ and marks as PUBLISHED
4. If RabbitMQ is down, events remain PENDING and are retried

### Database Schema

```sql
CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    exchange_name VARCHAR(100) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    INDEX idx_outbox_status (status),
    INDEX idx_outbox_created_at (created_at)
);
```

### Monitoring Queries

```sql
-- Check pending events
SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at;

-- Check failed events
SELECT * FROM outbox_events WHERE status = 'FAILED' ORDER BY created_at;

-- Count by status
SELECT status, COUNT(*) FROM outbox_events GROUP BY status;
```

## Error Handling

| Error Type | Behavior |
|------------|----------|
| ISBN already exists | Validation fails, no compensation needed |
| Genre not found | Validation fails, no compensation needed |
| No valid authors | Validation fails, no compensation needed |
| Database error during creation | Compensate validation (no-op) |
| Outbox save fails | Compensate creation (delete book), compensate validation |

## Testing

### Unit Tests for Saga Steps

Each step can be tested independently:

```java
@Test
void validateBookStep_shouldFail_whenIsbnExists() {
    when(bookRepository.findByIsbn("123")).thenReturn(Optional.of(existingBook));
    
    CreateBookSagaContext context = CreateBookSagaContext.of(request, "123", null);
    
    assertFalse(validateBookStep.execute(context));
    assertTrue(context.isValidationFailed());
}
```

### Integration Tests

```java
@Test
@Transactional
void createBookSaga_shouldRollback_whenEventPublishFails() {
    // Simulate outbox service failure
    when(outboxEventService.saveEvent(any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("Outbox failure"));
    
    assertThrows(RuntimeException.class, () -> 
        createBookSagaOrchestrator.execute(request, isbn, null));
    
    // Verify book was compensated (deleted)
    assertFalse(bookRepository.findByIsbn(isbn).isPresent());
}
```
