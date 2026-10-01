package co.edu.eci.blueprints.persistence;

import co.edu.eci.blueprints.model.Blueprint;
import co.edu.eci.blueprints.model.Point;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@Profile("!postgres")
public class InMemoryBlueprintPersistence implements BlueprintPersistence {

    private final Map<String, Blueprint> blueprints = new ConcurrentHashMap<>();

    public InMemoryBlueprintPersistence() {

        Blueprint bp1 = new Blueprint("john", "house",
                List.of(
                        new Point(80, 300),
                        new Point(80, 160),
                        new Point(160, 90),
                        new Point(240, 160),
                        new Point(240, 300),
                        new Point(80, 300)
                ));

        Blueprint bp2 = new Blueprint("john", "garage",
                List.of(
                        new Point(300, 300),
                        new Point(300, 200),
                        new Point(460, 200),
                        new Point(460, 300),
                        new Point(300, 300)
                ));

        Blueprint bp3 = new Blueprint("jane", "garden",
                List.of(
                        new Point(40, 320),
                        new Point(100, 260),
                        new Point(160, 300),
                        new Point(220, 240),
                        new Point(280, 280),
                        new Point(340, 220),
                        new Point(400, 260),
                        new Point(460, 200)
                ));

        Blueprint bp4 = new Blueprint("jane", "pool",
                List.of(
                        new Point(120, 120),
                        new Point(400, 120),
                        new Point(400, 240),
                        new Point(120, 240),
                        new Point(120, 120)
                ));

        Blueprint bp5 = new Blueprint("samuel", "star",
                List.of(
                        new Point(260, 50),
                        new Point(292, 136),
                        new Point(384, 140),
                        new Point(312, 197),
                        new Point(336, 285),
                        new Point(260, 235),
                        new Point(184, 285),
                        new Point(208, 197),
                        new Point(136, 140),
                        new Point(228, 136),
                        new Point(260, 50)
                ));

        Blueprint bp6 = new Blueprint("angela", "bridge",
                List.of(
                        new Point(40, 260),
                        new Point(120, 200),
                        new Point(200, 180),
                        new Point(260, 175),
                        new Point(320, 180),
                        new Point(400, 200),
                        new Point(480, 260)
                ));

        blueprints.put(keyOf(bp1), bp1);
        blueprints.put(keyOf(bp2), bp2);
        blueprints.put(keyOf(bp3), bp3);
        blueprints.put(keyOf(bp4), bp4);
        blueprints.put(keyOf(bp5), bp5);
        blueprints.put(keyOf(bp6), bp6);
    }

    private String keyOf(Blueprint bp) {
        return bp.getAuthor() + ":" + bp.getName();
    }

    private String keyOf(String author, String name) {
        return author + ":" + name;
    }

    @Override
    public void saveBlueprint(Blueprint bp) throws BlueprintPersistenceException {
        String k = keyOf(bp);

        // putIfAbsent makes the existence check and the insert a single atomic step.
        if (blueprints.putIfAbsent(k, bp) != null) {
            throw new BlueprintPersistenceException(
                    "Blueprint already exists: " + k
            );
        }
    }

    @Override
    public Blueprint getBlueprint(String author, String name)
            throws BlueprintNotFoundException {

        Blueprint bp = blueprints.get(keyOf(author, name));

        if (bp == null) {
            throw new BlueprintNotFoundException(
                    "Blueprint not found: %s/%s".formatted(author, name)
            );
        }

        return bp;
    }

    @Override
    public Set<Blueprint> getBlueprintsByAuthor(String author)
            throws BlueprintNotFoundException {

        Set<Blueprint> set = blueprints.values().stream()
                .filter(bp -> bp.getAuthor().equals(author))
                .collect(Collectors.toSet());

        if (set.isEmpty()) {
            throw new BlueprintNotFoundException(
                    "No blueprints for author: " + author
            );
        }

        return set;
    }

    @Override
    public Set<Blueprint> getAllBlueprints() {
        return new HashSet<>(blueprints.values());
    }

    @Override
    public void addPoint(String author, String name, int x, int y)
            throws BlueprintNotFoundException {

        Point point = new Point(x, y);
        // Atomic copy-on-write: stored Blueprint instances are never mutated, so concurrent
        // readers always see a consistent snapshot and concurrent writers never lose points.
        Blueprint updated = blueprints.computeIfPresent(keyOf(author, name), (k, current) -> {
            List<Point> points = new ArrayList<>(current.getPoints().size() + 1);
            points.addAll(current.getPoints());
            points.add(point);
            return new Blueprint(current.getAuthor(), current.getName(), points);
        });

        if (updated == null) {
            throw new BlueprintNotFoundException(
                    "Blueprint not found: %s/%s".formatted(author, name)
            );
        }
    }

    @Override
    public void updatePoints(String author, String name, List<Point> points) throws BlueprintNotFoundException {
        if (blueprints.replace(keyOf(author, name), new Blueprint(author, name, points)) == null) {
            throw new BlueprintNotFoundException("Blueprint not found: %s/%s".formatted(author, name));
        }
    }

    @Override
    public void deleteBlueprint(String author, String name) throws BlueprintNotFoundException {
        if (blueprints.remove(keyOf(author, name)) == null) {
            throw new BlueprintNotFoundException("Blueprint not found: %s/%s".formatted(author, name));
        }
    }
}
