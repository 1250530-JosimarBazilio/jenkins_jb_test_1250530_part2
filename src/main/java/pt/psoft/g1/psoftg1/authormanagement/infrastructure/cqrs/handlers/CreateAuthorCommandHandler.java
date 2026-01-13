package pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.commands.CreateAuthorCommand;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.saga.CreateAuthorSagaOrchestrator;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.services.CreateAuthorRequest;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.CommandHandler;

/**
 * Command handler for creating a new author.
 * 
 * This handler processes CreateAuthorCommand by delegating to the Saga
 * orchestrator,
 * which ensures atomic operations with compensating transactions.
 * 
 * Responsibilities:
 * - Transform command into the appropriate request format
 * - Delegate to saga orchestrator for atomic execution
 * - Return the created author entity
 */
@Component
@RequiredArgsConstructor
public class CreateAuthorCommandHandler implements CommandHandler<CreateAuthorCommand, Author> {

    private static final Logger log = LoggerFactory.getLogger(CreateAuthorCommandHandler.class);

    private final CreateAuthorSagaOrchestrator createAuthorSagaOrchestrator;

    @Override
    public Author handle(CreateAuthorCommand command) {
        log.info("Handling CreateAuthorCommand for author: {}", command.getName());

        // Transform command into CreateAuthorRequest
        CreateAuthorRequest request = new CreateAuthorRequest();
        request.setName(command.getName());
        request.setBio(command.getBio());

        // Handle photo
        MultipartFile photo = command.getPhoto();
        String photoURI = command.getPhotoURI();
        if (photo == null && photoURI != null || photo != null && photoURI == null) {
            request.setPhoto(null);
            request.setPhotoURI(null);
        } else {
            request.setPhoto(photo);
            request.setPhotoURI(photoURI);
        }

        // Delegate to Saga Orchestrator for atomic operation with compensation
        return createAuthorSagaOrchestrator.execute(request, photoURI);
    }
}
