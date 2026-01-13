package pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries.GetCoAuthorsByAuthorNumberQuery;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

import java.util.List;

/**
 * Query handler for finding co-authors of a specific author.
 * 
 * This handler processes GetCoAuthorsByAuthorNumberQuery and returns
 * all co-authors who have written books together with the specified author.
 * Query handlers should never modify the state of the system.
 */
@Component
@RequiredArgsConstructor
public class GetCoAuthorsByAuthorNumberQueryHandler
        implements QueryHandler<GetCoAuthorsByAuthorNumberQuery, List<Author>> {

    private static final Logger log = LoggerFactory.getLogger(GetCoAuthorsByAuthorNumberQueryHandler.class);

    private final AuthorRepository authorRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Author> handle(GetCoAuthorsByAuthorNumberQuery query) {
        log.debug("Handling GetCoAuthorsByAuthorNumberQuery for author number: {}", query.getAuthorNumber());
        return authorRepository.findCoAuthorsByAuthorNumber(query.getAuthorNumber());
    }
}
