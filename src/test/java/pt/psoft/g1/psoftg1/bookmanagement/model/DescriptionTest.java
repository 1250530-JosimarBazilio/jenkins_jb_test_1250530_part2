package pt.psoft.g1.psoftg1.bookmanagement.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Description value object.
 * Tests domain logic and validation rules.
 */
@DisplayName("Description Value Object Tests")
class DescriptionTest {

    private static final int MAX_LENGTH = 4096;

    @Nested
    @DisplayName("Description Creation Tests")
    class DescriptionCreationTests {

        @Test
        @DisplayName("Should create description with valid text")
        void shouldCreateDescriptionWithValidText() {
            Description description = new Description("A valid description");
            assertEquals("A valid description", description.toString());
        }

        @Test
        @DisplayName("Should handle null description")
        void shouldHandleNullDescription() {
            Description description = new Description(null);
            assertNull(description.toString());
        }

        @Test
        @DisplayName("Should handle empty description")
        void shouldHandleEmptyDescription() {
            Description description = new Description("");
            assertNull(description.toString());
        }

        @Test
        @DisplayName("Should handle blank (whitespace only) description")
        void shouldHandleBlankDescription() {
            Description description = new Description("   ");
            assertNull(description.toString());
        }

        @Test
        @DisplayName("Should create description with exactly max length")
        void shouldCreateDescriptionWithExactlyMaxLength() {
            String maxLengthDesc = "A".repeat(MAX_LENGTH);
            Description description = new Description(maxLengthDesc);
            assertEquals(maxLengthDesc, description.toString());
        }

        @Test
        @DisplayName("Should create description with one less than max length")
        void shouldCreateDescriptionWithOneLessThanMaxLength() {
            String desc = "A".repeat(MAX_LENGTH - 1);
            Description description = new Description(desc);
            assertEquals(desc, description.toString());
        }

        @Test
        @DisplayName("Should throw exception when description exceeds max length")
        void shouldThrowExceptionWhenDescriptionExceedsMaxLength() {
            String tooLongDesc = "A".repeat(MAX_LENGTH + 1);
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Description(tooLongDesc));
            assertEquals("Description has a maximum of 4096 characters", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when description is exactly one over max length")
        void shouldThrowExceptionWhenDescriptionIsOneOverMaxLength() {
            String tooLongDesc = "A".repeat(MAX_LENGTH + 1);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Description(tooLongDesc));
        }
    }

    @Nested
    @DisplayName("Description ToString Tests")
    class DescriptionToStringTests {

        @Test
        @DisplayName("Should return correct string representation")
        void shouldReturnCorrectStringRepresentation() {
            String text = "Test description for toString";
            Description description = new Description(text);
            assertEquals(text, description.toString());
        }

        @Test
        @DisplayName("toString should match stored value")
        void toStringShouldMatchStoredValue() {
            String text = "Another test description";
            Description description = new Description(text);
            String result = description.toString();
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertEquals(text, result);
        }
    }
}
