# BOOKS Microservice - Design Decisions

## Overview

This document tracks all design decisions made during the extraction of the BOOKS microservice from the monolith and the implementation of architectural patterns. These decisions are crucial for:

1. **ADD Report**: Documenting architectural choices and their rationale
2. **Implementation Reference**: Understanding deviations from the monolith
3. **Future Development**: Knowing how services are decoupled

---

## Decision Log

### DD-001: Implement Saga Pattern for Book Creation

| Attribute | Value |
|-----------|-------|
| **Date** | 2026-01-01 |
| **Component** | `bookmanagement/infrastructure/saga/` |
| **Status** | Implemented |

#### Context
In a microservice architecture, creating a Book involves multiple steps that may fail:
1. Validating ISBN uniqueness
2. Validating Genre existence
3. Validating Authors existence
4. Creating Book entity
5. Publishing BookCreated event

If any step fails, we need to rollback previous changes atomically.

#### Decision
Implemented the **Saga Pattern** with an Orchestrator approach for Book creation.

#### Implementation Structure
```
bookmanagement/infrastructure/saga/
├── CreateBookSagaContext.java       # Context with saga data
├── CreateBookSagaOrchestrator.java  # Orchestrator service
└── steps/
    ├── ValidateBookStep.java        # Step 1: Validation
    ├── CreateBookStep.java          # Step 2: Create Book
    └── PublishBookCreatedEventStep.java  # Step 3: Outbox event
```

#### Rationale
- **Atomic Operations**: Either all steps succeed or all are compensated
- **Eventual Consistency**: System reaches consistent state after compensation
- **Failure Resilience**: Each step has a compensating action for rollback
- **Consistency with AUTHNUSERS**: Same pattern used across microservices

#### Trade-offs
- **Pro**: Clean rollback mechanism for distributed operations
- **Pro**: Easy to add new steps to the saga
- **Pro**: Good logging and debugging support
- **Con**: More complex than simple service methods
- **Con**: Additional classes to maintain

---

### DD-002: Implement Outbox Pattern for Reliable Event Publishing

| Attribute | Value |
|-----------|-------|
| **Date** | 2026-01-01 |
| **Component** | `shared/infrastructure/outbox/` |
| **Status** | Implemented |

#### Context
Publishing events directly to RabbitMQ during a transaction can lead to:
- Events published but transaction rolled back (phantom events)
- Transaction committed but event publishing failed (lost events)
- No guarantee of at-least-once delivery

#### Decision
Implemented the **Transactional Outbox Pattern** for reliable event publishing.

#### Implementation Structure
```
shared/infrastructure/outbox/
├── OutboxEvent.java           # Entity for outbox events
├── OutboxEventRepository.java # Repository for querying events
├── OutboxPublisher.java       # Scheduled publisher (polls every 100ms)
└── OutboxStatus.java          # Enum: PENDING, PUBLISHED, FAILED

shared/services/
└── OutboxEventService.java    # High-level API for saving events
```

#### How It Works
1. Business logic saves event to `outbox_events` table in same transaction
2. `OutboxPublisher` polls for PENDING events every 100ms
3. Publisher sends to RabbitMQ and marks as PUBLISHED
4. Failed events are retried up to 3 times
5. Old PUBLISHED events are cleaned up daily at 3:00 AM

#### Rationale
- **Atomicity**: Event stored in same transaction as business data
- **Reliability**: Guaranteed at-least-once delivery
- **Resilience**: Survives RabbitMQ outages (events queued in DB)
- **Ordering**: Events processed in FIFO order per aggregate

#### Trade-offs
- **Pro**: No lost events, even during broker failures
- **Pro**: Transactional consistency between data and events
- **Pro**: Built-in retry mechanism with failure tracking
- **Con**: Slight latency (100-200ms) compared to direct publishing
- **Con**: Additional database table and scheduled task

---

### DD-003: Generic Saga Infrastructure in Shared Module

| Attribute | Value |
|-----------|-------|
| **Date** | 2026-01-01 |
| **Component** | `shared/infrastructure/saga/` |
| **Status** | Implemented |

#### Context
The Saga pattern can be applied to multiple operations across different domains (Books, Authors, Genres). Having domain-specific implementations would lead to code duplication.

#### Decision
Created generic Saga infrastructure in the shared module that can be reused across domains.

#### Implementation Structure
```
shared/infrastructure/saga/
├── SagaStep.java         # Interface for saga steps
├── SagaOrchestrator.java # Generic orchestrator
└── SagaResult.java       # Result container
```

