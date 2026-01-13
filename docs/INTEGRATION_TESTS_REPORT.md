# Integration Tests Report

## Overview

This document provides a comprehensive report of the integration tests implemented for the LMS Books microservice. These tests verify the full HTTP request/response cycle with a real database (H2 in-memory).

**Date:** January 4, 2026  
**Total Tests:** 213 (including unit and integration tests)  
**Integration Tests:** 50  
**Status:** ✅ All tests passing

---

## Test Configuration

All integration tests use the following configuration:

| Annotation | Purpose |
|------------|---------|
| `@SpringBootTest` | Loads the complete Spring application context |
| `@AutoConfigureMockMvc` | Configures MockMvc for HTTP request simulation |
| `@ActiveProfiles("test")` | Activates the test profile |
| `@Transactional` | Ensures automatic rollback after each test |
| `@DisplayName` | Provides readable test descriptions |

---

## BookController Integration Tests

**File:** `src/test/java/pt/psoft/g1/psoftg1/bookmanagement/api/BookControllerIntegrationTest.java`

### GET /api/books/{isbn}

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturn200AndBookWhenBookExists` | Returns book details when ISBN exists | 200 OK |
| `shouldReturn404WhenBookDoesNotExist` | Returns error when ISBN not found | 404 Not Found |

### PUT /api/books/{isbn}

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturn201WhenBookIsCreatedSuccessfully` | Creates a new book with valid data | 201 Created |
| `shouldReturn400WhenRequestBodyIsInvalid` | Rejects request with missing required fields | 400 Bad Request |
| `shouldReturn400WhenBookWithIsbnAlreadyExists` | Rejects duplicate ISBN | 400 Bad Request |
| `shouldReturn400WhenIsbnFormatIsInvalid` | Rejects invalid ISBN format | 400 Bad Request |

### PATCH /api/books/{isbn}

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturn200WhenBookIsUpdatedSuccessfully` | Updates book with valid ETag | 200 OK |
| `shouldReturn409WhenUpdatingNonExistentBook` | Conflict when book not found | 409 Conflict |
| `shouldReturn409WhenETagDoesNotMatch` | Conflict on optimistic lock failure | 409 Conflict |

### GET /api/books

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturnListOfBooksByGenre` | Finds books by genre parameter | 200 OK |
| `shouldReturnListOfBooksByTitle` | Finds books by title parameter | 200 OK |
| `shouldReturn404WhenNoBooksMatchCriteria` | No books match search criteria | 404 Not Found |

---

## AuthorController Integration Tests

**File:** `src/test/java/pt/psoft/g1/psoftg1/authormanagement/api/AuthorControllerIntegrationTest.java`

### GET /api/authors (Search by Name)

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturnAuthorsMatchingSearchName` | Returns authors matching name query | 200 OK |
| `shouldReturnEmptyListWhenNoAuthorsMatch` | Returns empty list for non-matching name | 200 OK |

### GET /api/authors/{authorNumber}

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturn200AndAuthorWhenAuthorExists` | Returns author details with ETag | 200 OK |
| `shouldReturn404WhenAuthorDoesNotExist` | Error when author number not found | 404 Not Found |

### POST /api/authors

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturn201WhenAuthorIsCreatedSuccessfully` | Creates author with valid data | 201 Created |
| `shouldReturn400WhenRequestBodyIsInvalidMissingName` | Rejects request without name | 400 Bad Request |
| `shouldReturn400WhenRequestBodyIsInvalidBlankName` | Rejects request with blank name | 400 Bad Request |

### PATCH /api/authors/{authorNumber}

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturn200WhenAuthorIsUpdatedSuccessfully` | Updates author with valid ETag | 200 OK |
| `shouldReturn404WhenUpdatingNonExistentAuthor` | Error when author not found | 404 Not Found |
| `shouldReturn409WhenETagDoesNotMatch` | Conflict on optimistic lock failure | 409 Conflict |

---

## GenreController Integration Tests

**File:** `src/test/java/pt/psoft/g1/psoftg1/genremanagement/api/GenreControllerIntegrationTest.java`

