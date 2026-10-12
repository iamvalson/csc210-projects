import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.JPanel;

/**
 * The "view". Every time it's asked to repaint, it takes a fresh
 * snapshot from the tracker and draws it over a Mapbox Static Images
 * background image of the University of Lagos. Because this method
 * runs on the Event Dispatch Thread (see VehicleTrackerApp's Timer),
 * it never needs its own synchronization -- Swing's single-thread
 * rule takes care of that side, and VehicleTracker's own thread
 * safety takes care of the data side.
 *
 * Vehicle Points are expressed in a fixed "native" pixel space (see
 * MapProjector -- nativeWidth/nativeHeight below match its bitmap
 * size exactly). If the window gets resized or maximized to a
 * different size than that, everything -- the map image AND the
 * dots -- is scaled together via an AffineTransform, so the map
 * keeps filling the panel and the dots stay aligned with the roads
 * at any window size.
 *
 * When there's no map image (offline demo mode -- see
 * VehicleTrackerApp), a light grid is drawn instead of a blank
 * background, just so there's some visual reference for motion.
 */
public class VehicleTrackerPanel extends JPanel {
    private static final int GRID_SPACING = 40; // in native pixels
    private static final Color GRID_LINE_COLOR = new Color(225, 225, 225);
    private static final Color GRID_BACKGROUND_COLOR = new Color(248, 248, 248);

    private final VehicleTracker tracker;
    private final BufferedImage background; // nullable: grid background if no map was fetched
    private final int nativeWidth;
    private final int nativeHeight;

    public VehicleTrackerPanel(VehicleTracker tracker, int width, int height, BufferedImage background) {
        this.tracker = tracker;
        this.background = background;
        this.nativeWidth = width;
        this.nativeHeight = height;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(width, height));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double scaleX = getWidth() / (double) nativeWidth;
        double scaleY = getHeight() / (double) nativeHeight;
        g2.scale(scaleX, scaleY);

        if (background != null) {
            g2.drawImage(background, 0, 0, null);
        } else {
            drawGrid(g2);
        }

        Map<String, Point> snapshot = tracker.getLocations();
        for (Map.Entry<String, Point> entry : snapshot.entrySet()) {
            Point p = entry.getValue();

            g2.setColor(colorFor(entry.getKey()));
            g2.fillOval(p.x - 7, p.y - 7, 14, 14);
            g2.setColor(Color.WHITE);
            g2.drawOval(p.x - 7, p.y - 7, 14, 14); // outline so dots read clearly over map imagery

            g2.setColor(background != null ? Color.WHITE : Color.DARK_GRAY);
            g2.drawString(entry.getKey(), p.x + 9, p.y - 9);
        }

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(GRID_BACKGROUND_COLOR);
        g2.fillRect(0, 0, nativeWidth, nativeHeight);

        g2.setColor(GRID_LINE_COLOR);
        for (int x = 0; x <= nativeWidth; x += GRID_SPACING) {
            g2.drawLine(x, 0, x, nativeHeight);
        }
        for (int y = 0; y <= nativeHeight; y += GRID_SPACING) {
            g2.drawLine(0, y, nativeWidth, y);
        }
    }

    private Color colorFor(String id) {
        float hue = (id.hashCode() & 0xFF) / 255f;
        return Color.getHSBColor(hue, 0.75f, 0.9f);
    }
}
