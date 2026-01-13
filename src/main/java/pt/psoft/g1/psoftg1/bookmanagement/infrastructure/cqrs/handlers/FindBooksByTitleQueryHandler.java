package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries.FindBooksByTitleQuery;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

import java.util.List;

/**
 * Query handler for finding books by title.
 * 
 * This handler processes FindBooksByTitleQuery and returns all matching books.
 * Following CQRS principles, this handler only reads data and never modifies
 * it.
 */
@Component
@RequiredArgsConstructor
public class FindBooksByTitleQueryHandler implements QueryHandler<FindBooksByTitleQuery, List<Book>> {

    private static final Logger log = LoggerFactory.getLogger(FindBooksByTitleQueryHandler.class);

    private final BookRepository bookRepository;

    @Override
    public List<Book> handle(FindBooksByTitleQuery query) {
        log.debug("Handling FindBooksByTitleQuery for title: {}", query.getTitle());

        return bookRepository.findByTitle(query.getTitle());
    }
}
