package pt.psoft.g1.psoftg1.genremanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.psoft.g1.psoftg1.genremanagement.services.GenreService;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * REST Controller for Genre Management in Books Microservice.
 */
@Tag(name = "Genres", description = "Endpoints for managing Genres")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/genres")
public class GenreController {

    private final GenreService genreService;
    private final GenreViewMapper genreViewMapper;

    @Operation(summary = "Get all genres")
    @GetMapping
    public ResponseEntity<List<GenreView>> getAllGenres() {
        var genres = genreService.findAll();
        List<GenreView> genreViews = StreamSupport.stream(genres.spliterator(), false)
                .map(genreViewMapper::toGenreView)
                .collect(Collectors.toList());
        return ResponseEntity.ok(genreViews);
    }

    @Operation(summary = "Get genre by name")
    @GetMapping("/{name}")
    public ResponseEntity<GenreView> getGenreByName(@PathVariable String name) {
        return genreService.findByString(name)
                .map(genre -> ResponseEntity.ok(genreViewMapper.toGenreView(genre)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Health check endpoint")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Genres API is running");
    }
}
