package pt.psoft.g1.psoftg1.bookmanagement.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga.CreateBookSagaOrchestrator;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BookServiceImpl.
 * Uses mocks to isolate the service layer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookService Unit Tests")
class BookServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private AuthorRepository authorRepository;

    @Mock
    private CreateBookSagaOrchestrator createBookSagaOrchestrator;

    @InjectMocks
    private BookServiceImpl bookService;

    // Test data
    private static final String VALID_ISBN = "9780134685991";
    private static final String VALID_TITLE = "Effective Java";
    private static final String VALID_DESCRIPTION = "A comprehensive guide to programming in Java.";

    private Genre validGenre;
    private Author validAuthor;
    private List<Author> validAuthors;
    private Book validBook;

    @BeforeEach
    void setUp() {
        validGenre = new Genre("Programming");
        validAuthor = new Author("Joshua Bloch", "Joshua Bloch is a software engineer.", null);
        validAuthors = new ArrayList<>();
        validAuthors.add(validAuthor);
        validBook = new Book(VALID_ISBN, VALID_TITLE, VALID_DESCRIPTION, validGenre, validAuthors, null);
    }

    @Nested
    @DisplayName("Create Book Tests")
    class CreateBookTests {

        @Test
        @DisplayName("Should create book successfully with valid request")
        void shouldCreateBookSuccessfullyWithValidRequest() {
            // Arrange
            CreateBookRequest request = new CreateBookRequest();
            request.setTitle(VALID_TITLE);
            request.setDescription(VALID_DESCRIPTION);

            when(createBookSagaOrchestrator.execute(any(CreateBookRequest.class), eq(VALID_ISBN), isNull()))
                    .thenReturn(validBook);

            // Act
            Book result = bookService.create(request, VALID_ISBN);

            // Assert
            assertNotNull(result);
            assertEquals(VALID_ISBN, result.getIsbn());
            verify(createBookSagaOrchestrator).execute(any(CreateBookRequest.class), eq(VALID_ISBN), isNull());
        }

        @Test
        @DisplayName("Should delegate to saga orchestrator for book creation")
        void shouldDelegateToSagaOrchestratorForBookCreation() {
            // Arrange
            CreateBookRequest request = new CreateBookRequest();
            request.setTitle(VALID_TITLE);

            when(createBookSagaOrchestrator.execute(any(CreateBookRequest.class), eq(VALID_ISBN), isNull()))
                    .thenReturn(validBook);

            // Act
            bookService.create(request, VALID_ISBN);

            // Assert
            verify(createBookSagaOrchestrator, times(1)).execute(any(CreateBookRequest.class), eq(VALID_ISBN),
                    isNull());
        }
    }

    @Nested
    @DisplayName("Update Book Tests")
    class UpdateBookTests {

        @Test
        @DisplayName("Should call findByIsbn when updating book")
        void shouldCallFindByIsbnWhenUpdatingBook() {
            // Arrange
            UpdateBookRequest request = new UpdateBookRequest(VALID_ISBN, "New Title", null, List.of(), null);

            when(bookRepository.findByIsbn(VALID_ISBN)).thenReturn(Optional.of(validBook));

            // Act - version check will fail but we verify the repository was called
            try {
                bookService.update(request, "0");
            } catch (Exception e) {
                // Expected - version mismatch for unsaved entities
            }

            // Assert
            verify(bookRepository).findByIsbn(VALID_ISBN);
        }

        @Test
        @DisplayName("Should throw NotFoundException when book does not exist")
        void shouldThrowNotFoundExceptionWhenBookDoesNotExist() {
            // Arrange
            UpdateBookRequest request = new UpdateBookRequest(VALID_ISBN, "New Title", null, List.of(), null);

            when(bookRepository.findByIsbn(VALID_ISBN)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> bookService.update(request, "0"));
            verify(bookRepository).findByIsbn(VALID_ISBN);
            verify(bookRepository, never()).save(any(Book.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when new genre does not exist")
        void shouldThrowNotFoundExceptionWhenNewGenreDoesNotExist() {
            // Arrange
            UpdateBookRequest request = new UpdateBookRequest(VALID_ISBN, null, "NonExistentGenre", List.of(), null);

            when(bookRepository.findByIsbn(VALID_ISBN)).thenReturn(Optional.of(validBook));
            when(genreRepository.findByString("NonExistentGenre")).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> bookService.update(request, "0"));
        }

        @Test
        @DisplayName("Should lookup genre when genre is specified in request")
        void shouldLookupGenreWhenGenreIsSpecifiedInRequest() {
            // Arrange
            Genre newGenre = new Genre("Fiction");
            UpdateBookRequest request = new UpdateBookRequest(VALID_ISBN, null, "Fiction", List.of(), null);

            when(bookRepository.findByIsbn(VALID_ISBN)).thenReturn(Optional.of(validBook));
            when(genreRepository.findByString("Fiction")).thenReturn(Optional.of(newGenre));

            // Act - version check may fail
            try {
                bookService.update(request, "0");
            } catch (Exception e) {
                // Expected for unsaved entities
            }

            // Assert
            verify(genreRepository).findByString("Fiction");
        }

        @Test
        @DisplayName("Should lookup authors when authors are specified in request")
        void shouldLookupAuthorsWhenAuthorsAreSpecifiedInRequest() {
            // Arrange
            Author newAuthor = new Author("Martin Fowler", "Author bio", null);
            UpdateBookRequest request = new UpdateBookRequest(VALID_ISBN, null, null, List.of(1L), null);

            when(bookRepository.findByIsbn(VALID_ISBN)).thenReturn(Optional.of(validBook));
            when(authorRepository.findByAuthorNumber(1L)).thenReturn(Optional.of(newAuthor));

            // Act - version check may fail
            try {
                bookService.update(request, "0");
            } catch (Exception e) {
                // Expected for unsaved entities
            }

            // Assert
            verify(authorRepository).findByAuthorNumber(1L);
        }
    }

    @Nested
    @DisplayName("Find Book Tests")
    class FindBookTests {

        @Test
        @DisplayName("Should find book by ISBN")
        void shouldFindBookByIsbn() {
            // Arrange
            when(bookRepository.findByIsbn(VALID_ISBN)).thenReturn(Optional.of(validBook));

            // Act
            Book result = bookService.findByIsbn(VALID_ISBN);

            // Assert
            assertNotNull(result);
            assertEquals(VALID_ISBN, result.getIsbn());
            verify(bookRepository).findByIsbn(VALID_ISBN);
        }

        @Test
        @DisplayName("Should throw NotFoundException when book not found by ISBN")
        void shouldThrowNotFoundExceptionWhenBookNotFoundByIsbn() {
            // Arrange
            when(bookRepository.findByIsbn(VALID_ISBN)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotFoundException.class, () -> bookService.findByIsbn(VALID_ISBN));
            verify(bookRepository).findByIsbn(VALID_ISBN);
        }

        @Test
        @DisplayName("Should find books by genre")
        void shouldFindBooksByGenre() {
            // Arrange
            List<Book> books = Arrays.asList(validBook);
            when(bookRepository.findByGenre("Programming")).thenReturn(books);

            // Act
            List<Book> result = bookService.findByGenre("Programming");

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(bookRepository).findByGenre("Programming");
        }

        @Test
        @DisplayName("Should find books by title")
        void shouldFindBooksByTitle() {
            // Arrange
            List<Book> books = Arrays.asList(validBook);
            when(bookRepository.findByTitle(VALID_TITLE)).thenReturn(books);

            // Act
            List<Book> result = bookService.findByTitle(VALID_TITLE);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(bookRepository).findByTitle(VALID_TITLE);
        }

        @Test
        @DisplayName("Should find books by author name")
        void shouldFindBooksByAuthorName() {
            // Arrange
            List<Book> books = Arrays.asList(validBook);
            when(bookRepository.findByAuthorName("Joshua%")).thenReturn(books);

            // Act
            List<Book> result = bookService.findByAuthorName("Joshua");

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(bookRepository).findByAuthorName("Joshua%");
        }

        @Test
        @DisplayName("Should return empty list when no books found by genre")
        void shouldReturnEmptyListWhenNoBooksFoundByGenre() {
            // Arrange
            when(bookRepository.findByGenre("Unknown")).thenReturn(new ArrayList<>());

            // Act
            List<Book> result = bookService.findByGenre("Unknown");

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Save Book Tests")
    class SaveBookTests {

        @Test
        @DisplayName("Should save book successfully")
        void shouldSaveBookSuccessfully() {
            // Arrange
            when(bookRepository.save(validBook)).thenReturn(validBook);

            // Act
            Book result = bookService.save(validBook);

            // Assert
            assertNotNull(result);
            assertEquals(validBook, result);
            verify(bookRepository).save(validBook);
        }
    }
}
