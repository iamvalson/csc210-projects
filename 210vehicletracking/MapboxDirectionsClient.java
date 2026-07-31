import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches a real, road-snapped path between two points from the
 * Mapbox Directions API and decodes it into a list of GeoPoints.
 *
 * Requesting geometries=polyline (not polyline6) makes Mapbox encode
 * the route in the exact same Google Encoded Polyline format (5
 * decimal places) that PolylineDecoder already handles -- no
 * provider-specific decoding needed here.
 */
public final class MapboxDirectionsClient {
    private static final Pattern CODE_PATTERN = Pattern.compile("\"code\"\\s*:\\s*\"([A-Za-z]+)\"");
    private static final Pattern GEOMETRY_PATTERN = Pattern.compile("\"geometry\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newHttpClient();
    private final String accessToken;

    public MapboxDirectionsClient(String accessToken) {
        this.accessToken = accessToken;
    }

    /** profile: "driving" for real roads, "walking" for footpaths, "cycling" for bike paths. */
    public List<GeoPoint> fetchRoute(GeoPoint origin, GeoPoint destination, String profile)
            throws IOException, InterruptedException {
        String coordinates = origin.lng + "," + origin.lat + ";" + destination.lng + "," + destination.lat;
        String url = "https://api.mapbox.com/directions/v5/mapbox/" + encode(profile) + "/"
                + coordinates
                + "?geometries=polyline"
                + "&access_token=" + encode(accessToken);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();

        Matcher codeMatcher = CODE_PATTERN.matcher(body);
        String code = codeMatcher.find() ? codeMatcher.group(1) : "Unknown";
        if (!"Ok".equals(code)) {
            throw new IOException("Directions API returned code " + code
                    + " for " + origin + " -> " + destination + ". Raw response: " + body);
        }

        Matcher geometryMatcher = GEOMETRY_PATTERN.matcher(body);
        if (!geometryMatcher.find()) {
            throw new IOException("Couldn't find a route geometry in Directions response: " + body);
        }
        return PolylineDecoder.decode(geometryMatcher.group(1));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
