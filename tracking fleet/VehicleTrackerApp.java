import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Wires together:
 *  - a thread-safe VehicleTracker (the data model),
 *  - VehicleSimulator (background "updater" threads standing in for GPS/dispatch input),
 *  - VehicleTrackerPanel (the Swing view).
 *
 * Flip USE_DELEGATING_TRACKER to compare the two thread-safety strategies
 * from the chapter without changing anything else.
 */
public class VehicleTrackerApp {

    private static final boolean USE_DELEGATING_TRACKER = false;
    private static final int WORLD_SIZE = 100;
    private static final int REPAINT_INTERVAL_MS = 100;
    private static final String[] VEHICLE_IDS = {
        "Cab-1", "Cab-2", "Police-7", "Truck-A", "Truck-B", "Cab-3"
    };

    public static void main(String[] args) {
        VehicleTracker tracker = createTracker();

        VehicleSimulator simulator =
                new VehicleSimulator(tracker, Arrays.asList(VEHICLE_IDS), WORLD_SIZE);
        simulator.start();

        SwingUtilities.invokeLater(() -> createAndShowGui(tracker));
    }

    private static VehicleTracker createTracker() {
        Random random = new Random();
        if (USE_DELEGATING_TRACKER) {
            Map<String, Point> initial = new HashMap<>();
            for (String id : VEHICLE_IDS) {
                initial.put(id, new Point(random.nextInt(WORLD_SIZE), random.nextInt(WORLD_SIZE)));
            }
            return new DelegatingVehicleTracker(initial);
        } else {
            Map<String, MutablePoint> initial = new HashMap<>();
            for (String id : VEHICLE_IDS) {
                initial.put(id, new MutablePoint(random.nextInt(WORLD_SIZE), random.nextInt(WORLD_SIZE)));
            }
            return new MonitorVehicleTracker(initial);
        }
    }

    private static void createAndShowGui(VehicleTracker tracker) {
        JFrame frame = new JFrame("Fleet Vehicle Tracker");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        VehicleTrackerPanel panel = new VehicleTrackerPanel(tracker, WORLD_SIZE);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // The "view thread": a javax.swing.Timer always fires its
        // listener on the Event Dispatch Thread, so calling repaint()
        // here is always safe, no matter what the updater threads
        // (in VehicleSimulator) are doing concurrently to the tracker.
        Timer repaintTimer = new Timer(REPAINT_INTERVAL_MS, e -> panel.repaint());
        repaintTimer.start();
    }
}
