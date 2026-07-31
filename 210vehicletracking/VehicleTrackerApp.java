import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Wires together:
 *  - a thread-safe VehicleTracker (the data model),
 *  - RouteFollowingVehicleSimulator (background "updater" threads
 *    that move each vehicle along a REAL, road-snapped route fetched
 *    from the Mapbox Directions API),
 *  - VehicleTrackerPanel (the Swing view, now painting a Mapbox
 *    Static Images background of the University of Lagos).
 *
 * Requires a Mapbox access token, supplied via the
 * MAPBOX_ACCESS_TOKEN environment variable. Without one, this still
 * runs -- vehicles just travel in straight lines over a plain
 * background instead of following real roads over real map imagery,
 * so you can see the mechanics (motion + disappearing at the
 * destination) with zero setup. See README.md for how to get a token.
 *
 * Flip USE_DELEGATING_TRACKER to compare the two thread-safety
 * strategies from earlier in the chapter, exactly as before.
 */
public class VehicleTrackerApp {

    private static final boolean USE_DELEGATING_TRACKER = false;

    // ---- University of Lagos, Akoka campus ----
    private static final GeoPoint MAP_CENTER = new GeoPoint(6.5180, 3.3925);
    private static final int ZOOM = 16;
    private static final int LOGICAL_SIZE = 640;   // the Static Maps "size" param (640x640)
    private static final int SCALE = 2;             // 1 or 2; 2 = sharper "retina" image
    private static final String DIRECTIONS_PROFILE = "driving"; // try "walking" if a route comes back empty

    private static final GeoPoint MAIN_GATE = new GeoPoint(6.518837, 3.3844675);
    private static final GeoPoint GATE_HOUSE = new GeoPoint(6.5176648, 3.3852921);
    private static final GeoPoint FACULTY_OF_ENGINEERING = new GeoPoint(6.5181305, 3.3994649);
    private static final GeoPoint SENATE_BUILDING = new GeoPoint(6.5194683, 3.3987129);
    private static final GeoPoint SPORTS_CENTER = new GeoPoint(6.5166212, 3.3865737);
    private static final GeoPoint ENGINEERING_LECTURE_THEATRE = new GeoPoint(6.5179126, 3.3998176);

    private static final double SPEED_METERS_PER_TICK = 4.0; // ~72 km/h at the 200ms tick rate
    private static final long RESPAWN_DELAY_MS = 4000;        // set to -1 to have vehicles vanish for good

    private static final class Trip {
        final String vehicleId;
        final GeoPoint origin;
        final GeoPoint destination;

        Trip(String vehicleId, GeoPoint origin, GeoPoint destination) {
            this.vehicleId = vehicleId;
            this.origin = origin;
            this.destination = destination;
        }
    }

    private static final List<Trip> TRIPS = List.of(
            new Trip("Cab-1", MAIN_GATE, FACULTY_OF_ENGINEERING),
            new Trip("Cab-2", GATE_HOUSE, SENATE_BUILDING),
            new Trip("Truck-A", SPORTS_CENTER, ENGINEERING_LECTURE_THEATRE),
            new Trip("Cab-3", SENATE_BUILDING, MAIN_GATE)
    );

    public static void main(String[] args) {
        String accessToken = System.getenv("MAPBOX_ACCESS_TOKEN");
        boolean liveMode = accessToken != null && !accessToken.isBlank();
        if (!liveMode) {
            System.err.println("No MAPBOX_ACCESS_TOKEN set -- running in offline demo mode "
                    + "(straight-line routes, no map image). Set that environment variable to a "
                    + "Mapbox access token to see real roads over a real UNILAG map. See README.md.");
        }

        MapProjector projector = new MapProjector(MAP_CENTER, ZOOM, LOGICAL_SIZE, LOGICAL_SIZE, SCALE);
        BufferedImage background = liveMode ? fetchBackground(accessToken) : null;
        Map<String, VehicleRoute> routes = buildRoutes(accessToken, liveMode);
        VehicleTracker tracker = createTracker(routes, projector);

        RouteFollowingVehicleSimulator simulator = new RouteFollowingVehicleSimulator(
                tracker, projector, routes, SPEED_METERS_PER_TICK, RESPAWN_DELAY_MS);
        simulator.start();

        SwingUtilities.invokeLater(() -> createAndShowGui(tracker, projector, background));
    }

    private static BufferedImage fetchBackground(String accessToken) {
        try {
            return new MapboxStaticMapClient(accessToken)
                    .fetch(MAP_CENTER, ZOOM, LOGICAL_SIZE, LOGICAL_SIZE, SCALE);
        } catch (Exception e) {
            System.err.println("Couldn't fetch the static map background, continuing with a "
                    + "plain background instead: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, VehicleRoute> buildRoutes(String accessToken, boolean liveMode) {
        MapboxDirectionsClient directions = liveMode ? new MapboxDirectionsClient(accessToken) : null;
        Map<String, VehicleRoute> routes = new HashMap<>();
        for (Trip trip : TRIPS) {
            routes.put(trip.vehicleId, buildRoute(directions, trip.origin, trip.destination));
        }
        return routes;
    }

    private static VehicleRoute buildRoute(MapboxDirectionsClient directions, GeoPoint origin, GeoPoint destination) {
        if (directions != null) {
            try {
                List<GeoPoint> path = directions.fetchRoute(origin, destination, DIRECTIONS_PROFILE);
                return new VehicleRoute(path);
            } catch (Exception e) {
                System.err.println("Falling back to a straight line for " + origin + " -> "
                        + destination + " (" + e.getMessage() + ")");
            }
        }
        return new VehicleRoute(List.of(origin, destination));
    }

    private static VehicleTracker createTracker(Map<String, VehicleRoute> routes, MapProjector projector) {
        if (USE_DELEGATING_TRACKER) {
            Map<String, Point> initial = new HashMap<>();
            for (Map.Entry<String, VehicleRoute> entry : routes.entrySet()) {
                Point p = projector.toPixel(entry.getValue().start());
                initial.put(entry.getKey(), new Point(p.x, p.y));
            }
            return new DelegatingVehicleTracker(initial);
        } else {
            Map<String, MutablePoint> initial = new HashMap<>();
            for (Map.Entry<String, VehicleRoute> entry : routes.entrySet()) {
                Point p = projector.toPixel(entry.getValue().start());
                initial.put(entry.getKey(), new MutablePoint(p.x, p.y));
            }
            return new MonitorVehicleTracker(initial);
        }
    }

    private static void createAndShowGui(VehicleTracker tracker, MapProjector projector, BufferedImage background) {
        JFrame frame = new JFrame("Fleet Vehicle Tracker -- University of Lagos");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        VehicleTrackerPanel panel = new VehicleTrackerPanel(
                tracker, projector.bitmapWidth(), projector.bitmapHeight(), background);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // The "view thread": a javax.swing.Timer always fires its
        // listener on the Event Dispatch Thread, so calling repaint()
        // here is always safe, no matter what the updater threads
        // (in RouteFollowingVehicleSimulator) are doing concurrently
        // to the tracker.
        Timer repaintTimer = new Timer(100, e -> panel.repaint());
        repaintTimer.start();
    }
}