#### Generic Interfaces
```java
// SagaStep<T> interface
boolean execute(T context);
void compensate(T context);
String getStepName();

// SagaOrchestrator<T> class
SagaOrchestrator<T> addStep(SagaStep<T> step);
SagaResult<T> execute(T context);
```

#### Rationale
- **DRY Principle**: Single implementation of orchestration logic
- **Consistency**: Same behavior across all sagas
- **Extensibility**: Easy to create new sagas for other entities
- **Testability**: Generic components can be tested independently

#### Trade-offs
- **Pro**: Reduced code duplication
- **Pro**: Consistent error handling and logging
- **Pro**: Easy to understand saga flow
- **Con**: Requires understanding of generics

---

### DD-004: Event Publishing Integration with Saga

| Attribute | Value |
|-----------|-------|
| **Date** | 2026-01-01 |
| **Component** | `PublishBookCreatedEventStep.java` |
| **Status** | Implemented |

#### Context
The final step of a saga should publish an event to notify other microservices. This step must be integrated with the Outbox pattern.

#### Decision
Create event publishing as a saga step that uses the OutboxEventService.

#### Implementation
```java
@Component
public class PublishBookCreatedEventStep implements SagaStep<CreateBookSagaContext> {
    
    private final OutboxEventService outboxEventService;
    
    @Override
    public boolean execute(CreateBookSagaContext context) {
        BookViewAMQP bookView = new BookViewAMQP(...);
        
        outboxEventService.saveEvent(
                "Book",
                book.getIsbn(),
                "BookCreatedEvent",
                bookView,
                RabbitmqConfig.EXCHANGE_NAME,
                BookEvents.BOOK_CREATED
        );
        
        return true;
    }
    
    @Override
    public void compensate(CreateBookSagaContext context) {
        // If transaction rolls back, outbox event is also rolled back
        // No additional compensation needed
    }
}
```

#### Rationale
- **Transactional Boundary**: Event saved in same transaction as Book
- **Automatic Rollback**: If saga fails, outbox event is also rolled back
- **Clean Integration**: Saga pattern works seamlessly with Outbox pattern

---

### DD-005: Service Layer Refactoring to Use Saga

| Attribute | Value |
|-----------|-------|
| **Date** | 2026-01-01 |
| **Component** | `BookServiceImpl.java` |
| **Status** | Implemented |

#### Context
The existing BookServiceImpl had all creation logic in a single method. This made it hard to add compensation logic and event publishing.

#### Decision
Refactor BookServiceImpl to delegate to CreateBookSagaOrchestrator.

#### Before
```java
@Override
public Book create(CreateBookRequest request, String isbn) {
    if(bookRepository.findByIsbn(isbn).isPresent()){
        throw new ConflictException("Book already exists");
    }
    // ... validation and creation logic ...
    return bookRepository.save(newBook);
}
```

#### After
```java
@Override
public Book create(CreateBookRequest request, String isbn) {
    log.info("Creating book with ISBN: {} using Saga Pattern", isbn);
    
    // Handle photo logic
    MultipartFile photo = request.getPhoto();
    String photoURI = request.getPhotoURI();
    if(photo == null && photoURI != null || photo != null && photoURI == null) {
        request.setPhoto(null);
        request.setPhotoURI(null);
    }
    
    // Delegate to Saga Orchestrator for atomic operation with compensation
    return createBookSagaOrchestrator.execute(request, isbn, photoURI);
}
```

#### Rationale
- **Separation of Concerns**: Service layer only handles request preparation
- **Single Responsibility**: Saga orchestrator handles the complex flow
- **Testability**: Each saga step can be tested independently

---

## Pattern Summary

| Pattern | Purpose | Location |
|---------|---------|----------|
| **Saga Pattern** | Distributed transactions with compensation | `**/infrastructure/saga/` |
| **Outbox Pattern** | Reliable event publishing | `shared/infrastructure/outbox/` |
| **Publisher Interface** | Abstraction for event publishing | `**/publishers/` |
| **Repository Pattern** | Data access abstraction | `**/repositories/` |

---

## Future Considerations

1. **Author Saga**: Apply same pattern for Author creation/update
2. **Genre Saga**: Apply same pattern for Genre management
3. **Event Sourcing**: Consider for audit trail requirements
4. **CQRS**: Separate read and write models if needed
5. **Monitoring**: Add metrics for saga execution and outbox status
