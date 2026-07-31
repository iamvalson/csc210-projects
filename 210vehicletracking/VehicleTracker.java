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
 *
 * setLocation() moves a vehicle that's already being tracked and
 * throws if the id is unknown -- that strictness (from the original
 * chapter example) is deliberate, since it surfaces a typo'd id
 * immediately instead of it silently doing nothing. addVehicle() and
 * removeVehicle() are the explicit way to add a brand-new vehicle
 * (e.g. one respawning onto a new route) or make one disappear (e.g.
 * it just arrived at its destination).
 */
public interface VehicleTracker {
    Map<String, Point> getLocations();
    Point getLocation(String id);
    void setLocation(String id, int x, int y);
    void addVehicle(String id, int x, int y);
    void removeVehicle(String id);
}
