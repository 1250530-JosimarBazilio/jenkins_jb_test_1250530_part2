package pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.queries.GetGenreByNameQuery;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryHandler;

import java.util.Optional;

/**
 * Query handler for finding a genre by name.
 */
@Component
@RequiredArgsConstructor
public class GetGenreByNameQueryHandler implements QueryHandler<GetGenreByNameQuery, Optional<Genre>> {

    private final GenreRepository genreRepository;

    @Override
    public Optional<Genre> handle(GetGenreByNameQuery query) {
        return genreRepository.findByString(query.getName());
    }
}
