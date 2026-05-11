package chit.tefca.directory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Registers a {@link WebClient.Builder} for reactive HTTP outbound calls.
 * The directory-cache-service uses spring-webflux without the Servlet-conflicting
 * {@code spring-boot-starter-webflux}, so the builder is not auto-registered.
 */
@Configuration
@org.springframework.context.annotation.Profile("!prod")
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
