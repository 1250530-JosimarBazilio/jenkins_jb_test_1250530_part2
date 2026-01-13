# CQRS Lite Implementation

## Overview

This project implements a lightweight CQRS (Command Query Responsibility Segregation) pattern that separates read and write operations at the application level without the complexity of event sourcing or separate databases.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          REST Controllers                           │
│                    (BookCqrsController, etc.)                       │
└─────────────────────┬────────────────────────┬──────────────────────┘
                      │                        │
                      ▼                        ▼
         ┌────────────────────┐   ┌────────────────────┐
         │    CommandBus      │   │     QueryBus       │
         └─────────┬──────────┘   └──────────┬─────────┘
                   │                         │
          ┌────────┴─────────┐      ┌────────┴────────┐
          ▼                  ▼      ▼                 ▼
  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐
  │CreateBookCmd  │  │UpdateBookCmd  │  │GetBookByIsbn  │
  │   Handler     │  │   Handler     │  │Query Handler  │
  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘
          │                  │                  │
          └──────────────────┴─────────┬────────┘
                                       ▼
                              ┌─────────────────┐
                              │  Repositories   │
                              └─────────────────┘
```

## Core Components

### 1. Base Interfaces (`shared.infrastructure.cqrs`)

#### Command
```java
public interface Command<R> { }
```
Marker interface for write operations. Type parameter `R` represents the return type.

#### Query
```java
public interface Query<R> { }
```
Marker interface for read operations. Type parameter `R` represents the return type.

#### CommandHandler
```java
public interface CommandHandler<C extends Command<R>, R> {
    R handle(C command);
}
```
Processes commands and returns results. Each command has exactly one handler.

#### QueryHandler
```java
public interface QueryHandler<Q extends Query<R>, R> {
    R handle(Q query);
}
```
Processes queries and returns results. Query handlers must not modify state.

### 2. Buses (`shared.infrastructure.cqrs`)

#### CommandBus
- Automatically discovers all `CommandHandler` beans
- Routes commands to the appropriate handler
- Throws `IllegalArgumentException` if no handler is registered

#### QueryBus
- Automatically discovers all `QueryHandler` beans
- Routes queries to the appropriate handler
- Throws `IllegalArgumentException` if no handler is registered

## Book Management Commands

| Command | Description | Handler |
|---------|-------------|---------|
| `CreateBookCommand` | Creates a new book | `CreateBookCommandHandler` |
| `UpdateBookCommand` | Updates an existing book | `UpdateBookCommandHandler` |

## Book Management Queries

| Query | Description | Handler |
|-------|-------------|---------|
| `GetBookByIsbnQuery` | Retrieves book by ISBN | `GetBookByIsbnQueryHandler` |
| `FindBooksByGenreQuery` | Finds books by genre | `FindBooksByGenreQueryHandler` |
| `FindBooksByTitleQuery` | Finds books by title | `FindBooksByTitleQueryHandler` |
| `FindBooksByAuthorQuery` | Finds books by author | `FindBooksByAuthorQueryHandler` |
| `SearchBooksQuery` | Multi-criteria search | `SearchBooksQueryHandler` |

## Genre Management Commands

| Command | Description | Handler |
|---------|-------------|---------|
| `CreateGenreCommand` | Creates a new genre | `CreateGenreCommandHandler` |

## Genre Management Queries

| Query | Description | Handler |
|-------|-------------|---------|
| `GetAllGenresQuery` | Gets all genres | `GetAllGenresQueryHandler` |
| `GetGenreByNameQuery` | Gets genre by name | `GetGenreByNameQueryHandler` |

## Usage Examples

### Using Commands

```java
@RestController
@RequiredArgsConstructor
public class BookCqrsController {
    private final CommandBus commandBus;

    @PutMapping("/api/v2/books/{isbn}")
    public ResponseEntity<BookView> create(CreateBookRequest resource, @PathVariable String isbn) {
        CreateBookCommand command = new CreateBookCommand(
            isbn,
            resource.getTitle(),
            resource.getGenre(),
            resource.getDescription(),
            resource.getAuthors(),
            null, // photo
            null  // photoURI
        );

        Book book = commandBus.dispatch(command);
        return ResponseEntity.ok(bookViewMapper.toBookView(book));
    }
}
```

### Using Queries

```java
@RestController
@RequiredArgsConstructor
public class BookCqrsController {
    private final QueryBus queryBus;

