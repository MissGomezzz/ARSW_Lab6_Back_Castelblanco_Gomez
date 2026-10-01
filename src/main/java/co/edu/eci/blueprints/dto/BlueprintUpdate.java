package co.edu.eci.blueprints.dto;

import java.util.List;

import co.edu.eci.blueprints.model.Point;

/**
 * Outbound STOMP payload broadcast on {@code /topic/blueprints.{author}.{name}}.
 * {@code points} contains only the newly appended points.
 */
public record BlueprintUpdate(String author, String name, List<Point> points, String clientId) { }
