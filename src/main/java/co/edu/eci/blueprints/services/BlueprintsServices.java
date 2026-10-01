package co.edu.eci.blueprints.services;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import co.edu.eci.blueprints.dto.AuthorBlueprints;
import co.edu.eci.blueprints.filters.BlueprintsFilter;
import co.edu.eci.blueprints.model.Blueprint;
import co.edu.eci.blueprints.model.Point;
import co.edu.eci.blueprints.persistence.BlueprintNotFoundException;
import co.edu.eci.blueprints.persistence.BlueprintPersistence;
import co.edu.eci.blueprints.persistence.BlueprintPersistenceException;

@Service
public class BlueprintsServices {

    private final BlueprintPersistence persistence;
    private final BlueprintsFilter filter;

    public BlueprintsServices(BlueprintPersistence persistence, BlueprintsFilter filter) {
        this.persistence = persistence;
        this.filter = filter;
    }

    public void addNewBlueprint(Blueprint bp) throws BlueprintPersistenceException {
        persistence.saveBlueprint(bp);
    }

    public Set<Blueprint> getAllBlueprints() {
        return persistence.getAllBlueprints().stream()
                .map(filter::apply)
                .collect(Collectors.toSet());
    }

    public Set<Blueprint> getBlueprintsByAuthor(String author) throws BlueprintNotFoundException {
        return persistence.getBlueprintsByAuthor(author).stream()
                .map(filter::apply)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the (filtered) blueprints of an author together with the total number of points.
     *
     * @throws BlueprintNotFoundException if the author has no blueprints.
     */
    public AuthorBlueprints getAuthorBlueprints(String author) throws BlueprintNotFoundException {
        Set<Blueprint> blueprints = getBlueprintsByAuthor(author);
        int totalPoints = blueprints.stream()
                .mapToInt(bp -> bp.getPoints().size())
                .sum();
        return new AuthorBlueprints(author, totalPoints, blueprints);
    }

    public Blueprint getBlueprint(String author, String name) throws BlueprintNotFoundException {
        return filter.apply(persistence.getBlueprint(author, name));
    }

    public void addPoint(String author, String name, int x, int y) throws BlueprintNotFoundException {
        persistence.addPoint(author, name, x, y);
    }

    public void updatePoints(String author, String name, List<Point> points) throws BlueprintNotFoundException {
        persistence.updatePoints(author, name, points);
    }

    public void deleteBlueprint(String author, String name) throws BlueprintNotFoundException {
        persistence.deleteBlueprint(author, name);
    }
}
