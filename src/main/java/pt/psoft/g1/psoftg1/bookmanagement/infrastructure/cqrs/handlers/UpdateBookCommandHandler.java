package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.commands.UpdateBookCommand;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.bookmanagement.services.UpdateBookRequest;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.CommandHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Command handler for updating an existing book.
 * 
 * This handler processes UpdateBookCommand by:
 * 1. Retrieving the existing book
 * 2. Validating authors and genre
 * 3. Applying the patch with optimistic locking
 * 4. Persisting the updated book
 */
@Component
@RequiredArgsConstructor
public class UpdateBookCommandHandler implements CommandHandler<UpdateBookCommand, Book> {

    private static final Logger log = LoggerFactory.getLogger(UpdateBookCommandHandler.class);

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;

    @Override
    public Book handle(UpdateBookCommand command) {
        log.info("Handling UpdateBookCommand for ISBN: {}", command.getIsbn());

        // Find existing book
        Book book = bookRepository.findByIsbn(command.getIsbn())
                .orElseThrow(() -> new NotFoundException(Book.class, command.getIsbn()));

        // Build update request
        UpdateBookRequest request = new UpdateBookRequest();
        request.setIsbn(command.getIsbn());
        request.setTitle(command.getTitle());
        request.setDescription(command.getDescription());

        // Handle authors
        if (command.getAuthorNumbers() != null) {
            List<Author> authors = new ArrayList<>();
            for (Long authorNumber : command.getAuthorNumbers()) {
                Optional<Author> temp = authorRepository.findByAuthorNumber(authorNumber);
                temp.ifPresent(authors::add);
            }
            request.setAuthorObjList(authors);
        }

        // Handle photo
        MultipartFile photo = command.getPhoto();
        String photoURI = command.getPhotoURI();
        if (photo == null && photoURI != null || photo != null && photoURI == null) {
            request.setPhoto(null);
            request.setPhotoURI(null);
        } else {
            request.setPhoto(photo);
            request.setPhotoURI(photoURI);
        }

        // Handle genre
        if (command.getGenre() != null) {
            Optional<Genre> genre = genreRepository.findByString(command.getGenre());
            if (genre.isEmpty()) {
                throw new NotFoundException("Genre not found");
            }
            request.setGenreObj(genre.get());
        }

        // Apply patch with optimistic locking
        book.applyPatch(Long.parseLong(command.getCurrentVersion()), request);

        // Persist and return
        return bookRepository.save(book);
    }
}
