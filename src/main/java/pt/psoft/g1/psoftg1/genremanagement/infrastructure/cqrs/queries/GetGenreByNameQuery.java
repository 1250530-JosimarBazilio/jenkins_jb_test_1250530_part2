package pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.queries;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Query;

import java.util.Optional;

/**
 * Query to find a genre by its name.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetGenreByNameQuery implements Query<Optional<Genre>> {

    private String name;
}
