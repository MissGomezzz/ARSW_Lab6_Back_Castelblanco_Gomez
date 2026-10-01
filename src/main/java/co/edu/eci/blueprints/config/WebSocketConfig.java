package co.edu.eci.blueprints.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import co.edu.eci.blueprints.security.StompAuthChannelInterceptor;
import co.edu.eci.blueprints.security.StompAuthErrorHandler;

/**
 * STOMP over native WebSocket for real-time collaborative drawing.
 *
 * <ul>
 *   <li>Endpoint: {@code /ws-blueprints} (no SockJS fallback).</li>
 *   <li>Client to server: {@code /app/**} (e.g. {@code /app/draw}).</li>
 *   <li>Broadcast: {@code /topic/blueprints.{author}.{name}}; per-user errors: {@code /user/queue/errors}.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws-blueprints";

    private final CorsProperties corsProperties;
    private final StompAuthChannelInterceptor authInterceptor;
    private final StompAuthErrorHandler errorHandler;

    public WebSocketConfig(CorsProperties corsProperties, StompAuthChannelInterceptor authInterceptor,
                           StompAuthErrorHandler errorHandler) {
        this.corsProperties = corsProperties;
        this.authInterceptor = authInterceptor;
        this.errorHandler = errorHandler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
        registry.setErrorHandler(errorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        // Deliver broadcasts to each session in the order they were published.
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // A single inbound worker processes frames in arrival order, so points are
        // persisted and broadcast in the same order the user clicked them.
        // The default pool (one thread per core) can reorder rapid clicks.
        registration.interceptors(authInterceptor)
                .taskExecutor().corePoolSize(1).maxPoolSize(1);
    }
}
