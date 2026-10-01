package co.edu.eci.blueprints.model;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * A canvas point. Coordinates are canvas pixels, so negative values are rejected.
 */
public record Point(
        @PositiveOrZero(message = "x must be >= 0") int x,
        @PositiveOrZero(message = "y must be >= 0") int y
) { }
