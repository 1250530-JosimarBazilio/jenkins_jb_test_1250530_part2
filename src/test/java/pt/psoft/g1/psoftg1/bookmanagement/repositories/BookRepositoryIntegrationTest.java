package pt.psoft.g1.psoftg1.bookmanagement.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BookRepository.
 * Tests database operations using an embedded database.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("BookRepository Integration Tests")
class BookRepositoryIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Nested
    @DisplayName("Save Book Tests")
    class SaveBookTests {

        @Test
        @DisplayName("Should persist book successfully")
        void shouldPersistBookSuccessfully() {
            // TODO: Implement test
        }

        @Test
        @DisplayName("Should generate ID when saving new book")
        void shouldGenerateIdWhenSavingNewBook() {
            // TODO: Implement test
        }

        @Test
        @DisplayName("Should update version on save")
        void shouldUpdateVersionOnSave() {
            // TODO: Implement test
        }
    }

    @Nested
    @DisplayName("Find Book Tests")
    class FindBookTests {

        @Test
        @DisplayName("Should find book by ISBN")
        void shouldFindBookByIsbn() {
            // TODO: Implement test
        }

        @Test
        @DisplayName("Should return empty when book not found by ISBN")
        void shouldReturnEmptyWhenBookNotFoundByIsbn() {
            // TODO: Implement test
        }

        @Test
        @DisplayName("Should find books by genre")
        void shouldFindBooksByGenre() {
            // TODO: Implement test
        }

        @Test
        @DisplayName("Should find books by title containing string")
        void shouldFindBooksByTitleContainingString() {
            // TODO: Implement test
        }

        @Test
        @DisplayName("Should find books by author name")
        void shouldFindBooksByAuthorName() {
            // TODO: Implement test
        }
    }

    @Nested
    @DisplayName("Delete Book Tests")
    class DeleteBookTests {

        @Test
        @DisplayName("Should delete book successfully")
        void shouldDeleteBookSuccessfully() {
            // TODO: Implement test
        }
    }
}
