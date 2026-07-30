import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe using the Java monitor pattern: all mutable state (the
 * map, and every MutablePoint inside it) is guarded by this object's
 * own intrinsic lock. Every public method is synchronized, so only one
 * thread can be touching the state at a time.
 *
 * Because MutablePoint is not thread-safe by itself, it must never
 * escape this class while still "live" (i.e. while another thread
 * could still mutate it). So every method that returns location data
 * returns a fresh copy instead, made while holding the lock.
 */
public class MonitorVehicleTracker implements VehicleTracker {
    private final Map<String, MutablePoint> locations;

    public MonitorVehicleTracker(Map<String, MutablePoint> locations) {
        this.locations = deepCopy(locations);
    }

    @Override
    public synchronized Map<String, Point> getLocations() {
        Map<String, Point> snapshot = new HashMap<>();
        for (Map.Entry<String, MutablePoint> entry : locations.entrySet()) {
            MutablePoint p = entry.getValue();
            snapshot.put(entry.getKey(), new Point(p.x, p.y));
        }
        return snapshot; // a brand-new map of immutable points: safe to hand out
    }

    @Override
    public synchronized Point getLocation(String id) {
        MutablePoint p = locations.get(id);
        return (p == null) ? null : new Point(p.x, p.y);
    }

    @Override
    public synchronized void setLocation(String id, int x, int y) {
        MutablePoint p = locations.get(id);
        if (p == null) {
            throw new IllegalArgumentException("No such vehicle: " + id);
        }
        p.x = x;
        p.y = y;
    }

    private static Map<String, MutablePoint> deepCopy(Map<String, MutablePoint> m) {
        Map<String, MutablePoint> copy = new HashMap<>();
        for (Map.Entry<String, MutablePoint> entry : m.entrySet()) {
            copy.put(entry.getKey(), new MutablePoint(entry.getValue()));
        }
        return copy;
    }
}
