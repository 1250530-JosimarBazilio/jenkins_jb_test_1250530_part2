package pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.handlers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.commands.UpdateAuthorCommand;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.authormanagement.services.UpdateAuthorRequest;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.CommandHandler;

/**
 * Command handler for updating an existing author.
 * 
 * This handler processes UpdateAuthorCommand by applying partial updates
 * to the author entity with optimistic locking.
 */
@Component
@RequiredArgsConstructor
public class UpdateAuthorCommandHandler implements CommandHandler<UpdateAuthorCommand, Author> {

    private static final Logger log = LoggerFactory.getLogger(UpdateAuthorCommandHandler.class);

    private final AuthorRepository authorRepository;

    @Override
    public Author handle(UpdateAuthorCommand command) {
        log.info("Handling UpdateAuthorCommand for author number: {}", command.getAuthorNumber());

        // Find existing author
        Author author = authorRepository.findByAuthorNumber(command.getAuthorNumber())
                .orElseThrow(() -> new NotFoundException("Cannot update an object that does not yet exist"));

        // Build update request
        UpdateAuthorRequest request = new UpdateAuthorRequest();
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

        // Apply patch with version check
        author.applyPatch(command.getDesiredVersion(), request);

        // Save and return
        Author updatedAuthor = authorRepository.save(author);
        log.info("Author updated successfully: {}", updatedAuthor.getAuthorNumber());

        return updatedAuthor;
    }
}
