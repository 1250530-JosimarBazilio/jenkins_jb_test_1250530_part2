package pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Command;

/**
 * Command to create a new genre.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateGenreCommand implements Command<Genre> {

    private String name;
}
