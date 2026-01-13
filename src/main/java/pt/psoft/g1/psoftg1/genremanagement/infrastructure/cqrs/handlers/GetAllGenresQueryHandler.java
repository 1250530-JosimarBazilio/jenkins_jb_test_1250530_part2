package pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.queries.GetAllGenresQuery;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

/**
 * Query handler for getting all genres.
 */
@Component
@RequiredArgsConstructor
public class GetAllGenresQueryHandler implements QueryHandler<GetAllGenresQuery, Iterable<Genre>> {

    private final GenreRepository genreRepository;

    @Override
    public Iterable<Genre> handle(GetAllGenresQuery query) {
        return genreRepository.findAll();
    }
}
