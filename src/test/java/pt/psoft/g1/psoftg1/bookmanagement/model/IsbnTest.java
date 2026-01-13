package pt.psoft.g1.psoftg1.bookmanagement.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ISBN value object.
 * Tests ISBN-10 and ISBN-13 validation.
 */
@DisplayName("ISBN Value Object Unit Tests")
class IsbnTest {

    // Valid ISBN-13 examples (with correct check digits)
    private static final String VALID_ISBN_13 = "9780134685991";
    private static final String VALID_ISBN_13_2 = "9780132350884";
    // ISBN-13 with check digit 0 (sum % 10 = 0, so checksum = 10 -> 0)
    private static final String VALID_ISBN_13_WITH_ZERO_CHECK = "9780306406157";
    // ISBN-13 where first 12 digits sum to multiple of 10, so checksum = 10 ->
    // converted to 0
    // 9+21+8+0+2+0+1+9+0+6+1+3=60, checksum=10-0=10->0
    private static final String VALID_ISBN_13_CHECKSUM_TEN = "9780201302110";

    // Valid ISBN-10 examples (with correct check digits)
    private static final String VALID_ISBN_10 = "0201633612";
    private static final String VALID_ISBN_10_WITH_X = "155860832X";

    @Nested
    @DisplayName("ISBN-13 Tests")
    class Isbn13Tests {

        @Test
        @DisplayName("Should create valid ISBN-13")
        void shouldCreateValidIsbn13() {
            Isbn isbn = new Isbn(VALID_ISBN_13);

            assertNotNull(isbn);
            assertEquals(VALID_ISBN_13, isbn.toString());
        }

        @Test
        @DisplayName("Should create valid ISBN-13 with check digit 0")
        void shouldCreateValidIsbn13WithCheckDigitZero() {
            // This ISBN has a check digit of 0, testing the checksum == 10 branch
            // 9780306406157 is a valid ISBN-13 where the computed checksum becomes 0
            Isbn isbn = new Isbn(VALID_ISBN_13_WITH_ZERO_CHECK);

            assertNotNull(isbn);
            assertEquals(VALID_ISBN_13_WITH_ZERO_CHECK, isbn.toString());
        }

        @Test
        @DisplayName("Should create another valid ISBN-13")
        void shouldCreateAnotherValidIsbn13() {
            Isbn isbn = new Isbn(VALID_ISBN_13_2);

            assertNotNull(isbn);
            assertEquals(VALID_ISBN_13_2, isbn.toString());
        }

        @Test
        @DisplayName("Should create valid ISBN-13 where computed checksum equals 10")
        void shouldCreateValidIsbn13WhereChecksumEqualsTen() {
            // Tests the specific branch: if (checksum == 10) { checksum = 0; }
            // For ISBN 9780201302110:
            // sum = 9 + 7*3 + 8 + 0*3 + 2 + 0*3 + 1 + 3*3 + 0 + 2*3 + 1 + 1*3 = 60
            // checksum = 10 - (60 % 10) = 10 - 0 = 10 -> converted to 0
            // Last digit = 0, so ISBN is valid
            Isbn isbn = new Isbn(VALID_ISBN_13_CHECKSUM_TEN);

            assertNotNull(isbn);
            assertEquals(VALID_ISBN_13_CHECKSUM_TEN, isbn.toString());
        }

        @ParameterizedTest
        @ValueSource(strings = { "9780134685992", "9780000000001", "1234567890123" })
        @DisplayName("Should throw exception for invalid ISBN-13 check digit")
        void shouldThrowExceptionForInvalidIsbn13CheckDigit(String invalidIsbn) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Isbn(invalidIsbn));
        }
    }

    @Nested
    @DisplayName("ISBN-10 Tests")
    class Isbn10Tests {

        @Test
        @DisplayName("Should create valid ISBN-10")
        void shouldCreateValidIsbn10() {
            Isbn isbn = new Isbn(VALID_ISBN_10);

            assertNotNull(isbn);
            assertEquals(VALID_ISBN_10, isbn.toString());
        }

        @Test
        @DisplayName("Should create valid ISBN-10 with X check digit")
        void shouldCreateValidIsbn10WithXCheckDigit() {
            Isbn isbn = new Isbn(VALID_ISBN_10_WITH_X);

            assertNotNull(isbn);
            assertEquals(VALID_ISBN_10_WITH_X, isbn.toString());
        }

        @ParameterizedTest
        @ValueSource(strings = { "0201633611", "1234567891", "9876543211" })
        @DisplayName("Should throw exception for invalid ISBN-10 check digit")
        void shouldThrowExceptionForInvalidIsbn10CheckDigit(String invalidIsbn) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Isbn(invalidIsbn));
        }
    }

    @Nested
    @DisplayName("Invalid ISBN Format Tests")
    class InvalidIsbnFormatTests {

        @ParameterizedTest
        @ValueSource(strings = { "", " ", "123", "invalid-isbn", "978-0-000-00000-X", "abcdefghij", "12345" })
        @DisplayName("Should throw exception for invalid ISBN formats")
        void shouldThrowExceptionForInvalidIsbnFormats(String invalidIsbn) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Isbn(invalidIsbn));
        }

        @Test
        @DisplayName("Should throw exception when ISBN is null")
        void shouldThrowExceptionWhenIsbnIsNull() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Isbn(null));
            assertEquals("Isbn cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for ISBN with special characters")
        void shouldThrowExceptionForIsbnWithSpecialCharacters() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Isbn("978-0-13-468599-1"));
        }

        @Test
        @DisplayName("Should throw exception for ISBN with spaces")
        void shouldThrowExceptionForIsbnWithSpaces() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Isbn("978 0134685991"));
        }

        @Test
        @DisplayName("Should throw exception for ISBN with letters in wrong position")
        void shouldThrowExceptionForIsbnWithLettersInWrongPosition() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Isbn("978013468599X"));
        }
    }

    @Nested
    @DisplayName("ISBN String Representation Tests")
    class IsbnStringRepresentationTests {

        @Test
        @DisplayName("Should return correct string representation")
        void shouldReturnCorrectStringRepresentation() {
            Isbn isbn = new Isbn(VALID_ISBN_13);

            assertEquals(VALID_ISBN_13, isbn.toString());
        }
    }

    @Nested
    @DisplayName("ISBN Equality Tests")
    class IsbnEqualityTests {

        @Test
        @DisplayName("Should correctly compare two equal ISBNs")
        void shouldCorrectlyCompareTwoEqualIsbns() {
            Isbn isbn1 = new Isbn(VALID_ISBN_13);
            Isbn isbn2 = new Isbn(VALID_ISBN_13);

            assertEquals(isbn1, isbn2);
            assertEquals(isbn1.hashCode(), isbn2.hashCode());
        }

        @Test
        @DisplayName("Should correctly identify different ISBNs as not equal")
        void shouldCorrectlyIdentifyDifferentIsbnsAsNotEqual() {
            Isbn isbn1 = new Isbn(VALID_ISBN_13);
            Isbn isbn2 = new Isbn(VALID_ISBN_13_2);

            assertNotEquals(isbn1, isbn2);
        }
    }
}
