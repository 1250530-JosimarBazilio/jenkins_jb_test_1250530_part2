package pt.psoft.g1.psoftg1.bookmanagement.api;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for Book views.
 */
@Mapper(componentModel = "spring")
public abstract class BookViewMapper {

    @Mapping(source = "isbn", target = "isbn")
    @Mapping(source = "title.title", target = "title")
    @Mapping(source = "genre.genre", target = "genre")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "authors", target = "authors", qualifiedByName = "authorsToAuthorNames")
    @Mapping(target = "_links", ignore = true)
    public abstract BookView toBookView(Book book);

    public abstract List<BookView> toBookView(List<Book> books);

    public BookViewAMQP toBookViewAMQP(Book book) {
        if (book == null) {
            return null;
        }
        return new BookViewAMQP(
                book.getIsbn(),
                book.getTitle().toString(),
                book.getDescription(),
                book.getGenre().getGenre(),
                book.getVersion());
    }

    @Named("authorsToAuthorNames")
    protected List<String> authorsToAuthorNames(List<Author> authors) {
        if (authors == null) {
            return null;
        }
        return authors.stream()
                .map(Author::getName)
                .collect(Collectors.toList());
    }
}
