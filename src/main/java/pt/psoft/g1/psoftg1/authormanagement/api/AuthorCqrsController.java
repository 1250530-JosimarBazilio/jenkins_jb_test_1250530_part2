package pt.psoft.g1.psoftg1.authormanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.commands.CreateAuthorCommand;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.commands.UpdateAuthorCommand;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries.FindAuthorsByNameQuery;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries.GetAuthorByNumberQuery;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries.GetCoAuthorsByAuthorNumberQuery;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.queries.GetTopAuthorsByLendingsQuery;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.services.CreateAuthorRequest;
import pt.psoft.g1.psoftg1.authormanagement.services.UpdateAuthorRequest;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.shared.api.ListResponse;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.CommandBus;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.QueryBus;
import pt.psoft.g1.psoftg1.shared.services.ConcurrencyService;
import pt.psoft.g1.psoftg1.shared.services.FileStorageService;

import java.util.List;

/**
 * CQRS-based controller for Author management.
 * 
 * This controller uses the Command/Query Bus pattern for handling
 * author operations, providing a clear separation between read and write
 * operations.
 * 
 * Endpoints are versioned under /api/v2/authors to allow coexistence with
 * legacy endpoints.
 */
@Tag(name = "Author CQRS", description = "CQRS-based endpoints for managing Authors (v2)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/authors")
public class AuthorCqrsController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final AuthorViewMapper authorViewMapper;
    private final ConcurrencyService concurrencyService;
    private final FileStorageService fileStorageService;

    // ==================== COMMANDS (Write Operations) ====================

    @Operation(summary = "Creates a new Author (CQRS) with JSON body")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthorView> createFromJson(@Valid @RequestBody CreateAuthorRequest resource) {
        return doCreate(resource);
    }

    @Operation(summary = "Creates a new Author (CQRS) with form data")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthorView> createFromForm(@Valid CreateAuthorRequest resource) {
        return doCreate(resource);
    }

    private ResponseEntity<AuthorView> doCreate(CreateAuthorRequest resource) {
        // Handle photo upload
        resource.setPhotoURI(null);
        MultipartFile file = resource.getPhoto();
        String fileName = this.fileStorageService.getRequestPhoto(file);
        if (fileName != null) {
            resource.setPhotoURI(fileName);
        }

        // Build and dispatch command
        CreateAuthorCommand command = new CreateAuthorCommand(
                resource.getName(),
                resource.getBio(),
                resource.getPhoto(),
                resource.getPhotoURI());

        Author author = commandBus.dispatch(command);

        final var newAuthorUri = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{authorNumber}")
                .buildAndExpand(author.getAuthorNumber())
                .toUri();

        return ResponseEntity.created(newAuthorUri)
                .eTag(Long.toString(author.getVersion()))
                .body(authorViewMapper.toAuthorView(author));
    }

    @Operation(summary = "Updates a specific author (CQRS) with JSON body")
    @PatchMapping(value = "/{authorNumber}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthorView> partialUpdateFromJson(
            @PathVariable("authorNumber") @Parameter(description = "The number of the Author to update") final Long authorNumber,
            final WebRequest request,
            @Valid @RequestBody UpdateAuthorRequest resource) {
        return doPartialUpdate(authorNumber, request, resource);
    }

    @Operation(summary = "Updates a specific author (CQRS) with form data")
    @PatchMapping(value = "/{authorNumber}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthorView> partialUpdateFromForm(
            @PathVariable("authorNumber") @Parameter(description = "The number of the Author to update") final Long authorNumber,
            final WebRequest request,
            @Valid UpdateAuthorRequest resource) {
        return doPartialUpdate(authorNumber, request, resource);
    }

    private ResponseEntity<AuthorView> doPartialUpdate(Long authorNumber, WebRequest request,
            UpdateAuthorRequest resource) {
        final String ifMatchValue = request.getHeader(ConcurrencyService.IF_MATCH);
        if (ifMatchValue == null || ifMatchValue.isEmpty() || ifMatchValue.equals("null")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You must issue a conditional PATCH using 'if-match'");
        }

        // Handle photo upload
        MultipartFile file = resource.getPhoto();
        String fileName = this.fileStorageService.getRequestPhoto(file);
        if (fileName != null) {
            resource.setPhotoURI(fileName);
        }

        // Build and dispatch command
        UpdateAuthorCommand command = new UpdateAuthorCommand(
                authorNumber,
                resource.getName(),
                resource.getBio(),
                resource.getPhoto(),
                resource.getPhotoURI(),
                concurrencyService.getVersionFromIfMatchHeader(ifMatchValue));

        Author author = commandBus.dispatch(command);

        return ResponseEntity.ok()
                .eTag(Long.toString(author.getVersion()))
                .body(authorViewMapper.toAuthorView(author));
    }

    // ==================== QUERIES (Read Operations) ====================

    @Operation(summary = "Get author by author number (CQRS)")
    @GetMapping(value = "/{authorNumber}")
    public ResponseEntity<AuthorView> findByAuthorNumber(
            @PathVariable("authorNumber") @Parameter(description = "The number of the Author to find") final Long authorNumber) {

        GetAuthorByNumberQuery query = new GetAuthorByNumberQuery(authorNumber);
        Author author = queryBus.dispatch(query)
                .orElseThrow(() -> new NotFoundException(Author.class, authorNumber));

        return ResponseEntity.ok()
                .eTag(Long.toString(author.getVersion()))
                .body(authorViewMapper.toAuthorView(author));
    }

    @Operation(summary = "Search authors by name (CQRS)")
    @GetMapping
    public ListResponse<AuthorView> findByName(@RequestParam("name") final String name) {
        FindAuthorsByNameQuery query = new FindAuthorsByNameQuery(name);
        List<Author> authors = queryBus.dispatch(query);
        return new ListResponse<>(authorViewMapper.toAuthorView(authors));
    }

    @Operation(summary = "Get Top 5 authors by number of lendings (CQRS)")
    @GetMapping("/top5")
    public ListResponse<AuthorLendingView> getTop5() {
        GetTopAuthorsByLendingsQuery query = new GetTopAuthorsByLendingsQuery(5);
        var list = queryBus.dispatch(query);

        if (list.isEmpty()) {
            throw new NotFoundException("No authors to show");
        }

        return new ListResponse<>(list);
    }

    @Operation(summary = "Get co-authors of a specific author (CQRS)")
    @GetMapping("/{authorNumber}/coauthors")
    public ListResponse<AuthorView> getCoAuthors(
            @PathVariable("authorNumber") @Parameter(description = "The number of the Author") final Long authorNumber) {

        // First verify the author exists
        GetAuthorByNumberQuery authorQuery = new GetAuthorByNumberQuery(authorNumber);
        queryBus.dispatch(authorQuery)
                .orElseThrow(() -> new NotFoundException(Author.class, authorNumber));

        // Get co-authors
        GetCoAuthorsByAuthorNumberQuery query = new GetCoAuthorsByAuthorNumberQuery(authorNumber);
        List<Author> coAuthors = queryBus.dispatch(query);

        return new ListResponse<>(authorViewMapper.toAuthorView(coAuthors));
    }
}
