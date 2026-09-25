package co.edu.eci.blueprints.dto;

import co.edu.eci.blueprints.model.Point;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO de entrada para reemplazar los puntos de un {@code Blueprint}
 * (PUT /api/v1/blueprints/{author}/{bpname}).
 */
public record UpdateBlueprintRequest(
        @NotNull(message = "points must not be null") @Valid List<Point> points
) { }
