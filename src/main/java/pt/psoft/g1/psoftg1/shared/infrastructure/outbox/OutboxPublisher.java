package pt.psoft.g1.psoftg1.shared.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled component that polls the outbox table and publishes pending events
 * to RabbitMQ.
 * 
 * This implements the "polling publisher" variant of the Outbox Pattern.
 * 
 * Key features:
 * - Polls every 100ms for low latency
 * - Processes events in FIFO order (per aggregate)
 * - Retries failed events up to MAX_RETRIES times
 * - Cleans up old published events daily
 * 
 * @see OutboxEvent
 * @see OutboxEventRepository
 */
@Profile("!test")
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${outbox.max-retries:3}")
    private int maxRetries;

    @Value("${outbox.batch-size:100}")
    private int batchSize;

    @Value("${outbox.cleanup-days:7}")
    private int cleanupDays;

    public OutboxPublisher(OutboxEventRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Poll and publish pending events every 100ms.
     * 
     * This provides a good balance between latency and resource usage.
     * Events are typically published within 100-200ms of being saved.
     */
    @Scheduled(fixedDelayString = "${outbox.poll-interval:100}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository
                .findByStatusWithLimit(OutboxStatus.PENDING, batchSize);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }
    }

    /**
     * Attempt to publish a single event to RabbitMQ.
     *
     * @param event The outbox event to publish
     */
    private void publishEvent(OutboxEvent event) {
        try {
            // Create message with proper JSON content type to avoid double-encoding
            // The payload is already JSON-serialized, so we send it as raw bytes
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setContentEncoding(StandardCharsets.UTF_8.name());

            Message message = new Message(
                    event.getPayload().getBytes(StandardCharsets.UTF_8),
                    props);

            rabbitTemplate.send(
                    event.getExchangeName(),
                    event.getRoutingKey(),
                    message);

            event.markAsPublished();
            outboxRepository.save(event);

            log.debug("Published outbox event: {} ({})", event.getEventType(), event.getAggregateId());

        } catch (Exception e) {
            log.warn("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());

            event.recordFailure(e.getMessage(), maxRetries);
            outboxRepository.save(event);

            if (event.getStatus() == OutboxStatus.FAILED) {
                log.error("Outbox event {} permanently failed after {} retries: {}",
                        event.getId(), maxRetries, event.getEventType());
            }
        }
    }

    /**
     * Clean up old published events daily to prevent table growth.
     * 
     * Runs at 3:00 AM every day.
     */
    @Scheduled(cron = "${outbox.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupOldEvents() {
        Instant cutoff = Instant.now().minus(cleanupDays, ChronoUnit.DAYS);

        int deleted = outboxRepository.deleteOldEvents(OutboxStatus.PUBLISHED, cutoff);

        if (deleted > 0) {
            log.info("Cleaned up {} old published outbox events (older than {} days)", deleted, cleanupDays);
        }
    }

    /**
     * Log outbox statistics for monitoring.
     * 
     * Runs every hour.
     */
    @Scheduled(cron = "${outbox.stats-cron:0 0 * * * ?}")
    public void logStatistics() {
        long pending = outboxRepository.countByStatus(OutboxStatus.PENDING);
        long failed = outboxRepository.countByStatus(OutboxStatus.FAILED);

        if (pending > 0 || failed > 0) {
            log.info("Outbox stats: {} pending, {} failed", pending, failed);
        }

        if (failed > 0) {
            log.warn("There are {} failed outbox events that require attention", failed);
        }
    }
}
