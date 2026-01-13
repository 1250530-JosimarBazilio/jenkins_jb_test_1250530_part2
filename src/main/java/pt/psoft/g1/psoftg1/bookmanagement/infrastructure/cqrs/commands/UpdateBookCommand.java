package pt.psoft.g1.psoftg1.bookmanagement.infrastructure.cqrs.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.shared.infrastructure.cqrs.Command;

import java.util.List;

/**
 * Command to update an existing book in the system.
 * 
 * This command encapsulates all information needed to update a book,
 * including optimistic locking version for concurrency control.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateBookCommand implements Command<Book> {

    private String isbn;
    private String title;
    private String genre;
    private String description;
    private List<Long> authorNumbers;
    private MultipartFile photo;
    private String photoURI;
    private String currentVersion;
}
