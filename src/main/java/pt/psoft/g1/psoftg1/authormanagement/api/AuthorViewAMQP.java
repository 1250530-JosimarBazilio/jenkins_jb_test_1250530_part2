package pt.psoft.g1.psoftg1.authormanagement.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for publishing author events via AMQP (RabbitMQ).
 * 
 * This class is used when publishing author events to message queues
 * for inter-service communication.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Author view for AMQP messaging")
public class AuthorViewAMQP implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long authorNumber;
    private String name;
    private String bio;
    private String photoURI;
    private Long version;
}
