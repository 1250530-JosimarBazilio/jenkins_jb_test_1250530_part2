package pt.psoft.g1.psoftg1.shared.infrastructure.outbox;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing an event in the transactional outbox.
 * 
 * The Outbox Pattern ensures reliable event publishing by:
 * 1. Storing events in the same transaction as business data
 * 2. Asynchronously publishing to the message broker
 * 3. Guaranteeing at-least-once delivery
 * 
 * @see OutboxPublisher
 * @see OutboxStatus
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_created_at", columnList = "createdAt")
})
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type of aggregate that generated this event (e.g., "Book", "Author",
     * "Genre").
     */
    @Column(nullable = false, length = 100)
    private String aggregateType;

    /**
     * ID of the aggregate instance (e.g., ISBN, author number).
     */
    @Column(nullable = false, length = 255)
    private String aggregateId;

    /**
     * Type of event (e.g., "BookCreatedEvent", "AuthorCreatedEvent").
     */
    @Column(nullable = false, length = 100)
    private String eventType;

    /**
     * JSON serialized event payload.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * RabbitMQ exchange name.
     */
    @Column(nullable = false, length = 100)
    private String exchangeName;

    /**
     * RabbitMQ routing key.
     */
    @Column(nullable = false, length = 100)
    private String routingKey;

    /**
     * Current status of the event.
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status = OutboxStatus.PENDING;

    /**
     * When the event was created.
     */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * When the event was successfully published (null if not yet published).
     */
    private Instant publishedAt;

    /**
     * Number of publish attempts.
     */
    @Column(nullable = false)
    private int retryCount = 0;

    /**
     * Last error message if publish failed.
     */
    @Column(length = 1000)
    private String lastError;

    // JPA requires a no-arg constructor
    protected OutboxEvent() {
    }

    /**
     * Create a new outbox event.
     *
     * @param aggregateType Type of aggregate (e.g., "Book")
     * @param aggregateId   ID of the aggregate
     * @param eventType     Type of event (e.g., "BookCreatedEvent")
     * @param payload       JSON serialized event data
     * @param exchangeName  RabbitMQ exchange
     * @param routingKey    RabbitMQ routing key
     */
    public OutboxEvent(String aggregateType, String aggregateId, String eventType,
            String payload, String exchangeName, String routingKey) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.createdAt = Instant.now();
        this.status = OutboxStatus.PENDING;
    }

    // Getters

    public Long getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    // State transition methods

    /**
     * Mark this event as successfully published.
     */
    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    /**
     * Record a failed publish attempt.
     *
     * @param error      The error message
     * @param maxRetries Maximum number of retries before marking as FAILED
     */
    public void recordFailure(String error, int maxRetries) {
        this.retryCount++;
        this.lastError = error;
        if (this.retryCount >= maxRetries) {
            this.status = OutboxStatus.FAILED;
        }
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "id=" + id +
                ", aggregateType='" + aggregateType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                '}';
    }
}
