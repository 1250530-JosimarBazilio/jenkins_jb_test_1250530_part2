package pt.psoft.g1.psoftg1.genremanagement.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Genre entity.
 */
@DisplayName("Genre Entity Unit Tests")
class GenreTest {

    private static final String VALID_GENRE_NAME = "Fiction";
    private static final int GENRE_MAX_LENGTH = 100;

    @Nested
    @DisplayName("Genre Creation Tests")
    class GenreCreationTests {

        @Test
        @DisplayName("Should create genre with valid name")
        void shouldCreateGenreWithValidName() {
            Genre genre = new Genre(VALID_GENRE_NAME);

            assertNotNull(genre);
            assertEquals(VALID_GENRE_NAME, genre.getGenre());
        }

        @ParameterizedTest
        @ValueSource(strings = { "Science Fiction", "Mystery", "Romance", "Horror", "Biography" })
        @DisplayName("Should create genre with various valid names")
        void shouldCreateGenreWithVariousValidNames(String genreName) {
            Genre genre = new Genre(genreName);

            assertNotNull(genre);
            assertEquals(genreName, genre.getGenre());
        }

        @Test
        @DisplayName("Should throw exception when genre name is null")
        void shouldThrowExceptionWhenGenreNameIsNull() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Genre(null));
            assertEquals("Genre cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when genre name is empty")
        void shouldThrowExceptionWhenGenreNameIsEmpty() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Genre(""));
            assertEquals("Genre cannot be blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when genre name is blank")
        void shouldThrowExceptionWhenGenreNameIsBlank() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Genre("   "));
            assertEquals("Genre cannot be blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when genre name exceeds max length")
        void shouldThrowExceptionWhenGenreNameExceedsMaxLength() {
            String longGenreName = "a".repeat(GENRE_MAX_LENGTH + 1);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Genre(longGenreName));
            assertTrue(exception.getMessage().contains("maximum") || exception.getMessage().contains("4096"));
        }

        @Test
        @DisplayName("Should create genre with exactly max length name")
        void shouldCreateGenreWithExactlyMaxLengthName() {
            String maxLengthName = "a".repeat(GENRE_MAX_LENGTH);

            Genre genre = new Genre(maxLengthName);

            assertNotNull(genre);
            assertEquals(maxLengthName, genre.getGenre());
        }

        @Test
        @DisplayName("Should create genre with single character name")
        void shouldCreateGenreWithSingleCharacterName() {
            Genre genre = new Genre("A");

            assertNotNull(genre);
            assertEquals("A", genre.getGenre());
        }
    }

    @Nested
    @DisplayName("Genre String Representation Tests")
    class GenreStringRepresentationTests {

        @Test
        @DisplayName("Should return correct string representation")
        void shouldReturnCorrectStringRepresentation() {
            Genre genre = new Genre(VALID_GENRE_NAME);

            assertEquals(VALID_GENRE_NAME, genre.toString());
        }

        @Test
        @DisplayName("toString should return genre name")
        void toStringShouldReturnGenreName() {
            String genreName = "Science Fiction";
            Genre genre = new Genre(genreName);

            assertEquals(genreName, genre.toString());
        }
    }

    @Nested
    @DisplayName("Genre Getter Tests")
    class GenreGetterTests {

        @Test
        @DisplayName("Should return correct genre name via getter")
        void shouldReturnCorrectGenreNameViaGetter() {
            Genre genre = new Genre(VALID_GENRE_NAME);

            assertEquals(VALID_GENRE_NAME, genre.getGenre());
        }
    }
}
