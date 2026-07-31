import java.util.List;

/**
 * A road-snapped path for one vehicle to travel, as a sequence of
 * GeoPoints (usually decoded from a Directions API polyline, but a
 * plain two-point straight line works too -- see the offline fallback
 * in VehicleTrackerApp). Vehicles move through this by distance
 * traveled, not by waypoint index, so speed stays roughly constant
 * even though real polyline waypoints aren't evenly spaced.
 */
public final class VehicleRoute {
    private final List<GeoPoint> waypoints;
    private final double[] cumulativeMeters;
    private final double totalMeters;

    public VehicleRoute(List<GeoPoint> waypoints) {
        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("A route needs at least 2 points");
        }
        this.waypoints = List.copyOf(waypoints);
        this.cumulativeMeters = new double[this.waypoints.size()];
        double acc = 0;
        for (int i = 1; i < this.waypoints.size(); i++) {
            acc += haversineMeters(this.waypoints.get(i - 1), this.waypoints.get(i));
            cumulativeMeters[i] = acc;
        }
        this.totalMeters = acc;
    }

    public double lengthMeters() {
        return totalMeters;
    }

    public GeoPoint start() {
        return waypoints.get(0);
    }

    /** The interpolated position after traveling this far along the route. */
    public GeoPoint pointAtDistance(double distanceMeters) {
        double d = Math.max(0, Math.min(distanceMeters, totalMeters));

        int i = 1;
        while (i < cumulativeMeters.length - 1 && cumulativeMeters[i] < d) {
            i++;
        }
        double segStart = cumulativeMeters[i - 1];
        double segEnd = cumulativeMeters[i];
        double segLen = segEnd - segStart;
        double t = segLen <= 0 ? 0 : (d - segStart) / segLen;

        GeoPoint a = waypoints.get(i - 1);
        GeoPoint b = waypoints.get(i);
        return new GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t);
    }

    private static double haversineMeters(GeoPoint a, GeoPoint b) {
        double radius = 6_371_000;
        double dLat = Math.toRadians(b.lat - a.lat);
        double dLng = Math.toRadians(b.lng - a.lng);
        double la1 = Math.toRadians(a.lat);
        double la2 = Math.toRadians(b.lat);

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(la1) * Math.cos(la2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * radius * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
