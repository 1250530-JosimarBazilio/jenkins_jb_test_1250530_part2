package pt.psoft.g1.psoftg1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import pt.psoft.g1.psoftg1.shared.services.FileStorageProperties;

/**
 * LMS Books Microservice Application.
 * 
 * Features:
 * - Saga Pattern for distributed transactions with compensation
 * - Outbox Pattern for reliable event publishing
 * - Event-Driven Architecture with RabbitMQ
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(FileStorageProperties.class)
public class PsoftG1Application {

	public static void main(String[] args) {
		SpringApplication.run(PsoftG1Application.class, args);
	}

}
