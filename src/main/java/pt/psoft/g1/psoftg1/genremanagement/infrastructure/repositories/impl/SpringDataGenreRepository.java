package pt.psoft.g1.psoftg1.genremanagement.infrastructure.repositories.impl;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA implementation of GenreRepository for Books Microservice.
 */
public interface SpringDataGenreRepository extends GenreRepository, CrudRepository<Genre, Integer> {

    @Query("SELECT g FROM Genre g")
    List<Genre> findAllGenres();

    @Override
    @Query("SELECT g FROM Genre g WHERE g.genre = :genreName")
    Optional<Genre> findByString(@Param("genreName") @NotNull String genre);
}
