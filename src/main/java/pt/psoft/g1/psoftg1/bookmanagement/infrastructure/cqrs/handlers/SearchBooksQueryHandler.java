package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries.SearchBooksQuery;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Query handler for searching books by multiple criteria.
 * 
 * This handler processes SearchBooksQuery and returns books matching
 * any of the provided criteria (title, genre, author name).
 * Results are combined using OR logic and sorted by title.
 */
@Component
@RequiredArgsConstructor
public class SearchBooksQueryHandler implements QueryHandler<SearchBooksQuery, List<Book>> {

    private static final Logger log = LoggerFactory.getLogger(SearchBooksQueryHandler.class);

    private final BookRepository bookRepository;

    @Override
    public List<Book> handle(SearchBooksQuery query) {
        log.debug("Handling SearchBooksQuery - title: {}, genre: {}, author: {}",
                query.getTitle(), query.getGenre(), query.getAuthorName());

        Set<Book> bookSet = new HashSet<>();

        // Search by title
        if (query.getTitle() != null) {
            List<Book> booksByTitle = bookRepository.findByTitle(query.getTitle());
            bookSet.addAll(booksByTitle);
        }

        // Search by genre
        if (query.getGenre() != null) {
            List<Book> booksByGenre = bookRepository.findByGenre(query.getGenre());
            bookSet.addAll(booksByGenre);
        }

        // Search by author name
        if (query.getAuthorName() != null) {
            List<Book> booksByAuthor = bookRepository.findByAuthorName(query.getAuthorName() + "%");
            bookSet.addAll(booksByAuthor);
        }

        // Sort by title and return as list
        return bookSet.stream()
                .sorted(Comparator.comparing(b -> b.getTitle().toString()))
                .collect(Collectors.toList());
    }
}
