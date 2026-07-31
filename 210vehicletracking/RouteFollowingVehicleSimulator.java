import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Drives vehicles along real, road-snapped VehicleRoutes instead of
 * VehicleSimulator's random walk. Each vehicle gets its own periodic
 * task, just like VehicleSimulator -- these threads only ever call
 * the thread-safe VehicleTracker, never touch Swing directly.
 *
 * When a vehicle reaches the end of its route it is removed from the
 * tracker (so the view stops drawing it) and, optionally, respawned
 * at the start of the same route after a pause. That keeps a demo
 * looking "alive" instead of every vehicle vanishing for good after
 * one trip -- set respawnDelayMs to a negative number to disable
 * that and have vehicles disappear for good, exactly as requested.
 */
public class RouteFollowingVehicleSimulator {
    private static final long TICK_MS = 200;

    private final VehicleTracker tracker;
    private final MapProjector projector;
    private final Map<String, VehicleRoute> routes;
    private final double speedMetersPerTick;
    private final long respawnDelayMs;

    private final Map<String, Double> traveledMeters = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Random random = new Random();

    public RouteFollowingVehicleSimulator(VehicleTracker tracker,
                                           MapProjector projector,
                                           Map<String, VehicleRoute> routes,
                                           double speedMetersPerTick,
                                           long respawnDelayMs) {
        this.tracker = tracker;
        this.projector = projector;
        this.routes = routes;
        this.speedMetersPerTick = speedMetersPerTick;
        this.respawnDelayMs = respawnDelayMs;
        this.scheduler = Executors.newScheduledThreadPool(Math.max(1, routes.size()));
    }

    /** Starts one independent "GPS feed" per vehicle, each following its own route. */
    public void start() {
        for (String id : routes.keySet()) {
            traveledMeters.put(id, 0.0);
            long initialDelayMs = random.nextInt(500);
            scheduleAdvancing(id, initialDelayMs);
        }
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void scheduleAdvancing(String id, long initialDelayMs) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> advance(id), initialDelayMs, TICK_MS, TimeUnit.MILLISECONDS);
        tasks.put(id, future);
    }

    private void advance(String id) {
        VehicleRoute route = routes.get(id);
        double traveled = traveledMeters.merge(id, speedMetersPerTick, Double::sum);
        GeoPoint position = route.pointAtDistance(traveled);
        Point pixel = projector.toPixel(position);
        tracker.setLocation(id, pixel.x, pixel.y);

        if (traveled >= route.lengthMeters()) {
            tracker.removeVehicle(id);
            ScheduledFuture<?> selfFuture = tasks.get(id);
            if (selfFuture != null) {
                selfFuture.cancel(false); // stop rescheduling; it just ran, so don't interrupt it
            }
            if (respawnDelayMs >= 0) {
                scheduler.schedule(() -> respawn(id), respawnDelayMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void respawn(String id) {
        VehicleRoute route = routes.get(id);
        traveledMeters.put(id, 0.0);
        Point start = projector.toPixel(route.start());
        tracker.addVehicle(id, start.x, start.y);
        scheduleAdvancing(id, 0);
    }
}
