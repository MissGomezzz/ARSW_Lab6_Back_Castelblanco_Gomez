package co.edu.eci.blueprints.api;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import co.edu.eci.blueprints.dto.BlueprintUpdate;
import co.edu.eci.blueprints.dto.DrawEvent;
import co.edu.eci.blueprints.dto.WsError;
import co.edu.eci.blueprints.persistence.BlueprintNotFoundException;
import co.edu.eci.blueprints.services.BlueprintsServices;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

/**
 * Real-time drawing over STOMP.
 *
 * <p>Clients SEND a {@link DrawEvent} to {@code /app/draw}; the point is persisted and a
 * {@link BlueprintUpdate} is broadcast to {@code /topic/blueprints.{author}.{name}}. Errors are sent
 * only to the sender on {@code /user/queue/errors}.</p>
 */
@Controller
public class BlueprintDrawController {

    private static final Logger log = LoggerFactory.getLogger(BlueprintDrawController.class);

    static final String TOPIC_PREFIX = "/topic/blueprints.";
    static final String ERRORS_QUEUE = "/queue/errors";

    private final BlueprintsServices services;
    private final SimpMessagingTemplate messagingTemplate;
    private final Validator validator;

    public BlueprintDrawController(BlueprintsServices services,
                                   SimpMessagingTemplate messagingTemplate,
                                   Validator validator) {
        this.services = services;
        this.messagingTemplate = messagingTemplate;
        this.validator = validator;
    }

    public static String topicFor(String author, String name) {
        return TOPIC_PREFIX + author + "." + name;
    }

    @MessageMapping("/draw")
    public void draw(@Payload DrawEvent event, Principal principal) throws BlueprintNotFoundException {
        Set<ConstraintViolation<DrawEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        services.addPoint(event.author(), event.name(), event.point().x(), event.point().y());

        String destination = topicFor(event.author(), event.name());
        messagingTemplate.convertAndSend(destination,
                new BlueprintUpdate(event.author(), event.name(), List.of(event.point()), event.clientId()));

        log.info("Draw event: user={}, blueprint={}/{}, point=({}, {}), clientId={} -> {}",
                principalName(principal), event.author(), event.name(),
                event.point().x(), event.point().y(), event.clientId(), destination);
    }

    @MessageExceptionHandler(ConstraintViolationException.class)
    @SendToUser(destinations = ERRORS_QUEUE, broadcast = false)
    public WsError handleValidation(ConstraintViolationException e, Principal principal) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        log.warn("Draw event rejected (validation): user={}, {}", principalName(principal), message);
        return new WsError(message);
    }

    @MessageExceptionHandler(BlueprintNotFoundException.class)
    @SendToUser(destinations = ERRORS_QUEUE, broadcast = false)
    public WsError handleNotFound(BlueprintNotFoundException e, Principal principal) {
        log.warn("Draw event rejected (not found): user={}, {}", principalName(principal), e.getMessage());
        return new WsError(e.getMessage());
    }

    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(destinations = ERRORS_QUEUE, broadcast = false)
    public WsError handleMalformed(MessageConversionException e, Principal principal) {
        log.warn("Draw event rejected (malformed payload): user={}, {}", principalName(principal), e.getMessage());
        return new WsError("Malformed draw payload");
    }

    private static String principalName(Principal principal) {
        return principal != null ? principal.getName() : "anonymous";
    }
}