    @GetMapping("/api/v2/books/{isbn}")
    public ResponseEntity<BookView> findByIsbn(@PathVariable String isbn) {
        GetBookByIsbnQuery query = new GetBookByIsbnQuery(isbn);
        Book book = queryBus.dispatch(query);
        return ResponseEntity.ok(bookViewMapper.toBookView(book));
    }
}
```

## API Endpoints

### CQRS v2 Endpoints

The CQRS implementation is available via `/api/v2/` endpoints:

| Method | Endpoint | Description |
|--------|----------|-------------|
| PUT | `/api/v2/books/{isbn}` | Create a book (CQRS) |
| GET | `/api/v2/books/{isbn}` | Get book by ISBN (CQRS) |
| PATCH | `/api/v2/books/{isbn}` | Update a book (CQRS) |
| GET | `/api/v2/books` | Search books (CQRS) |
| GET | `/api/v2/genres` | Get all genres (CQRS) |
| GET | `/api/v2/genres/{name}` | Get genre by name (CQRS) |

### Legacy v1 Endpoints

The original endpoints (`/api/books`, `/api/genres`) remain available for backwards compatibility.

## Benefits of CQRS Lite

1. **Separation of Concerns**: Clear separation between read and write logic
2. **Single Responsibility**: Each handler has one specific purpose
3. **Testability**: Handlers can be unit tested in isolation
4. **Scalability**: Read and write sides can be optimized independently
5. **Maintainability**: Easier to understand and modify specific operations
6. **Flexibility**: Easy to add cross-cutting concerns (logging, validation, caching)

## Integration with Existing Patterns

### Saga Pattern
The `CreateBookCommandHandler` integrates with the existing Saga Pattern via `CreateBookSagaOrchestrator` for atomic operations with compensating transactions.

### Outbox Pattern
Events are still published through the existing Outbox Pattern infrastructure after command execution.

## Directory Structure

```
src/main/java/pt/psoft/g1/psoftg1/
├── shared/
│   └── infrastructure/
│       └── cqrs/
│           ├── Command.java
│           ├── Query.java
│           ├── CommandHandler.java
│           ├── QueryHandler.java
│           ├── CommandBus.java
│           └── QueryBus.java
├── bookmanagement/
│   ├── api/
│   │   ├── BookController.java       # Legacy
│   │   └── BookCqrsController.java   # CQRS
│   └── infrastructure/
│       └── cqrs/
│           ├── commands/
│           │   ├── CreateBookCommand.java
│           │   └── UpdateBookCommand.java
│           ├── queries/
│           │   ├── GetBookByIsbnQuery.java
│           │   ├── FindBooksByGenreQuery.java
│           │   ├── FindBooksByTitleQuery.java
│           │   ├── FindBooksByAuthorQuery.java
│           │   └── SearchBooksQuery.java
│           └── handlers/
│               ├── CreateBookCommandHandler.java
│               ├── UpdateBookCommandHandler.java
│               ├── GetBookByIsbnQueryHandler.java
│               ├── FindBooksByGenreQueryHandler.java
│               ├── FindBooksByTitleQueryHandler.java
│               ├── FindBooksByAuthorQueryHandler.java
│               └── SearchBooksQueryHandler.java
└── genremanagement/
    ├── api/
    │   ├── GenreController.java      # Legacy
    │   └── GenreCqrsController.java  # CQRS
    └── infrastructure/
        └── cqrs/
            ├── commands/
            │   └── CreateGenreCommand.java
            ├── queries/
            │   ├── GetAllGenresQuery.java
            │   └── GetGenreByNameQuery.java
            └── handlers/
                ├── CreateGenreCommandHandler.java
                ├── GetAllGenresQueryHandler.java
                └── GetGenreByNameQueryHandler.java
```

## Future Enhancements

1. **Read Models**: Create optimized read models/projections for complex queries
2. **Event Publishing**: Add automatic event publishing after command execution
3. **Caching**: Add caching layer to QueryBus for frequently accessed data
4. **Validation Pipeline**: Add command validation pipeline before execution
5. **Logging/Metrics**: Add centralized logging and metrics collection in buses
