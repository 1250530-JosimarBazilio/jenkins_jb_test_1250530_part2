# Java 17 (Temurin) - imagem oficial e estável
FROM eclipse-temurin:17-jre-jammy

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY target/lms-books-0.0.1-SNAPSHOT.jar /app/app.jar

# Expose port
EXPOSE 8090

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
