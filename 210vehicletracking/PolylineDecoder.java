import java.util.ArrayList;
import java.util.List;

/**
 * Decodes strings in Google's "Encoded Polyline Algorithm Format" --
 * the compact string the Directions API returns in
 * routes[0].overview_polyline.points, tracing the actual road path
 * between two locations.
 *
 * This is Google's own published algorithm (see "Encoded Polyline
 * Algorithm Format" in the Google Maps Platform docs), not a
 * proprietary format -- reimplementing a small decoder for it is
 * standard practice and is what every Google Maps client library
 * does under the hood.
 */
public final class PolylineDecoder {

    private PolylineDecoder() {
    }

    public static List<GeoPoint> decode(String encoded) {
        List<GeoPoint> path = new ArrayList<>();
        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int result = 0;
            int shift = 0;
            int b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += deltaLat;

            result = 0;
            shift = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += deltaLng;

            path.add(new GeoPoint(lat / 1e5, lng / 1e5));
        }
        return path;
    }
}
