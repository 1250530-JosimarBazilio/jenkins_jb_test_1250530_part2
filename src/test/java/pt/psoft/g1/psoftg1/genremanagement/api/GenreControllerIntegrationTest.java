package pt.psoft.g1.psoftg1.genremanagement.api;

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
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GenreController.
 * Tests the full HTTP request/response cycle with real database.
 * Note: GenreController only has GET endpoints (no POST/PUT/PATCH).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("GenreController Integration Tests")
class GenreControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GenreRepository genreRepository;

    private Genre testGenre;

    @BeforeEach
    void setUp() {
        // Create test genre
        testGenre = new Genre("Science Fiction");
        testGenre = genreRepository.save(testGenre);
    }

    @Nested
    @DisplayName("GET /api/genres")
    class GetGenresTests {

        @Test
        @DisplayName("Should return list of all genres")
        void shouldReturnListOfAllGenres() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/genres")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$[*].genre", hasItem("Science Fiction")));
        }

        @Test
        @DisplayName("Should return genres with correct structure")
        void shouldReturnGenresWithCorrectStructure() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/genres")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].genre").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/genres/{name}")
    class GetGenreByNameTests {

        @Test
        @DisplayName("Should return 200 and genre when genre exists")
        void shouldReturn200AndGenreWhenGenreExists() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/genres/Science Fiction")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.genre", is("Science Fiction")));
        }

        @Test
        @DisplayName("Should return 404 when genre does not exist")
        void shouldReturn404WhenGenreDoesNotExist() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/genres/NonExistentGenre12345")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/genres/health")
    class HealthCheckTests {

        @Test
        @DisplayName("Should return 200 for health check")
        void shouldReturn200ForHealthCheck() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/genres/health")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Genres API is running")));
        }
    }
}
