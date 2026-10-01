package co.edu.eci.blueprints.security;

import java.nio.charset.StandardCharsets;

import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * Turns authentication/authorization failures on the inbound STOMP channel into an ERROR frame
 * whose {@code message} header starts with {@value #UNAUTHORIZED_PREFIX}, so clients can tell an
 * expired or invalid session apart from other errors (and stop reconnecting). Any other failure
 * keeps Spring's default ERROR frame.
 */
@Component
public class StompAuthErrorHandler extends StompSubProtocolErrorHandler {

    public static final String UNAUTHORIZED_PREFIX = "unauthorized";

    @Override
    @Nullable
    public Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage, Throwable ex) {
        Throwable authFailure = findAuthFailure(ex);
        if (authFailure == null) {
            return super.handleClientMessageProcessingError(clientMessage, ex);
        }
        String text = UNAUTHORIZED_PREFIX + ": " + authFailure.getMessage();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(text);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(text.getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());
    }

    @Nullable
    private static Throwable findAuthFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationException || t instanceof AccessDeniedException) {
                return t;
            }
        }
        return null;
    }
}
