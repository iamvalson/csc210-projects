import java.util.Objects;

/**
 * An immutable real-world coordinate (WGS84 latitude/longitude) --
 * as opposed to Point/MutablePoint, which are pixel coordinates on
 * screen.
 *
 * Vehicles move through GeoPoint space (real coordinates along a
 * real road), and MapProjector converts each GeoPoint into a screen
 * Point right before it's handed to the VehicleTracker.
 */
public final class GeoPoint {
    public final double lat;
    public final double lng;

    public GeoPoint(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    @Override
    public String toString() {
        return "(" + lat + ", " + lng + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoPoint)) return false;
        GeoPoint other = (GeoPoint) o;
        return Double.compare(lat, other.lat) == 0 && Double.compare(lng, other.lng) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lat, lng);
    }
}
