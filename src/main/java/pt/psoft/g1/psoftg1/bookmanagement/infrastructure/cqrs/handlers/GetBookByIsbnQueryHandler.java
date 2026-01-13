package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries.GetBookByIsbnQuery;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

/**
 * Query handler for retrieving a book by its ISBN.
 * 
 * This handler processes GetBookByIsbnQuery and returns the corresponding book.
 * Following CQRS principles, this handler only reads data and never modifies
 * it.
 */
@Component
@RequiredArgsConstructor
public class GetBookByIsbnQueryHandler implements QueryHandler<GetBookByIsbnQuery, Book> {

    private static final Logger log = LoggerFactory.getLogger(GetBookByIsbnQueryHandler.class);

    private final BookRepository bookRepository;

    @Override
    public Book handle(GetBookByIsbnQuery query) {
        log.debug("Handling GetBookByIsbnQuery for ISBN: {}", query.getIsbn());

        return bookRepository.findByIsbn(query.getIsbn())
                .orElseThrow(() -> new NotFoundException(Book.class, query.getIsbn()));
    }
}
