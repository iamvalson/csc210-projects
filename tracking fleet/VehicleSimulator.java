import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Stands in for real GPS devices / dispatcher input. Each vehicle gets
 * its own periodic task on a shared thread pool, running completely
 * independently of the Swing Event Dispatch Thread -- exactly like the
 * "updater threads" described in the chapter would.
 *
 * These threads only ever call the thread-safe VehicleTracker; they
 * never touch a Swing component, so there's no need for
 * SwingUtilities.invokeLater here at all.
 */
public class VehicleSimulator {
    private final VehicleTracker tracker;
    private final List<String> vehicleIds;
    private final int worldSize;
    private final ScheduledExecutorService scheduler;
    private final Random random = new Random();

    public VehicleSimulator(VehicleTracker tracker, List<String> vehicleIds, int worldSize) {
        this.tracker = tracker;
        this.vehicleIds = vehicleIds;
        this.worldSize = worldSize;
        this.scheduler = Executors.newScheduledThreadPool(vehicleIds.size());
    }

    /** Starts one independent "GPS feed" per vehicle. */
    public void start() {
        for (String id : vehicleIds) {
            long initialDelayMs = random.nextInt(500);
            long periodMs = 300 + random.nextInt(700); // each vehicle reports at its own rate
            scheduler.scheduleAtFixedRate(
                    () -> moveRandomly(id), initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void moveRandomly(String id) {
        Point current = tracker.getLocation(id);
        if (current == null) {
            return;
        }
        int dx = random.nextInt(21) - 10; // step of -10..+10
        int dy = random.nextInt(21) - 10;
        int newX = clamp(current.x + dx, 0, worldSize - 1);
        int newY = clamp(current.y + dy, 0, worldSize - 1);
        tracker.setLocation(id, newX, newY);
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
