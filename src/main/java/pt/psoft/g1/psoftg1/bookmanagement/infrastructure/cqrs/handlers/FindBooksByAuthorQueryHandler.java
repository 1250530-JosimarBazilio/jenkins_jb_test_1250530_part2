package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.queries.FindBooksByAuthorQuery;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

import java.util.List;

/**
 * Query handler for finding books by author name.
 * 
 * This handler processes FindBooksByAuthorQuery and returns all matching books.
 * Following CQRS principles, this handler only reads data and never modifies
 * it.
 */
@Component
@RequiredArgsConstructor
public class FindBooksByAuthorQueryHandler implements QueryHandler<FindBooksByAuthorQuery, List<Book>> {

    private static final Logger log = LoggerFactory.getLogger(FindBooksByAuthorQueryHandler.class);

    private final BookRepository bookRepository;

    @Override
    public List<Book> handle(FindBooksByAuthorQuery query) {
        log.debug("Handling FindBooksByAuthorQuery for author: {}", query.getAuthorName());

        return bookRepository.findByAuthorName(query.getAuthorName() + "%");
    }
}
