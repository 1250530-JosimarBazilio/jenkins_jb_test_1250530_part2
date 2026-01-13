package pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries.GetAuthorByNumberQuery;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

import java.util.Optional;

/**
 * Query handler for retrieving an author by number.
 * 
 * This handler processes GetAuthorByNumberQuery and returns the author
 * if found. Query handlers should never modify the state of the system.
 */
@Component
@RequiredArgsConstructor
public class GetAuthorByNumberQueryHandler implements QueryHandler<GetAuthorByNumberQuery, Optional<Author>> {

    private static final Logger log = LoggerFactory.getLogger(GetAuthorByNumberQueryHandler.class);

    private final AuthorRepository authorRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Author> handle(GetAuthorByNumberQuery query) {
        log.debug("Handling GetAuthorByNumberQuery for author number: {}", query.getAuthorNumber());
        return authorRepository.findByAuthorNumber(query.getAuthorNumber());
    }
}
