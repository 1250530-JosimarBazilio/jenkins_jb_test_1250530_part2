package pt.psoft.g1.psoftg1.shared.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the StringUtilsCustom utility class.
 */
@DisplayName("StringUtilsCustom Tests")
class StringUtilsCustomTest {

    @Nested
    @DisplayName("sanitizeHtml Tests")
    class SanitizeHtmlTests {

        @Test
        @DisplayName("Should return null when input is null")
        void shouldReturnNullWhenInputIsNull() {
            String result = StringUtilsCustom.sanitizeHtml(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Should return empty string when input is empty")
        void shouldReturnEmptyStringWhenInputIsEmpty() {
            String result = StringUtilsCustom.sanitizeHtml("");
            assertEquals("", result);
        }

        @Test
        @DisplayName("Should preserve plain text")
        void shouldPreservePlainText() {
            String input = "Hello, World!";
            String result = StringUtilsCustom.sanitizeHtml(input);
            assertEquals(input, result);
        }

        @Test
        @DisplayName("Should preserve allowed HTML tags")
        void shouldPreserveAllowedHtmlTags() {
            String input = "<p>Paragraph</p>";
            String result = StringUtilsCustom.sanitizeHtml(input);
            assertTrue(result.contains("<p>"));
            assertTrue(result.contains("</p>"));
        }

        @Test
        @DisplayName("Should preserve bold tags")
        void shouldPreserveBoldTags() {
            String input = "<b>Bold</b> and <strong>Strong</strong>";
            String result = StringUtilsCustom.sanitizeHtml(input);
            assertTrue(result.contains("<b>"));
            assertTrue(result.contains("<strong>"));
        }

        @Test
        @DisplayName("Should preserve italic tags")
        void shouldPreserveItalicTags() {
            String input = "<i>Italic</i> and <em>Emphasis</em>";
            String result = StringUtilsCustom.sanitizeHtml(input);
            assertTrue(result.contains("<i>"));
            assertTrue(result.contains("<em>"));
        }

        @Test
        @DisplayName("Should strip dangerous script tags")
        void shouldStripDangerousScriptTags() {
            String input = "<script>alert('XSS')</script>Safe text";
            String result = StringUtilsCustom.sanitizeHtml(input);
            assertFalse(result.contains("<script>"));
            assertFalse(result.contains("alert"));
            assertTrue(result.contains("Safe text"));
        }

        @Test
        @DisplayName("Should strip onclick attributes")
        void shouldStripOnclickAttributes() {
            String input = "<p onclick='alert(1)'>Text</p>";
            String result = StringUtilsCustom.sanitizeHtml(input);
            assertFalse(result.contains("onclick"));
            assertTrue(result.contains("<p>"));
        }

        @Test
        @DisplayName("Should strip disallowed tags")
        void shouldStripDisallowedTags() {
            String input = "<div>Content</div>";
            String result = StringUtilsCustom.sanitizeHtml(input);
            assertFalse(result.contains("<div>"));
            assertTrue(result.contains("Content"));
        }

        @Test
        @DisplayName("Should handle whitespace-only input")
        void shouldHandleWhitespaceOnlyInput() {
            String input = "   ";
            String result = StringUtilsCustom.sanitizeHtml(input);
            assertNotNull(result);
        }
    }
}
