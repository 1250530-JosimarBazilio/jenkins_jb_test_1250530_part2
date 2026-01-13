package pt.psoft.g1.psoftg1.bookmanagement.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.bookmanagement.services.CreateBookRequest;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for BookController.
 * Tests the full HTTP request/response cycle with real database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("BookController Integration Tests")
class BookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private AuthorRepository authorRepository;

    private Genre testGenre;
    private Author testAuthor;

    @BeforeEach
    void setUp() {
        // Create test genre
        testGenre = new Genre("Fiction");
        testGenre = genreRepository.save(testGenre);

        // Create test author
        testAuthor = new Author("John Doe", "A famous author", null);
        testAuthor = authorRepository.save(testAuthor);
    }

    @Nested
    @DisplayName("GET /api/books/{isbn}")
    class GetBookByIsbnTests {

        @Test
        @DisplayName("Should return 200 and book when book exists")
        void shouldReturn200AndBookWhenBookExists() throws Exception {
            // Arrange
            List<Author> authors = new ArrayList<>();
            authors.add(testAuthor);
            Book book = new Book("9780134685991", "Effective Java", "A programming book", testGenre, authors, null);
            bookRepository.save(book);

            // Act & Assert
            mockMvc.perform(get("/api/books/9780134685991")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isbn", is("9780134685991")))
                    .andExpect(jsonPath("$.title", is("Effective Java")))
                    .andExpect(header().exists("ETag"));
        }

        @Test
        @DisplayName("Should return 404 when book does not exist")
        void shouldReturn404WhenBookDoesNotExist() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/books/9999999999999")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/books/{isbn}")
    class CreateBookTests {

        @Test
        @DisplayName("Should return 201 when book is created successfully")
        void shouldReturn201WhenBookIsCreatedSuccessfully() throws Exception {
            // Arrange
            CreateBookRequest request = new CreateBookRequest();
            request.setTitle("Clean Code");
            request.setDescription("A handbook of agile software craftsmanship");
            request.setGenre("Fiction");
            request.setAuthors(List.of(testAuthor.getAuthorNumber()));

            // Act & Assert
            mockMvc.perform(put("/api/books/9780132350884")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title", is("Clean Code")))
                    .andExpect(header().exists("ETag"))
                    .andExpect(header().exists("Location"));
        }

        @Test
        @DisplayName("Should return 400 when request body is invalid")
        void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
            // Arrange - missing required fields
            String invalidRequest = "{\"description\": \"Only description\"}";

            // Act & Assert
            mockMvc.perform(put("/api/books/9780132350884")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when book with ISBN already exists")
        void shouldReturn400WhenBookWithIsbnAlreadyExists() throws Exception {
            // Arrange - create existing book first
            List<Author> authors = new ArrayList<>();
            authors.add(testAuthor);
            Book existingBook = new Book("9780132350884", "Existing Book", "Description", testGenre, authors, null);
            bookRepository.save(existingBook);

            CreateBookRequest request = new CreateBookRequest();
            request.setTitle("Duplicate Book");
            request.setDescription("Trying to create duplicate");
            request.setGenre("Fiction");
            request.setAuthors(List.of(testAuthor.getAuthorNumber()));

            // Act & Assert - API returns 400 for duplicate ISBN
            mockMvc.perform(put("/api/books/9780132350884")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when ISBN format is invalid")
        void shouldReturn400WhenIsbnFormatIsInvalid() throws Exception {
            // Arrange
            CreateBookRequest request = new CreateBookRequest();
            request.setTitle("Test Book");
            request.setDescription("Description");
            request.setGenre("Fiction");
            request.setAuthors(List.of(testAuthor.getAuthorNumber()));

            // Act & Assert - invalid ISBN
            mockMvc.perform(put("/api/books/invalid-isbn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PATCH /api/books/{isbn}")
    class UpdateBookTests {

        @Test
        @DisplayName("Should return 200 when book is updated successfully")
        void shouldReturn200WhenBookIsUpdatedSuccessfully() throws Exception {
            // Arrange
            List<Author> authors = new ArrayList<>();
            authors.add(testAuthor);
            Book book = new Book("9780134685991", "Original Title", "Original Description", testGenre, authors, null);
            book = bookRepository.save(book);

            String updateRequest = "{\"title\": \"Updated Title\", \"description\": \"Updated Description\"}";

            // Act & Assert
            mockMvc.perform(patch("/api/books/9780134685991")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("If-Match", book.getVersion())
                    .content(updateRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is("Updated Title")));
        }

        @Test
        @DisplayName("Should return 409 when updating non-existent book")
        void shouldReturn409WhenUpdatingNonExistentBook() throws Exception {
            // Arrange
            String updateRequest = "{\"title\": \"Updated Title\"}";

            // Act & Assert - API returns 409 Conflict when book not found
            mockMvc.perform(patch("/api/books/9999999999999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("If-Match", "1")
                    .content(updateRequest))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should return 409 when ETag does not match (optimistic lock)")
        void shouldReturn409WhenETagDoesNotMatch() throws Exception {
            // Arrange
            List<Author> authors = new ArrayList<>();
            authors.add(testAuthor);
            Book book = new Book("9780134685991", "Original Title", "Original Description", testGenre, authors, null);
            bookRepository.save(book);

            String updateRequest = "{\"title\": \"Updated Title\"}";

            // Act & Assert - API returns 409 Conflict for version mismatch
            mockMvc.perform(patch("/api/books/9780134685991")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("If-Match", "999")
                    .content(updateRequest))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/books")
    class SearchBooksTests {

        @BeforeEach
        void setUpBooks() {
            List<Author> authors = new ArrayList<>();
            authors.add(testAuthor);

            Book book1 = new Book("9780134685991", "Java Programming", "Learn Java", testGenre, authors, null);
            Book book2 = new Book("9780132350884", "Clean Code", "Software craftsmanship", testGenre, authors, null);
            bookRepository.save(book1);
            bookRepository.save(book2);
        }

        @Test
        @DisplayName("Should return list of books by genre")
        void shouldReturnListOfBooksByGenre() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/books")
                    .param("genre", "Fiction")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @DisplayName("Should return list of books by title")
        void shouldReturnListOfBooksByTitle() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/books")
                    .param("title", "Java")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @DisplayName("Should return 404 when no books match criteria")
        void shouldReturn404WhenNoBooksMatchCriteria() throws Exception {
            // Act & Assert - API returns 404 when no books found
            mockMvc.perform(get("/api/books")
                    .param("title", "NonExistentBookTitle12345")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }
}
