package co.edu.eci.blueprints.security;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP sessions with the same JWTs used by the REST API.
 *
 * <ul>
 *   <li>CONNECT must carry the native header {@code Authorization: Bearer <jwt>}; the token is
 *       decoded with the application {@link JwtDecoder} and the resulting authentication becomes
 *       the session user. A missing or invalid token rejects the CONNECT (ERROR frame + close).</li>
 *   <li>Any later frame (SUBSCRIBE, SEND, ...) requires an authenticated session.</li>
 *   <li>SEND to {@code /app/draw} additionally requires {@code SCOPE_blueprints.write}.</li>
 * </ul>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String BEARER_PREFIX = "Bearer ";
    static final String DRAW_DESTINATION = "/app/draw";
    static final String WRITE_AUTHORITY = "SCOPE_blueprints.write";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeats and non-STOMP messages
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
            Authentication authentication = authenticate(accessor);
            accessor.setUser(authentication);
            log.debug("STOMP CONNECT authenticated: session={}, user={}",
                    accessor.getSessionId(), authentication.getName());
            return message;
        }

        if (StompCommand.DISCONNECT.equals(command)) {
            return message;
        }

        if (!(accessor.getUser() instanceof Authentication auth) || !auth.isAuthenticated()) {
            throw new AccessDeniedException("STOMP session is not authenticated");
        }

        if (StompCommand.SEND.equals(command) && DRAW_DESTINATION.equals(accessor.getDestination())
                && auth.getAuthorities().stream().noneMatch(a -> WRITE_AUTHORITY.equals(a.getAuthority()))) {
            log.warn("STOMP SEND to {} denied: user={} lacks {}",
                    DRAW_DESTINATION, auth.getName(), WRITE_AUTHORITY);
            throw new AccessDeniedException("Missing scope blueprints.write");
        }

        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null) {
            header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER.toLowerCase(Locale.ROOT));
        }
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            log.warn("STOMP CONNECT rejected: missing bearer token (session={})", accessor.getSessionId());
            throw new BadCredentialsException("Missing 'Authorization: Bearer <token>' header on CONNECT");
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return authenticationConverter.convert(jwt);
        } catch (JwtException e) {
            log.warn("STOMP CONNECT rejected: invalid token (session={}): {}",
                    accessor.getSessionId(), e.getMessage());
            throw new BadCredentialsException("Invalid bearer token", e);
        }
    }
}
