/**
 * A mutable 2D point. Deliberately NOT thread-safe on its own: two
 * threads writing x/y at the same time, or one reading while another
 * writes, is a data race.
 *
 * It's safe to use ONLY because MonitorVehicleTracker guards every
 * access to a MutablePoint with its own intrinsic lock, and never lets
 * a live MutablePoint escape to a caller (it always hands out copies
 * made with the copy constructor below).
 */
public class MutablePoint {
    public int x;
    public int y;

    public MutablePoint() {
        this.x = 0;
        this.y = 0;
    }

    public MutablePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public MutablePoint(MutablePoint p) {
        this.x = p.x;
        this.y = p.y;
    }
}
