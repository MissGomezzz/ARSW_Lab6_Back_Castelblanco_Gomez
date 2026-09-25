package co.edu.eci.blueprints.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.edu.eci.blueprints.model.Blueprint;
import co.edu.eci.blueprints.model.Point;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementacion de {@link BlueprintPersistence} sobre PostgreSQL usando JDBC.
 *
 * <p>Los puntos de cada plano se almacenan en una columna JSONB y se
 * serializan/deserializan con Jackson. Se activa con el perfil de Spring
 * {@code postgres}.</p>
 */
@Repository
@Profile("postgres")
public class PostgresBlueprintPersistence implements BlueprintPersistence {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresBlueprintPersistence(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void saveBlueprint(Blueprint bp) throws BlueprintPersistenceException {
        try {
            jdbc.update(
                    "INSERT INTO blueprints (author, name, points) VALUES (?, ?, ?::jsonb)",
                    bp.getAuthor(), bp.getName(), toJson(bp.getPoints()));
        } catch (DataIntegrityViolationException e) {
            throw new BlueprintPersistenceException(
                    "Blueprint already exists: " + bp.getAuthor() + ":" + bp.getName());
        }
    }

    @Override
    public Blueprint getBlueprint(String author, String name) throws BlueprintNotFoundException {
        List<Blueprint> found = jdbc.query(
                "SELECT author, name, points::text AS points FROM blueprints WHERE author = ? AND name = ?",
                this::mapRow, author, name);
        if (found.isEmpty()) {
            throw new BlueprintNotFoundException("Blueprint not found: %s/%s".formatted(author, name));
        }
        return found.get(0);
    }

    @Override
    public Set<Blueprint> getBlueprintsByAuthor(String author) throws BlueprintNotFoundException {
        Set<Blueprint> set = jdbc.query(
                        "SELECT author, name, points::text AS points FROM blueprints WHERE author = ?",
                        this::mapRow, author)
                .stream().collect(Collectors.toSet());
        if (set.isEmpty()) {
            throw new BlueprintNotFoundException("No blueprints for author: " + author);
        }
        return set;
    }

    @Override
    public Set<Blueprint> getAllBlueprints() {
        return jdbc.query("SELECT author, name, points::text AS points FROM blueprints", this::mapRow)
                .stream().collect(Collectors.toSet());
    }

    @Override
    public void addPoint(String author, String name, int x, int y) throws BlueprintNotFoundException {
        String pointJson = toJson(List.of(new Point(x, y)));
        int updated = jdbc.update(
                "UPDATE blueprints SET points = points || ?::jsonb WHERE author = ? AND name = ?",
                pointJson, author, name);
        if (updated == 0) {
            throw new BlueprintNotFoundException("Blueprint not found: %s/%s".formatted(author, name));
        }
    }

    @Override
    public void updatePoints(String author, String name, List<Point> points) throws BlueprintNotFoundException {
        int updated = jdbc.update(
                "UPDATE blueprints SET points = ?::jsonb WHERE author = ? AND name = ?",
                toJson(points), author, name);
        if (updated == 0) {
            throw new BlueprintNotFoundException("Blueprint not found: %s/%s".formatted(author, name));
        }
    }

    @Override
    public void deleteBlueprint(String author, String name) throws BlueprintNotFoundException {
        int deleted = jdbc.update("DELETE FROM blueprints WHERE author = ? AND name = ?", author, name);
        if (deleted == 0) {
            throw new BlueprintNotFoundException("Blueprint not found: %s/%s".formatted(author, name));
        }
    }

    private Blueprint mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Blueprint(rs.getString("author"), rs.getString("name"),
                parsePoints(rs.getString("points")));
    }

    private String toJson(List<Point> points) {
        try {
            return mapper.writeValueAsString(points);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize points to JSON", e);
        }
    }

    private List<Point> parsePoints(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<Point>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize points from JSON", e);
        }
    }
}
