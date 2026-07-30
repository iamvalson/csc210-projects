import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Map;
import javax.swing.JPanel;

/**
 * The "view". Every time it's asked to repaint, it takes a fresh
 * snapshot from the tracker and draws it. Because this method runs on
 * the Event Dispatch Thread (see VehicleTrackerApp's Timer), it never
 * needs its own synchronization -- Swing's single-thread rule takes
 * care of that side, and VehicleTracker's own thread safety takes care
 * of the data side.
 */
public class VehicleTrackerPanel extends JPanel {
    private final VehicleTracker tracker;
    private final int worldSize;

    public VehicleTrackerPanel(VehicleTracker tracker, int worldSize) {
        this.tracker = tracker;
        this.worldSize = worldSize;
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(600, 600));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Map<String, Point> snapshot = tracker.getLocations();
        double scaleX = getWidth() / (double) worldSize;
        double scaleY = getHeight() / (double) worldSize;

        for (Map.Entry<String, Point> entry : snapshot.entrySet()) {
            Point p = entry.getValue();
            int px = (int) (p.x * scaleX);
            int py = (int) (p.y * scaleY);

            g2.setColor(colorFor(entry.getKey()));
            g2.fillOval(px - 6, py - 6, 12, 12);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(entry.getKey(), px + 8, py - 8);
        }
    }

    private Color colorFor(String id) {
        float hue = (id.hashCode() & 0xFF) / 255f;
        return Color.getHSBColor(hue, 0.65f, 0.85f);
    }
}
