package co.edu.eci.blueprints.dto;

import co.edu.eci.blueprints.model.Point;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound STOMP payload sent to {@code /app/draw}.
 *
 * @param clientId optional opaque id of the sender, echoed back so it can ignore its own update.
 */
public record DrawEvent(
        @NotBlank(message = "author must not be blank") String author,
        @NotBlank(message = "name must not be blank") String name,
        @NotNull(message = "point must not be null") @Valid Point point,
        String clientId
) { }
