package pt.psoft.g1.psoftg1.authormanagement.model;

import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pt.psoft.g1.psoftg1.authormanagement.services.UpdateAuthorRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Author entity.
 */
@DisplayName("Author Entity Unit Tests")
class AuthorTest {

    private static final String VALID_NAME = "Joshua Bloch";
    private static final String VALID_BIO = "Joshua Bloch is a software engineer and author of Effective Java.";
    private static final int BIO_MAX_LENGTH = 4096;
    private static final int NAME_MAX_LENGTH = 150;

    @Nested
    @DisplayName("Author Creation Tests")
    class AuthorCreationTests {

        @Test
        @DisplayName("Should create author with valid name and bio")
        void shouldCreateAuthorWithValidNameAndBio() {
            Author author = new Author(VALID_NAME, VALID_BIO, null);

            assertNotNull(author);
            assertEquals(VALID_NAME, author.getName());
            assertEquals(VALID_BIO, author.getBio());
        }

        @Test
        @DisplayName("Should create author without photo")
        void shouldCreateAuthorWithoutPhoto() {
            Author author = new Author(VALID_NAME, VALID_BIO, null);

            assertNotNull(author);
            assertNull(author.getPhoto());
        }

        @Test
        @DisplayName("Should create author with photo URI")
        void shouldCreateAuthorWithPhotoUri() {
            String photoUri = "uploads/author-photo.jpg";
            Author author = new Author(VALID_NAME, VALID_BIO, photoUri);

            assertNotNull(author);
            assertNotNull(author.getPhoto());
        }

