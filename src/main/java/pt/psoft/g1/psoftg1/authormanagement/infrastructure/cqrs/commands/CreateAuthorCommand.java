package pt.psoft.g1.psoftg1.authormanagement.infrastructure.cqrs.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Command;

/**
 * Command to create a new author in the system.
 * 
 * This command encapsulates all necessary information to create an author,
 * following the CQRS pattern where commands represent write operations.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateAuthorCommand implements Command<Author> {

    private String name;
    private String bio;
    private MultipartFile photo;
    private String photoURI;
}
