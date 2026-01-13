package pt.psoft.g1.psoftg1.shared.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.psoft.g1.psoftg1.shared.infrastructure.outbox.OutboxEvent;
import pt.psoft.g1.psoftg1.shared.infrastructure.outbox.OutboxEventRepository;
import pt.psoft.g1.psoftg1.shared.infrastructure.outbox.OutboxStatus;

import java.util.List;

/**
 * Service for managing outbox events.
 * 
 * This service provides a high-level API for saving events to the outbox.
 * It should be used instead of direct RabbitMQ publishing to ensure reliable
 * delivery.
 * 
 * Usage example:
 * 
 * <pre>
 * // Instead of:
 * rabbitTemplate.convertAndSend("exchange", "routing.key", eventDto);
 * 
 * // Use:
 * outboxService.saveEvent("Book", isbn, "BookCreatedEvent", eventDto, "exchange", "routing.key");
 * </pre>
 * 
 * @see OutboxEvent
 * @see pt.psoft.g1.psoftg1.shared.infrastructure.outbox.OutboxPublisher
 */
@Service
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventService(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Save an event to the outbox for reliable publishing.
     * 
     * This method should be called within the same transaction as the business
     * operation.
     * The event will be published asynchronously by the OutboxPublisher.
     *
     * @param aggregateType Type of aggregate (e.g., "Book", "Author", "Genre")
     * @param aggregateId   ID of the aggregate instance
     * @param eventType     Type of event (e.g., "BookCreatedEvent")
     * @param payload       Event data object (will be serialized to JSON)
     * @param exchangeName  RabbitMQ exchange name
     * @param routingKey    RabbitMQ routing key
     * @return The saved OutboxEvent
     * @throws RuntimeException if JSON serialization fails
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent saveEvent(String aggregateType, String aggregateId, String eventType,
            Object payload, String exchangeName, String routingKey) {
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload for {}: {}", eventType, e.getMessage());
            throw new RuntimeException("Failed to serialize event payload", e);
        }

        return saveEventWithJsonPayload(aggregateType, aggregateId, eventType,
                jsonPayload, exchangeName, routingKey);
    }

    /**
     * Save an event with a pre-serialized JSON payload.
     *
     * @param aggregateType Type of aggregate
     * @param aggregateId   ID of the aggregate
     * @param eventType     Type of event
     * @param jsonPayload   Already serialized JSON payload
     * @param exchangeName  RabbitMQ exchange name
     * @param routingKey    RabbitMQ routing key
     * @return The saved OutboxEvent
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent saveEventWithJsonPayload(String aggregateType, String aggregateId,
            String eventType, String jsonPayload,
            String exchangeName, String routingKey) {
        OutboxEvent event = new OutboxEvent(
                aggregateType,
                aggregateId,
                eventType,
                jsonPayload,
                exchangeName,
                routingKey);

        OutboxEvent saved = outboxRepository.save(event);
        log.debug("Saved outbox event: {} for {} ({})", eventType, aggregateType, aggregateId);

        return saved;
    }

    /**
     * Get pending event count (for monitoring).
     *
     * @return Number of pending events
     */
    public long getPendingCount() {
        return outboxRepository.countByStatus(OutboxStatus.PENDING);
    }

    /**
     * Get failed event count (for alerting).
     *
     * @return Number of failed events
     */
    public long getFailedCount() {
        return outboxRepository.countByStatus(OutboxStatus.FAILED);
    }

    /**
     * Get failed events for manual inspection.
     *
     * @return List of failed events
     */
    public List<OutboxEvent> getFailedEvents() {
        return outboxRepository.findByStatusOrderByCreatedAtDesc(OutboxStatus.FAILED);
    }

    /**
     * Get events for a specific aggregate (for debugging).
     *
     * @param aggregateType Type of aggregate
     * @param aggregateId   ID of the aggregate
     * @return List of events for that aggregate
     */
    public List<OutboxEvent> getEventsForAggregate(String aggregateType, String aggregateId) {
        return outboxRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
                aggregateType, aggregateId);
    }
}
