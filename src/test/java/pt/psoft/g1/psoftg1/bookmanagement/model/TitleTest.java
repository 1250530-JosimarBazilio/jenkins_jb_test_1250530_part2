package pt.psoft.g1.psoftg1.bookmanagement.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Title value object.
 */
@DisplayName("Title Value Object Unit Tests")
class TitleTest {

    private static final String VALID_TITLE = "Effective Java";
    private static final int TITLE_MAX_LENGTH = 128;

    @Nested
    @DisplayName("Title Creation Tests")
    class TitleCreationTests {

        @Test
        @DisplayName("Should create valid title")
        void shouldCreateValidTitle() {
            Title title = new Title(VALID_TITLE);

            assertNotNull(title);
            assertEquals(VALID_TITLE, title.getTitle());
        }

        @ParameterizedTest
        @ValueSource(strings = { "Clean Code", "The Pragmatic Programmer", "Design Patterns", "Refactoring" })
        @DisplayName("Should create title with various valid names")
        void shouldCreateTitleWithVariousValidNames(String titleName) {
            Title title = new Title(titleName);

            assertNotNull(title);
            assertEquals(titleName, title.getTitle());
        }

        @Test
        @DisplayName("Should create title with single character")
        void shouldCreateTitleWithSingleCharacter() {
            Title title = new Title("A");

            assertNotNull(title);
            assertEquals("A", title.getTitle());
        }

        @Test
        @DisplayName("Should create title with exactly max length")
        void shouldCreateTitleWithExactlyMaxLength() {
            String maxLengthTitle = "a".repeat(TITLE_MAX_LENGTH);

            Title title = new Title(maxLengthTitle);

            assertNotNull(title);
            assertEquals(maxLengthTitle, title.getTitle());
        }

        @Test
        @DisplayName("Should strip whitespace from title")
        void shouldStripWhitespaceFromTitle() {
            Title title = new Title("  Effective Java  ");

            assertEquals("Effective Java", title.getTitle());
        }
    }

    @Nested
    @DisplayName("Title Validation Tests")
    class TitleValidationTests {

        @Test
        @DisplayName("Should throw exception when title is null")
        void shouldThrowExceptionWhenTitleIsNull() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Title(null));
            assertEquals("Title cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when title is empty")
        void shouldThrowExceptionWhenTitleIsEmpty() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Title(""));
            assertEquals("Title cannot be blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when title is blank")
        void shouldThrowExceptionWhenTitleIsBlank() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Title("   "));
            assertEquals("Title cannot be blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when title exceeds max length")
        void shouldThrowExceptionWhenTitleExceedsMaxLength() {
            String longTitle = "a".repeat(TITLE_MAX_LENGTH + 1);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Title(longTitle));
            assertTrue(exception.getMessage().contains("maximum") || exception.getMessage().contains("128"));
        }

        @ParameterizedTest
        @ValueSource(strings = { "\t", "\n", "   \t   ", "\n\n" })
        @DisplayName("Should throw exception for whitespace-only titles")
        void shouldThrowExceptionForWhitespaceOnlyTitles(String whitespaceTitle) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Title(whitespaceTitle));
        }
    }

    @Nested
    @DisplayName("Title String Representation Tests")
    class TitleStringRepresentationTests {

        @Test
        @DisplayName("Should return correct string representation")
        void shouldReturnCorrectStringRepresentation() {
            Title title = new Title(VALID_TITLE);

            assertEquals(VALID_TITLE, title.toString());
        }

        @Test
        @DisplayName("toString should match getTitle")
        void toStringShouldMatchGetTitle() {
            Title title = new Title(VALID_TITLE);

            assertEquals(title.getTitle(), title.toString());
        }
    }

    @Nested
    @DisplayName("Title Getter Tests")
    class TitleGetterTests {

        @Test
        @DisplayName("Should return correct title via getter")
        void shouldReturnCorrectTitleViaGetter() {
            Title title = new Title(VALID_TITLE);

            assertEquals(VALID_TITLE, title.getTitle());
        }
    }
}
