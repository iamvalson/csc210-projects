import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe by DELEGATING to an already-thread-safe building block
 * (ConcurrentHashMap) instead of managing a lock by hand.
 *
 * This only works because Point is immutable. An immutable object can
 * never be caught mid-mutation, so it's always safe to publish one
 * directly -- no defensive copying needed on read. "Moving" a vehicle
 * just means atomically swapping in a brand-new Point via replace().
 *
 * Trade-off vs. MonitorVehicleTracker: getLocations() here returns a
 * live, unmodifiable VIEW of the underlying map, not a frozen snapshot.
 * If a vehicle moves while the view thread is iterating, the new
 * position may or may not show up in that pass. For a fleet map that's
 * usually fine (and cheaper); if you need a guaranteed consistent
 * snapshot across the whole set of vehicles, use MonitorVehicleTracker
 * instead.
 */
public class DelegatingVehicleTracker implements VehicleTracker {
    private final ConcurrentHashMap<String, Point> locations;
    private final Map<String, Point> unmodifiableLocations;

    public DelegatingVehicleTracker(Map<String, Point> points) {
        this.locations = new ConcurrentHashMap<>(points);
        this.unmodifiableLocations = Collections.unmodifiableMap(locations);
    }

    @Override
    public Map<String, Point> getLocations() {
        // Safe to publish directly: the map is thread-safe and its
        // values are immutable, so there's nothing left to protect.
        return unmodifiableLocations;
    }

    @Override
    public Point getLocation(String id) {
        return locations.get(id);
    }

    @Override
    public void setLocation(String id, int x, int y) {
        if (locations.replace(id, new Point(x, y)) == null) {
            throw new IllegalArgumentException("No such vehicle: " + id);
        }
    }

    @Override
    public void addVehicle(String id, int x, int y) {
        locations.put(id, new Point(x, y));
    }

    @Override
    public void removeVehicle(String id) {
        locations.remove(id);
    }
}
