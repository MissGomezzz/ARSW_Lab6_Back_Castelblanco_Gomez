package co.edu.eci.blueprints.dto;

import java.util.Set;

import co.edu.eci.blueprints.model.Blueprint;

/**
 * Blueprints of one author plus the total number of points across all of them
 * (GET /api/v1/blueprints?author={author}).
 */
public record AuthorBlueprints(String author, int totalPoints, Set<Blueprint> blueprints) { }
