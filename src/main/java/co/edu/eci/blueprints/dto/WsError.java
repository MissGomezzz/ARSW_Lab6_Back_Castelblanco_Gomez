package co.edu.eci.blueprints.dto;

/**
 * Error payload sent to the offending user on {@code /user/queue/errors}.
 */
public record WsError(String message) { }
