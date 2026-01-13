package pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.queries;

import lombok.Data;
import lombok.NoArgsConstructor;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Query;

/**
 * Query to get all genres.
 */
@Data
@NoArgsConstructor
public class GetAllGenresQuery implements Query<Iterable<Genre>> {
}
