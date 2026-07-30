import java.util.Map;

/**
 * The contract the GUI (view thread) and updater threads program against.
 * Both implementations below satisfy this same interface and are
 * fully thread-safe, but they get there in different ways:
 *
 *   - MonitorVehicleTracker: one lock guards mutable MutablePoints;
 *     every read defensively copies.
 *   - DelegatingVehicleTracker: thread safety is delegated to a
 *     ConcurrentHashMap holding immutable Points; no copying needed.
 *
 * Callers never need to know or care which one they're using.
 */
public interface VehicleTracker {
    Map<String, Point> getLocations();
    Point getLocation(String id);
    void setLocation(String id, int x, int y);
}