### GET /api/genres

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturnListOfAllGenres` | Returns all genres in database | 200 OK |
| `shouldReturnGenresWithCorrectStructure` | Validates response JSON structure | 200 OK |

### GET /api/genres/{name}

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturn200AndGenreWhenGenreExists` | Returns genre by name | 200 OK |
| `shouldReturn404WhenGenreDoesNotExist` | Error when genre not found | 404 Not Found |

### GET /api/genres/health

| Test | Description | Expected Status |
|------|-------------|-----------------|
| `shouldReturn200ForHealthCheck` | Health endpoint returns OK | 200 OK |

---

## Repository Integration Tests

**File:** `src/test/java/pt/psoft/g1/psoftg1/bookmanagement/repositories/BookRepositoryIntegrationTest.java`

### Save Operations

| Test | Description |
|------|-------------|
| `shouldSaveBookSuccessfully` | Persists book to database |
| `shouldGeneratePrimaryKeyOnSave` | Auto-generates book PK |
| `shouldUpdateBookOnSecondSave` | Updates existing book |

### Find Operations

| Test | Description |
|------|-------------|
| `shouldFindBookByIsbn` | Retrieves book by ISBN |
| `shouldFindBooksByGenre` | Retrieves books by genre |
| `shouldFindBooksByTitle` | Retrieves books by title pattern |
| `shouldReturnEmptyWhenBookNotFound` | Returns empty for non-existent ISBN |
| `shouldFindAllBooks` | Retrieves all books in database |

### Delete Operations

| Test | Description |
|------|-------------|
| `shouldDeleteBookSuccessfully` | Removes book from database |

---

## Test Execution Summary

```
Tests run: 213, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Test Categories Breakdown

| Category | Count | Status |
|----------|-------|--------|
| BookController Integration | 12 | ✅ Pass |
| AuthorController Integration | 10 | ✅ Pass |
| GenreController Integration | 5 | ✅ Pass |
| BookRepository Integration | 9 | ✅ Pass |
| BookEventsListener Integration | 7 | ✅ Pass |
| Unit Tests (Model, Services) | 170+ | ✅ Pass |

---

## API Response Codes

The integration tests validate the following HTTP status codes:

| Code | Meaning | When Used |
|------|---------|-----------|
| 200 | OK | Successful GET/PATCH operations |
| 201 | Created | Successful POST/PUT operations |
| 400 | Bad Request | Invalid request body or parameters |
| 404 | Not Found | Resource does not exist |
| 409 | Conflict | Optimistic lock failure or duplicate resource |

---

## Test Data Setup

Each test class uses `@BeforeEach` to set up test data:

- **Books:** Created with valid ISBN, title, description, genre, and authors
- **Authors:** Created with name and bio
- **Genres:** Created with genre name

All data is automatically rolled back after each test due to `@Transactional`.

---

## Running the Tests

### Run All Tests
```bash
./mvnw test
```

### Run Integration Tests Only
```bash
./mvnw test -Dtest="*IntegrationTest*"
```

### Run Specific Test Class
```bash
./mvnw test -Dtest="BookControllerIntegrationTest"
```

### Run With Coverage Report
```bash
./mvnw test jacoco:report
```

Coverage report available at: `target/site/jacoco/index.html`

---

## Notes

1. **Optimistic Locking:** The API uses ETags for optimistic concurrency control. Version mismatches return 409 Conflict.

2. **Response Format:** Author search returns a `ListResponse` with an `items` array wrapper.

3. **GenreController:** Only supports GET operations (no POST/PUT/PATCH endpoints).

4. **Book Creation:** Uses PUT with ISBN in path (idempotent design).

---

## Related Documentation

- [Test Fixes Report](TEST_FIXES_REPORT.md) - Documentation of test configuration fixes
- [CQRS Implementation](CQRS_IMPLEMENTATION.md) - CQRS architecture documentation
- [SAGA Implementation](SAGA_IMPLEMENTATION.md) - Saga pattern documentation
