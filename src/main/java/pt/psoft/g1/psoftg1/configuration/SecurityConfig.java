package pt.psoft.g1.psoftg1.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Security Configuration for Books Microservice.
 * Simplified security for microservice architecture.
 */
@EnableWebSecurity
@Configuration
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@EnableConfigurationProperties
public class SecurityConfig {

    @Value("${springdoc.api-docs.path}")
    private String restApiDocPath;

    @Value("${springdoc.swagger-ui.path}")
    private String swaggerPath;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Enable CORS and disable CSRF
        http = http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable());

        // Set session management to stateless
        http = http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Set permissions on endpoints - allow all for microservice
        http.authorizeHttpRequests(auth -> auth
                // Swagger endpoints must be publicly accessible
                .requestMatchers("/").permitAll()
                .requestMatchers(restApiDocPath + "/**").permitAll()
                .requestMatchers(swaggerPath + "/**").permitAll()
                // Health check endpoint
                .requestMatchers("/api/books/health").permitAll()
                // All book endpoints are public for now (add JWT validation later)
                .requestMatchers("/api/books/**").permitAll()
                .requestMatchers("/api/genres/**").permitAll()
                .requestMatchers("/api/authors/**").permitAll()
                .anyRequest().permitAll());

        return http.build();
    }

    // Used by spring security if CORS is enabled.
    @Bean
    public CorsFilter corsFilter() {
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        final CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