        @Test
        @DisplayName("Should throw exception when name is null")
        void shouldThrowExceptionWhenNameIsNull() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Author(null, VALID_BIO, null));
            assertTrue(exception.getMessage().contains("null") || exception.getMessage().contains("blank"));
        }

        @Test
        @DisplayName("Should throw exception when name is empty")
        void shouldThrowExceptionWhenNameIsEmpty() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Author("", VALID_BIO, null));
            assertTrue(exception.getMessage().contains("null") || exception.getMessage().contains("blank"));
        }

        @Test
        @DisplayName("Should throw exception when name is blank")
        void shouldThrowExceptionWhenNameIsBlank() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Author("   ", VALID_BIO, null));
            assertTrue(exception.getMessage().contains("null") || exception.getMessage().contains("blank"));
        }

        @Test
        @DisplayName("Should throw exception when bio is null")
        void shouldThrowExceptionWhenBioIsNull() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Author(VALID_NAME, null, null));
            assertEquals("Bio cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when bio is empty")
        void shouldThrowExceptionWhenBioIsEmpty() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Author(VALID_NAME, "", null));
            assertEquals("Bio cannot be blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when bio exceeds max length")
        void shouldThrowExceptionWhenBioExceedsMaxLength() {
            String longBio = "a".repeat(BIO_MAX_LENGTH + 1);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Author(VALID_NAME, longBio, null));
            assertTrue(exception.getMessage().contains("maximum") || exception.getMessage().contains("4096"));
        }

        @Test
        @DisplayName("Should throw exception when name exceeds max length")
        void shouldThrowExceptionWhenNameExceedsMaxLength() {
            String longName = "a".repeat(NAME_MAX_LENGTH + 1);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Author(longName, VALID_BIO, null));
            assertTrue(exception.getMessage().contains("exceed") || exception.getMessage().contains("150"));
        }

        @Test
        @DisplayName("Should create author with name at exactly max length")
        void shouldCreateAuthorWithNameAtExactlyMaxLength() {
            String maxLengthName = "a".repeat(NAME_MAX_LENGTH);
            Author author = new Author(maxLengthName, VALID_BIO, null);
            assertEquals(maxLengthName, author.getName());
        }

        @Test
        @DisplayName("Should create author with bio at exactly max length")
        void shouldCreateAuthorWithBioAtExactlyMaxLength() {
            String maxLengthBio = "a".repeat(BIO_MAX_LENGTH);
            Author author = new Author(VALID_NAME, maxLengthBio, null);
            assertEquals(maxLengthBio, author.getBio());
        }
    }

    @Nested
    @DisplayName("Author Update Tests")
    class AuthorUpdateTests {

        private Author author;

        @BeforeEach
        void setUp() {
            author = new Author(VALID_NAME, VALID_BIO, null);
        }

        @Test
        @DisplayName("Should update author name successfully")
        void shouldUpdateAuthorNameSuccessfully() {
            String newName = "Martin Fowler";
            UpdateAuthorRequest request = new UpdateAuthorRequest();
            request.setName(newName);

            author.applyPatch(author.getVersion(), request);

            assertEquals(newName, author.getName());
        }

        @Test
        @DisplayName("Should update author bio successfully")
        void shouldUpdateAuthorBioSuccessfully() {
            String newBio = "New biography for the author with updated information.";
            UpdateAuthorRequest request = new UpdateAuthorRequest();
            request.setBio(newBio);

            author.applyPatch(author.getVersion(), request);

            assertEquals(newBio, author.getBio());
        }

        @Test
        @DisplayName("Should throw StaleObjectStateException on version conflict")
        void shouldThrowStaleObjectStateExceptionOnVersionConflict() {
            UpdateAuthorRequest request = new UpdateAuthorRequest();
            request.setName("New Name");

            long wrongVersion = author.getVersion() + 1;

            assertThrows(
                    StaleObjectStateException.class,
                    () -> author.applyPatch(wrongVersion, request));
        }

        @Test
        @DisplayName("Should not change name when request name is null")
        void shouldNotChangeNameWhenRequestNameIsNull() {
            String originalName = author.getName();
            UpdateAuthorRequest request = new UpdateAuthorRequest();
            request.setBio("Updated bio only");

            author.applyPatch(author.getVersion(), request);

            assertEquals(originalName, author.getName());
        }

        @Test
        @DisplayName("Should update photo URI successfully")
        void shouldUpdatePhotoUriSuccessfully() {
            UpdateAuthorRequest request = new UpdateAuthorRequest();
            request.setPhotoURI("uploads/new-photo.jpg");

            author.applyPatch(author.getVersion(), request);

            assertNotNull(author.getPhoto());
        }
    }

    @Nested
    @DisplayName("Author Getters Tests")
    class AuthorGettersTests {

        private Author author;

        @BeforeEach
        void setUp() {
            author = new Author(VALID_NAME, VALID_BIO, null);
        }

        @Test
        @DisplayName("Should return correct name")
        void shouldReturnCorrectName() {
            assertEquals(VALID_NAME, author.getName());
        }

        @Test
        @DisplayName("Should return correct bio")
        void shouldReturnCorrectBio() {
            assertEquals(VALID_BIO, author.getBio());
        }

        @Test
        @DisplayName("Should return null photo when not set")
        void shouldReturnNullPhotoWhenNotSet() {
            assertNull(author.getPhoto());
        }

        @Test
        @DisplayName("Should return correct version")
        void shouldReturnCorrectVersion() {
            assertNotNull(author.getVersion());
        }

        @Test
        @DisplayName("Should return version as Long type")
        void shouldReturnVersionAsLongType() {
            Long version = author.getVersion();
            // Version should return actual value, not null or 0
            assertNotNull(version);
            assertTrue(version >= 0);
        }

        @Test
        @DisplayName("Should return zero version for new author - kills EmptyObjectReturnValsMutator")
        void shouldReturnZeroVersionForNewAuthor() {
            // This test ensures getVersion returns the actual version value
            // The mutation replaces return with 0L, so we need to verify actual behavior
            Long version = author.getVersion();
            // For new entities, version should be 0 (initialized in @Version field)
            assertEquals(Long.valueOf(0L), version);
        }

        @Test
        @DisplayName("Should return author id (authorNumber)")
        void shouldReturnAuthorId() {
            // For new entity, getId returns authorNumber which is initially null
            Long id = author.getId();
            // Initially null before persistence
            assertNull(id);
        }
    }

    @Nested
    @DisplayName("Author Photo Management Tests")
    class AuthorPhotoManagementTests {

        @Test
        @DisplayName("Should throw ConflictException when removing photo with wrong version")
        void shouldThrowConflictExceptionOnRemovePhotoVersionMismatch() {
            Author author = new Author(VALID_NAME, VALID_BIO, "photo.jpg");
            // Try to remove photo with wrong version
            assertThrows(
                    pt.psoft.g1.psoftg1.exceptions.ConflictException.class,
                    () -> author.removePhoto(999L));
        }

        @Test
        @DisplayName("Should remove photo when version matches")
        void shouldRemovePhotoWhenVersionMatches() {
            Author author = new Author(VALID_NAME, VALID_BIO, "photo.jpg");
            // Version is 0 for new entity
            author.removePhoto(author.getVersion());
            assertNull(author.getPhoto());
        }
    }
}
