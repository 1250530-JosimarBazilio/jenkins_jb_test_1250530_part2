package pt.psoft.g1.psoftg1.bootstrapping;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bootstrapper for Books Microservice.
 * Initializes sample data for Authors, Genres, and Books.
 */
@Component
@RequiredArgsConstructor
@Profile("bootstrap")
@PropertySource({ "classpath:config/library.properties" })
@Order(1)
public class Bootstrapper implements CommandLineRunner {

    private final GenreRepository genreRepository;
    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;

    @Override
    @Transactional
    public void run(final String... args) {
        createAuthors();
        createGenres();
        createBooks();
    }

    private void createAuthors() {
        if (authorRepository.searchByNameName("Manuel Antonio Pina").isEmpty()) {
            final Author author = new Author("Manuel Antonio Pina",
                    "Manuel António Pina foi um jornalista e escritor português, premiado em 2011 com o Prémio Camões",
                    null);
            authorRepository.save(author);
        }
        if (authorRepository.searchByNameName("Antoine de Saint Exupéry").isEmpty()) {
            final Author author = new Author("Antoine de Saint Exupéry",
                    "Antoine de Saint-Exupéry nasceu a 29 de junho de 1900 em Lyon. Foi um aviador e escritor francês.",
                    null);
            authorRepository.save(author);
        }
        if (authorRepository.searchByNameName("J R R Tolkien").isEmpty()) {
            final Author author = new Author("J R R Tolkien",
                    "J.R.R. Tolkien nasceu a 3 de Janeiro de 1892, em Bloemfontein. Autor de O Senhor dos Anéis.",
                    null);
            authorRepository.save(author);
        }
        if (authorRepository.searchByNameName("George R R Martin").isEmpty()) {
            final Author author = new Author("George R R Martin",
                    "George Raymond Richard Martin é um escritor e roteirista norte-americano de literatura fantástica.",
                    null);
            authorRepository.save(author);
        }
        if (authorRepository.searchByNameName("Alexandre Pereira").isEmpty()) {
            final Author author = new Author("Alexandre Pereira",
                    "Alexandre Pereira é licenciado e mestre em Engenharia Electrotécnica e de Computadores.",
                    null);
            authorRepository.save(author);
        }
    }

    private void createGenres() {
        if (genreRepository.findByString("Fantasia").isEmpty()) {
            genreRepository.save(new Genre("Fantasia"));
        }
        if (genreRepository.findByString("Ficção Científica").isEmpty()) {
            genreRepository.save(new Genre("Ficção Científica"));
        }
        if (genreRepository.findByString("Romance").isEmpty()) {
            genreRepository.save(new Genre("Romance"));
        }
        if (genreRepository.findByString("Infantil").isEmpty()) {
            genreRepository.save(new Genre("Infantil"));
        }
        if (genreRepository.findByString("Terror").isEmpty()) {
            genreRepository.save(new Genre("Terror"));
        }
        if (genreRepository.findByString("Mistério").isEmpty()) {
            genreRepository.save(new Genre("Mistério"));
        }
        if (genreRepository.findByString("Informação").isEmpty()) {
            genreRepository.save(new Genre("Informação"));
        }
    }

    private void createBooks() {
        // O Principezinho
        if (bookRepository.findByIsbn("9789722328296").isEmpty()) {
            Optional<Genre> genre = genreRepository.findByString("Infantil");
            List<Author> authorList = authorRepository.searchByNameNameStartsWith("Antoine");
            if (genre.isPresent() && !authorList.isEmpty()) {
                Book book = new Book("9789722328296", "O Principezinho",
                        "Um clássico da literatura mundial sobre um pequeno príncipe que viaja entre planetas.",
                        genre.get(), authorList, null);
                bookRepository.save(book);
            }
        }

        // O Senhor dos Anéis
        if (bookRepository.findByIsbn("9780544003415").isEmpty()) {
            Optional<Genre> genre = genreRepository.findByString("Fantasia");
            List<Author> authorList = authorRepository.searchByNameNameStartsWith("J R R");
            if (genre.isPresent() && !authorList.isEmpty()) {
                Book book = new Book("9780544003415", "O Senhor dos Anéis",
                        "Uma épica aventura de fantasia na Terra Média.",
                        genre.get(), authorList, null);
                bookRepository.save(book);
            }
        }

        // O Hobbit
        if (bookRepository.findByIsbn("9789897776090").isEmpty()) {
            Optional<Genre> genre = genreRepository.findByString("Fantasia");
            List<Author> authorList = authorRepository.searchByNameNameStartsWith("J R R");
            if (genre.isPresent() && !authorList.isEmpty()) {
                Book book = new Book("9789897776090", "O Hobbit",
                        "A aventura de Bilbo Baggins que encontra o Um Anel.",
                        genre.get(), authorList, null);
                bookRepository.save(book);
            }
        }

        // A Guerra dos Tronos
        if (bookRepository.findByIsbn("9780553103540").isEmpty()) {
            Optional<Genre> genre = genreRepository.findByString("Fantasia");
            List<Author> authorList = authorRepository.searchByNameNameStartsWith("George");
            if (genre.isPresent() && !authorList.isEmpty()) {
                Book book = new Book("9780553103540", "A Guerra dos Tronos",
                        "O primeiro livro das Crônicas de Gelo e Fogo.",
                        genre.get(), authorList, null);
                bookRepository.save(book);
            }
        }

        // C e Algoritmos
        if (bookRepository.findByIsbn("9789895612864").isEmpty()) {
            Optional<Genre> genre = genreRepository.findByString("Informação");
            List<Author> authorList = authorRepository.searchByNameNameStartsWith("Alexandre");
            if (genre.isPresent() && !authorList.isEmpty()) {
                Book book = new Book("9789895612864", "C e Algoritmos",
                        "O C é uma linguagem de programação incontornável no estudo das linguagens de programação.",
                        genre.get(), authorList, null);
                bookRepository.save(book);
            }
        }
    }
}
