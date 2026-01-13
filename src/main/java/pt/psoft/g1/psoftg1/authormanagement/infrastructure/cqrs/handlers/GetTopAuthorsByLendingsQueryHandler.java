package pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.psoft.g1.psoftg1.authormanagement.api.AuthorLendingView;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries.GetTopAuthorsByLendingsQuery;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

import java.util.List;

/**
 * Query handler for getting top authors by number of lendings.
 * 
 * This handler processes GetTopAuthorsByLendingsQuery and returns the top
 * authors
 * sorted by lending count. Query handlers should never modify the state of the
 * system.
 */
@Component
@RequiredArgsConstructor
public class GetTopAuthorsByLendingsQueryHandler
        implements QueryHandler<GetTopAuthorsByLendingsQuery, List<AuthorLendingView>> {

    private static final Logger log = LoggerFactory.getLogger(GetTopAuthorsByLendingsQueryHandler.class);

    private final AuthorRepository authorRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AuthorLendingView> handle(GetTopAuthorsByLendingsQuery query) {
        log.debug("Handling GetTopAuthorsByLendingsQuery with limit: {}", query.getLimit());
        Pageable pageable = PageRequest.of(0, query.getLimit());
        return authorRepository.findTopAuthorByLendings(pageable).getContent();
    }
}
