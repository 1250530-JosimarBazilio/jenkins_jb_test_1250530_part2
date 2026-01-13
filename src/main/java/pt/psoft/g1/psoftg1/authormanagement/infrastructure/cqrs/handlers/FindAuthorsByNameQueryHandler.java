package pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries.FindAuthorsByNameQuery;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

import java.util.List;

/**
 * Query handler for finding authors by name prefix.
 * 
 * This handler processes FindAuthorsByNameQuery and returns matching authors.
 * Query handlers should never modify the state of the system.
 */
@Component
@RequiredArgsConstructor
public class FindAuthorsByNameQueryHandler implements QueryHandler<FindAuthorsByNameQuery, List<Author>> {

    private static final Logger log = LoggerFactory.getLogger(FindAuthorsByNameQueryHandler.class);

    private final AuthorRepository authorRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Author> handle(FindAuthorsByNameQuery query) {
        log.debug("Handling FindAuthorsByNameQuery for name: {}", query.getName());
        return authorRepository.searchByNameNameStartsWith(query.getName());
    }
}
