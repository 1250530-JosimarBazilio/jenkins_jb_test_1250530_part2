package pt.psoft.g1.psoftg1.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pt.psoft.g1.psoftg1.shared.model.BookEvents;

/**
 * RabbitMQ Configuration for Books Service.
 * 
 * Architecture: SHARED DATABASE (single database per microservice)
 * 
 * In this architecture:
 * - All microservice instances share the same database
 * - Events are published for inter-service communication (e.g., to LMS-Lending)
 * - No need for instance-to-instance synchronization
 * 
 * For DATABASE-PER-INSTANCE architecture (where each instance has its own DB):
 * - Enable the BookEventsListener by setting profile "database-per-instance"
 * - Each instance receives events and syncs its local database
 * 
 * Events published to other microservices:
 * - BOOK_CREATED
 * - BOOK_UPDATED
 * - BOOK_DELETED
 */
@Profile("!test")
@Configuration
public class RabbitmqConfig {

    public static final String EXCHANGE_NAME = "LMS.books";
    public static final String FANOUT_EXCHANGE_CREATED = "LMS.books.created";
    public static final String FANOUT_EXCHANGE_UPDATED = "LMS.books.updated";
    public static final String FANOUT_EXCHANGE_DELETED = "LMS.books.deleted";

    // Author events exchanges
    public static final String AUTHOR_FANOUT_EXCHANGE_CREATED = "LMS.authors.created";
    public static final String AUTHOR_FANOUT_EXCHANGE_UPDATED = "LMS.authors.updated";
    public static final String AUTHOR_FANOUT_EXCHANGE_DELETED = "LMS.authors.deleted";

    @Value("${spring.application.instance-id:default}")
    private String instanceId;

    /**
     * JSON Message Converter for RabbitMQ.
     * Enables automatic serialization/deserialization of objects.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    /**
     * Direct Exchange for backward compatibility with other services.
     */
    @Bean
    public DirectExchange booksExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    /**
     * Fanout Exchanges - broadcast to ALL subscribers.
     * Each event type has its own fanout exchange.
     */
    @Bean
    public FanoutExchange fanoutExchangeCreated() {
        return new FanoutExchange(FANOUT_EXCHANGE_CREATED);
    }

    @Bean
    public FanoutExchange fanoutExchangeUpdated() {
        return new FanoutExchange(FANOUT_EXCHANGE_UPDATED);
    }

    @Bean
    public FanoutExchange fanoutExchangeDeleted() {
        return new FanoutExchange(FANOUT_EXCHANGE_DELETED);
    }

    // Author Fanout Exchanges
    @Bean
    public FanoutExchange authorFanoutExchangeCreated() {
        return new FanoutExchange(AUTHOR_FANOUT_EXCHANGE_CREATED);
    }

    @Bean
    public FanoutExchange authorFanoutExchangeUpdated() {
        return new FanoutExchange(AUTHOR_FANOUT_EXCHANGE_UPDATED);
    }

    @Bean
    public FanoutExchange authorFanoutExchangeDeleted() {
        return new FanoutExchange(AUTHOR_FANOUT_EXCHANGE_DELETED);
    }

    @Profile("!test")
    @Configuration
    static class ReceiverConfig {

        /**
         * Anonymous queues - each instance gets its own unique queue.
         * This ensures all instances receive all events (fan-out pattern).
         */
        @Bean(name = "queueBookCreated")
        public Queue queueBookCreated() {
            return new AnonymousQueue();
        }

        @Bean(name = "queueBookUpdated")
        public Queue queueBookUpdated() {
            return new AnonymousQueue();
        }

        @Bean(name = "queueBookDeleted")
        public Queue queueBookDeleted() {
            return new AnonymousQueue();
        }

        /**
         * Bindings for Fanout Exchanges.
         * Each queue is bound to its corresponding fanout exchange.
         */
        @Bean
        public Binding bindingBookCreatedFanout(FanoutExchange fanoutExchangeCreated,
                @Qualifier("queueBookCreated") Queue queueBookCreated) {
            return BindingBuilder.bind(queueBookCreated)
                    .to(fanoutExchangeCreated);
        }

        @Bean
        public Binding bindingBookUpdatedFanout(FanoutExchange fanoutExchangeUpdated,
                @Qualifier("queueBookUpdated") Queue queueBookUpdated) {
            return BindingBuilder.bind(queueBookUpdated)
                    .to(fanoutExchangeUpdated);
        }

        @Bean
        public Binding bindingBookDeletedFanout(FanoutExchange fanoutExchangeDeleted,
                @Qualifier("queueBookDeleted") Queue queueBookDeleted) {
            return BindingBuilder.bind(queueBookDeleted)
                    .to(fanoutExchangeDeleted);
        }

        /**
         * Bindings for Direct Exchange (backward compatibility).
         */
        @Bean
        public Binding bindingBookCreated(DirectExchange booksExchange,
                @Qualifier("queueBookCreated") Queue queueBookCreated) {
            return BindingBuilder.bind(queueBookCreated)
                    .to(booksExchange)
                    .with(BookEvents.BOOK_CREATED);
        }

        @Bean
        public Binding bindingBookUpdated(DirectExchange booksExchange,
                @Qualifier("queueBookUpdated") Queue queueBookUpdated) {
            return BindingBuilder.bind(queueBookUpdated)
                    .to(booksExchange)
                    .with(BookEvents.BOOK_UPDATED);
        }

        @Bean
        public Binding bindingBookDeleted(DirectExchange booksExchange,
                @Qualifier("queueBookDeleted") Queue queueBookDeleted) {
            return BindingBuilder.bind(queueBookDeleted)
                    .to(booksExchange)
                    .with(BookEvents.BOOK_DELETED);
        }

        // Author Queues
        @Bean(name = "queueAuthorCreated")
        public Queue queueAuthorCreated() {
            return new AnonymousQueue();
        }

        @Bean(name = "queueAuthorUpdated")
        public Queue queueAuthorUpdated() {
            return new AnonymousQueue();
        }

        @Bean(name = "queueAuthorDeleted")
        public Queue queueAuthorDeleted() {
            return new AnonymousQueue();
        }

        // Author Bindings for Fanout Exchanges
        @Bean
        public Binding bindingAuthorCreatedFanout(FanoutExchange authorFanoutExchangeCreated,
                @Qualifier("queueAuthorCreated") Queue queueAuthorCreated) {
            return BindingBuilder.bind(queueAuthorCreated)
                    .to(authorFanoutExchangeCreated);
        }

        @Bean
        public Binding bindingAuthorUpdatedFanout(FanoutExchange authorFanoutExchangeUpdated,
                @Qualifier("queueAuthorUpdated") Queue queueAuthorUpdated) {
            return BindingBuilder.bind(queueAuthorUpdated)
                    .to(authorFanoutExchangeUpdated);
        }

        @Bean
        public Binding bindingAuthorDeletedFanout(FanoutExchange authorFanoutExchangeDeleted,
                @Qualifier("queueAuthorDeleted") Queue queueAuthorDeleted) {
            return BindingBuilder.bind(queueAuthorDeleted)
                    .to(authorFanoutExchangeDeleted);
        }
    }
}
