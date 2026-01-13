package pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.commands.CreateGenreCommand;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.CommandHandler;

/**
 * Command handler for creating a new genre.
 */
@Component
@RequiredArgsConstructor
public class CreateGenreCommandHandler implements CommandHandler<CreateGenreCommand, Genre> {

    private final GenreRepository genreRepository;

    @Override
    public Genre handle(CreateGenreCommand command) {
        Genre genre = new Genre(command.getName());
        return genreRepository.save(genre);
    }
}
