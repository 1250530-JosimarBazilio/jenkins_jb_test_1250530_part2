package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries.FindBooksByGenreQuery;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

import java.util.List;

/**
 * Query handler for finding books by genre.
 * 
 * This handler processes FindBooksByGenreQuery and returns all matching books.
 * Following CQRS principles, this handler only reads data and never modifies
 * it.
 */
@Component
@RequiredArgsConstructor
public class FindBooksByGenreQueryHandler implements QueryHandler<FindBooksByGenreQuery, List<Book>> {

    private static final Logger log = LoggerFactory.getLogger(FindBooksByGenreQueryHandler.class);

    private final BookRepository bookRepository;

    @Override
    public List<Book> handle(FindBooksByGenreQuery query) {
        log.debug("Handling FindBooksByGenreQuery for genre: {}", query.getGenre());

        return bookRepository.findByGenre(query.getGenre());
    }
}
