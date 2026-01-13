package pt.psoft.g1.psoftg1.authormanagement.infrastructure.repositories.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import pt.psoft.g1.psoftg1.authormanagement.api.AuthorLendingView;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataAuthorRepository extends AuthorRepository, CrudRepository<Author, Long> {
        @Override
        Optional<Author> findByAuthorNumber(Long authorNumber);

        // NOTE: This query is disabled because the Lending entity is in a separate
        // microservice.
        // In a microservices architecture, cross-service queries should be handled via
        // API calls or events.
        // @Query("SELECT new
        // pt.psoft.g1.psoftg1.authormanagement.api.AuthorLendingView(a.name.name,
        // COUNT(l.pk)) " +
        // "FROM Book b " +
        // "JOIN b.authors a " +
        // "JOIN Lending l ON l.book.pk = b.pk " +
        // "GROUP BY a.name " +
        // "ORDER BY COUNT(l) DESC")
        // Page<AuthorLendingView> findTopAuthorByLendings(Pageable pageable);

        @Override
        default Page<AuthorLendingView> findTopAuthorByLendings(Pageable pageable) {
                // Lending data is in a separate microservice - this would require cross-service
                // communication
                return Page.empty();
        }

        @Query("SELECT DISTINCT coAuthor FROM Book b " +
                        "JOIN b.authors coAuthor " +
                        "WHERE b IN (SELECT b FROM Book b JOIN b.authors a WHERE a.authorNumber = :authorNumber) " +
                        "AND coAuthor.authorNumber <> :authorNumber")
        List<Author> findCoAuthorsByAuthorNumber(Long authorNumber);
}
