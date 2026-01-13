package pt.psoft.g1.psoftg1.genremanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.queries.GetAllGenresQuery;
import pt.psoft.g1.psoftg1.genremanagement.infrastructure.cqrs.queries.GetGenreByNameQuery;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryBus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * REST Controller for Genre Management using CQRS pattern.
 * 
 * This controller uses the QueryBus to dispatch queries to their respective
 * handlers,
 * following the CQRS (Command Query Responsibility Segregation) pattern.
 */
@Tag(name = "Genres CQRS", description = "Endpoints for managing Genres using CQRS pattern")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/genres")
public class GenreCqrsController {

    private final QueryBus queryBus;
    private final GenreViewMapper genreViewMapper;

    /**
     * Gets all genres using CQRS Query pattern.
     */
    @Operation(summary = "Get all genres (CQRS)")
    @GetMapping
    public ResponseEntity<List<GenreView>> getAllGenres() {
        GetAllGenresQuery query = new GetAllGenresQuery();
        Iterable<Genre> genres = queryBus.dispatch(query);

        List<GenreView> genreViews = StreamSupport.stream(genres.spliterator(), false)
                .map(genreViewMapper::toGenreView)
                .collect(Collectors.toList());

        return ResponseEntity.ok(genreViews);
    }

    /**
     * Gets a genre by name using CQRS Query pattern.
     */
    @Operation(summary = "Get genre by name (CQRS)")
    @GetMapping("/{name}")
    public ResponseEntity<GenreView> getGenreByName(@PathVariable String name) {
        GetGenreByNameQuery query = new GetGenreByNameQuery(name);
        Optional<Genre> genre = queryBus.dispatch(query);

        return genre
                .map(g -> ResponseEntity.ok(genreViewMapper.toGenreView(g)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Health check endpoint.
     */
    @Operation(summary = "Health check endpoint")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Genres CQRS API is running");
    }
}
