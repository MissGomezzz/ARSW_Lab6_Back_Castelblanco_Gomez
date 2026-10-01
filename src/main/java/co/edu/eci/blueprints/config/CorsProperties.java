package co.edu.eci.blueprints.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Allowed browser origins shared by the REST CORS policy and the STOMP/WebSocket handshake.
 * Bound from {@code blueprints.cors.allowed-origins} (env: {@code BLUEPRINTS_CORS_ALLOWED_ORIGINS},
 * comma separated).
 */
@ConfigurationProperties(prefix = "blueprints.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public static final String DEFAULT_ORIGIN = "http://localhost:5173";

    public CorsProperties {
        allowedOrigins = (allowedOrigins == null || allowedOrigins.isEmpty())
                ? List.of(DEFAULT_ORIGIN)
                : List.copyOf(allowedOrigins);
    }
}
