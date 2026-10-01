package co.edu.eci.blueprints.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.eci.blueprints.model.Blueprint;
import co.edu.eci.blueprints.model.Point;

class InMemoryBlueprintPersistenceTest {

    private InMemoryBlueprintPersistence persistence;

    @BeforeEach
    void setUp() throws BlueprintPersistenceException {
        persistence = new InMemoryBlueprintPersistence();
        persistence.saveBlueprint(new Blueprint("tester", "canvas", List.of(new Point(1, 1))));
    }

    @Test
    void addPointAppendsPointAtTheEnd() throws Exception {
        persistence.addPoint("tester", "canvas", 10, 20);

        assertThat(persistence.getBlueprint("tester", "canvas").getPoints())
                .containsExactly(new Point(1, 1), new Point(10, 20));
    }

    @Test
    void addPointDoesNotMutatePreviouslyReadSnapshot() throws Exception {
        Blueprint before = persistence.getBlueprint("tester", "canvas");

        persistence.addPoint("tester", "canvas", 2, 2);

        assertThat(before.getPoints()).containsExactly(new Point(1, 1));
        assertThat(persistence.getBlueprint("tester", "canvas").getPoints()).hasSize(2);
    }

    @Test
    void addPointOnMissingBlueprintThrowsNotFound() {
        assertThatThrownBy(() -> persistence.addPoint("tester", "missing", 1, 1))
                .isInstanceOf(BlueprintNotFoundException.class);
    }

    @Test
    void concurrentAddPointsAreNeverLost() throws Exception {
        int threads = 8;
        int pointsPerThread = 250;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int threadId = t;
                tasks.add(() -> {
                    start.await();
                    for (int i = 0; i < pointsPerThread; i++) {
                        persistence.addPoint("tester", "canvas", threadId, i);
                        // Concurrent reads must always see a consistent list
                        persistence.getBlueprint("tester", "canvas").getPoints().forEach(p -> { });
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            start.countDown();
            for (Future<Void> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(persistence.getBlueprint("tester", "canvas").getPoints())
                .hasSize(1 + threads * pointsPerThread);
    }

    @Test
    void saveExistingBlueprintThrowsConflict() {
        assertThatThrownBy(() -> persistence.saveBlueprint(new Blueprint("tester", "canvas", List.of())))
                .isInstanceOf(BlueprintPersistenceException.class);
    }
}
