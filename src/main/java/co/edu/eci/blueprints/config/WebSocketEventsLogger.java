package co.edu.eci.blueprints.config;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Logs the STOMP session lifecycle (connect, subscribe, unsubscribe, disconnect) for observability.
 */
@Component
public class WebSocketEventsLogger {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventsLogger.class);

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("STOMP connected: session={}, user={}", accessor.getSessionId(), userOf(event));
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("STOMP subscribe: session={}, user={}, destination={}",
                accessor.getSessionId(), userOf(event), accessor.getDestination());
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("STOMP unsubscribe: session={}, user={}, subscriptionId={}",
                accessor.getSessionId(), userOf(event), accessor.getSubscriptionId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        log.info("STOMP disconnected: session={}, user={}, closeStatus={}",
                event.getSessionId(), userOf(event), event.getCloseStatus());
    }

    private static String userOf(AbstractSubProtocolEvent event) {
        Principal user = event.getUser();
        return user != null ? user.getName() : "anonymous";
    }
}
