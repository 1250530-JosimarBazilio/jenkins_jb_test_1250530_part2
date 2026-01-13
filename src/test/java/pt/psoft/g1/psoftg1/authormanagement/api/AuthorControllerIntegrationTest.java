package pt.psoft.g1.psoftg1.authormanagement.api;

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
import pt.psoft.g1.psoftg1.authormanagement.services.CreateAuthorRequest;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthorController.
 * Tests the full HTTP request/response cycle with real database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthorController Integration Tests")
class AuthorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthorRepository authorRepository;

    private Author testAuthor;

    @BeforeEach
    void setUp() {
        // Create test author
        testAuthor = new Author("Jane Smith", "An accomplished writer with multiple awards", null);
        testAuthor = authorRepository.save(testAuthor);
    }

    @Nested
    @DisplayName("GET /api/authors (search by name)")
    class SearchAuthorsTests {

        @Test
        @DisplayName("Should return authors matching search name")
        void shouldReturnAuthorsMatchingSearchName() throws Exception {
            // Act & Assert - API returns ListResponse with "items" array
            mockMvc.perform(get("/api/authors")
                    .param("name", "Jane")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$.items[0].name", containsString("Jane")));
        }

        @Test
        @DisplayName("Should return empty list when no authors match")
        void shouldReturnEmptyListWhenNoAuthorsMatch() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/authors")
                    .param("name", "NonExistentAuthor12345")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /api/authors/{authorNumber}")
    class GetAuthorByNumberTests {

        @Test
        @DisplayName("Should return 200 and author when author exists")
        void shouldReturn200AndAuthorWhenAuthorExists() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/authors/" + testAuthor.getAuthorNumber())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Jane Smith")))
                    .andExpect(jsonPath("$.bio", is("An accomplished writer with multiple awards")))
                    .andExpect(header().exists("ETag"));
        }

        @Test
        @DisplayName("Should return 404 when author does not exist")
        void shouldReturn404WhenAuthorDoesNotExist() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/authors/999999")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/authors")
    class CreateAuthorTests {

        @Test
        @DisplayName("Should return 201 when author is created successfully")
        void shouldReturn201WhenAuthorIsCreatedSuccessfully() throws Exception {
            // Arrange
            CreateAuthorRequest request = new CreateAuthorRequest();
            request.setName("Robert Martin");
            request.setBio("Known as Uncle Bob, author of Clean Code");

            // Act & Assert
            mockMvc.perform(post("/api/authors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is("Robert Martin")))
                    .andExpect(header().exists("Location"));
        }

        @Test
        @DisplayName("Should return 400 when request body is invalid - missing name")
        void shouldReturn400WhenRequestBodyIsInvalidMissingName() throws Exception {
            // Arrange - missing required name field
            String invalidRequest = "{\"bio\": \"Only bio provided\"}";

            // Act & Assert
            mockMvc.perform(post("/api/authors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when request body is invalid - blank name")
        void shouldReturn400WhenRequestBodyIsInvalidBlankName() throws Exception {
            // Arrange - blank name
            CreateAuthorRequest request = new CreateAuthorRequest();
            request.setName("");
            request.setBio("Some bio");

            // Act & Assert
            mockMvc.perform(post("/api/authors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PATCH /api/authors/{authorNumber}")
    class UpdateAuthorTests {

        @Test
        @DisplayName("Should return 200 when author is updated successfully")
        void shouldReturn200WhenAuthorIsUpdatedSuccessfully() throws Exception {
            // Arrange
            String updateRequest = "{\"name\": \"Jane Doe Updated\", \"bio\": \"Updated biography\"}";

            // Act & Assert
            mockMvc.perform(patch("/api/authors/" + testAuthor.getAuthorNumber())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("If-Match", testAuthor.getVersion())
                    .content(updateRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Jane Doe Updated")));
        }

        @Test
        @DisplayName("Should return 404 when updating non-existent author")
        void shouldReturn404WhenUpdatingNonExistentAuthor() throws Exception {
            // Arrange
            String updateRequest = "{\"name\": \"Updated Name\"}";

            // Act & Assert
            mockMvc.perform(patch("/api/authors/999999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("If-Match", "1")
                    .content(updateRequest))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 409 when ETag does not match (optimistic lock)")
        void shouldReturn409WhenETagDoesNotMatch() throws Exception {
            // Arrange
            String updateRequest = "{\"name\": \"Updated Name\"}";

            // Act & Assert - API returns 409 for version mismatch (optimistic lock
            // conflict)
            mockMvc.perform(patch("/api/authors/" + testAuthor.getAuthorNumber())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("If-Match", "999")
                    .content(updateRequest))
                    .andExpect(status().isConflict());
        }
    }
}
