import java.util.Objects;

/**
 * An immutable 2D point.
 *
 * Because every field is final and set once in the constructor, a
 * correctly-constructed Point is safe to hand to any thread without
 * synchronization: there is no mutable state for two threads to race on.
 * This is what lets DelegatingVehicleTracker skip the defensive copying
 * that MonitorVehicleTracker needs to do.
 */
public final class Point {
    public final int x;
    public final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Point(Point p) {
        this(p.x, p.y);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point)) return false;
        Point other = (Point) o;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
