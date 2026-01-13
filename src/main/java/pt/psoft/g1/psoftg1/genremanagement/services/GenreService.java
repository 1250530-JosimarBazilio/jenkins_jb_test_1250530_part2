package pt.psoft.g1.psoftg1.genremanagement.services;

import pt.psoft.g1.psoftg1.genremanagement.model.Genre;

import java.util.Optional;

/**
 * Service interface for Genre management.
 */
public interface GenreService {

    Iterable<Genre> findAll();

    Genre save(Genre genre);

    Optional<Genre> findByString(String name);
}
