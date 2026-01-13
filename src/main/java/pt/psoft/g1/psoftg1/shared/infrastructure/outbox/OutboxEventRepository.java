package pt.psoft.g1.psoftg1.shared.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for OutboxEvent entities.
 * 
 * Provides methods to query and manage outbox events for reliable publishing.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Find all pending events ordered by creation time (FIFO).
     * This ensures events are published in the order they were created.
     *
     * @param status The status to filter by (typically PENDING)
     * @return List of events ordered by createdAt ascending
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    /**
     * Find pending events with a limit to prevent memory issues.
     *
     * @param status The status to filter by
     * @param limit  Maximum number of events to return
     * @return List of events
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findByStatusWithLimit(@Param("status") OutboxStatus status, @Param("limit") int limit);

    /**
     * Count events by status (for monitoring/metrics).
     *
     * @param status The status to count
     * @return Number of events with that status
     */
    long countByStatus(OutboxStatus status);

    /**
     * Find events by aggregate type and ID.
     * Useful for debugging and tracing.
     *
     * @param aggregateType Type of aggregate
     * @param aggregateId   ID of the aggregate
     * @return List of events for that aggregate
     */
    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            String aggregateType, String aggregateId);

    /**
     * Delete old published events to prevent table growth.
     * Should be called by a scheduled cleanup job.
     *
     * @param status Status of events to delete (typically PUBLISHED)
     * @param before Delete events published before this timestamp
     * @return Number of deleted events
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = :status AND e.publishedAt < :before")
    int deleteOldEvents(@Param("status") OutboxStatus status, @Param("before") Instant before);

    /**
     * Find failed events for manual inspection/retry.
     *
     * @return List of failed events
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtDesc(OutboxStatus status);
}
