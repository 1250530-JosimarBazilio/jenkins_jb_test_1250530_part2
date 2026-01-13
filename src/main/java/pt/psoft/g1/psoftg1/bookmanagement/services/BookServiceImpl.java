package pt.psoft.g1.psoftg1.bookmanagement.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.saga.CreateBookSagaOrchestrator;
import pt.psoft.g1.psoftg1.bookmanagement.model.*;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import lombok.RequiredArgsConstructor;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
// import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
// import pt.psoft.g1.psoftg1.readermanagement.repositories.ReaderRepository;
// import pt.psoft.g1.psoftg1.shared.repositories.PhotoRepository;
// import pt.psoft.g1.psoftg1.shared.services.Page;

//import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Book Service Implementation.
 * 
 * Uses the Saga Pattern for book creation to ensure:
 * - Atomic operations with compensating transactions
 * - Reliable event publishing via Outbox Pattern
 * - Proper error handling and rollback
 */
@Service
@RequiredArgsConstructor
@PropertySource({ "classpath:config/library.properties" })
public class BookServiceImpl implements BookService {

	private static final Logger log = LoggerFactory.getLogger(BookServiceImpl.class);

	private final BookRepository bookRepository;
	private final GenreRepository genreRepository;
	private final AuthorRepository authorRepository;
	private final CreateBookSagaOrchestrator createBookSagaOrchestrator;
	// private final PhotoRepository photoRepository;
	// private final ReaderRepository readerRepository;

	@Value("${suggestionsLimitPerGenre}")
	private long suggestionsLimitPerGenre;

	/**
	 * Creates a new book using the Saga Pattern.
	 * 
	 * The saga orchestrator handles:
	 * 1. Validation (ISBN uniqueness, genre existence, author validity)
	 * 2. Book entity creation
	 * 3. Event publishing via Outbox Pattern
	 * 
	 * If any step fails, previous steps are compensated (rolled back).
	 * 
	 * @param request The book creation request
	 * @param isbn    The ISBN for the new book
	 * @return The created Book
	 */
	@Override
	public Book create(CreateBookRequest request, String isbn) {
		log.info("Creating book with ISBN: {} using Saga Pattern", isbn);

		MultipartFile photo = request.getPhoto();
		String photoURI = request.getPhotoURI();
		if (photo == null && photoURI != null || photo != null && photoURI == null) {
			request.setPhoto(null);
			request.setPhotoURI(null);
		}

		// Delegate to Saga Orchestrator for atomic operation with compensation
		return createBookSagaOrchestrator.execute(request, isbn, photoURI);
	}

	@Override
	public Book update(UpdateBookRequest request, String currentVersion) {

		var book = findByIsbn(request.getIsbn());
		if (request.getAuthors() != null) {
			List<Long> authorNumbers = request.getAuthors();
			List<Author> authors = new ArrayList<>();
			for (Long authorNumber : authorNumbers) {
				Optional<Author> temp = authorRepository.findByAuthorNumber(authorNumber);
				if (temp.isEmpty()) {
					continue;
				}
				Author author = temp.get();
				authors.add(author);
			}

			request.setAuthorObjList(authors);
		}

		MultipartFile photo = request.getPhoto();
		String photoURI = request.getPhotoURI();
		if (photo == null && photoURI != null || photo != null && photoURI == null) {
			request.setPhoto(null);
			request.setPhotoURI(null);
		}

		if (request.getGenre() != null) {
			Optional<Genre> genre = genreRepository.findByString(request.getGenre());
			if (genre.isEmpty()) {
				throw new NotFoundException("Genre not found");
			}
			request.setGenreObj(genre.get());
		}

		book.applyPatch(Long.parseLong(currentVersion), request);

		bookRepository.save(book);

		return book;
	}

	@Override
	public Book save(Book book) {
		return this.bookRepository.save(book);
	}

	// @Override
	// public List<BookCountDTO> findTop5BooksLent(){
	// LocalDate oneYearAgo = LocalDate.now().minusYears(1);
	// Pageable pageableRules = PageRequest.of(0,5);
	// return this.bookRepository.findTop5BooksLent(oneYearAgo,
	// pageableRules).getContent();
	// }

	// @Override
	// public Book removeBookPhoto(String isbn, long desiredVersion) {
	// Book book = this.findByIsbn(isbn);
	// String photoFile;
	// try {
	// photoFile = book.getPhoto().getPhotoFile();
	// }catch (NullPointerException e){
	// throw new NotFoundException("Book did not have a photo assigned to it.");
	// }

	// book.removePhoto(desiredVersion);
	// var updatedBook = bookRepository.save(book);
	// photoRepository.deleteByPhotoFile(photoFile);
	// return updatedBook;
	// }

	@Override
	public List<Book> findByGenre(String genre) {
		return this.bookRepository.findByGenre(genre);
	}

	public List<Book> findByTitle(String title) {
		return bookRepository.findByTitle(title);
	}

	@Override
	public List<Book> findByAuthorName(String authorName) {
		return bookRepository.findByAuthorName(authorName + "%");
	}

	public Book findByIsbn(String isbn) {
		return this.bookRepository.findByIsbn(isbn)
				.orElseThrow(() -> new NotFoundException(Book.class, isbn));
	}

	// public List<Book> getBooksSuggestionsForReader(String readerNumber) {
	// List<Book> books = new ArrayList<>();

	// ReaderDetails readerDetails =
	// readerRepository.findByReaderNumber(readerNumber)
	// .orElseThrow(() -> new NotFoundException("Reader not found with provided
	// login"));
	// List<Genre> interestList = readerDetails.getInterestList();

	// if(interestList.isEmpty()) {
	// throw new NotFoundException("Reader has no interests");
	// }

	// for(Genre genre : interestList) {
	// List<Book> tempBooks = bookRepository.findByGenre(genre.toString());
	// if(tempBooks.isEmpty()) {
	// continue;
	// }

	// long genreBookCount = 0;

	// for (Book loopBook : tempBooks) {
	// if (genreBookCount >= suggestionsLimitPerGenre) {
	// break;
	// }

	// books.add(loopBook);
	// genreBookCount++;
	// }
	// }

	// return books;
	// }

	// @Override
	// public List<Book> searchBooks(Page page, SearchBooksQuery query) {
	// if (page == null) {
	// page = new Page(1, 10);
	// }
	// if (query == null) {
	// query = new SearchBooksQuery("", "", "");
	// }
	// return bookRepository.searchBooks(page, query);
	// }
}
