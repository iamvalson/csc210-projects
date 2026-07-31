import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.imageio.ImageIO;

/**
 * Fetches a single background image from the Mapbox Static Images
 * API, matching a MapProjector's center/zoom/size/scale exactly so
 * that projected vehicle positions line up with the roads drawn in
 * the image.
 *
 * Unlike Google's Static Maps API, Mapbox takes coordinates as
 * lon,lat (not lat,lng) and puts the retina flag ("@2x") in the URL
 * path rather than as a separate query parameter.
 */
public final class MapboxStaticMapClient {
    private static final String STYLE = "mapbox/streets-v12";

    private final HttpClient http = HttpClient.newHttpClient();
    private final String accessToken;

    public MapboxStaticMapClient(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * @param center        map center
     * @param zoom          zoom level (e.g. 15 suits a single-campus view --
     *                      if roads look slightly offset from the dots after
     *                      switching providers, try nudging this by +-1 first)
     * @param logicalWidth  width half of the request (matches MapProjector's logicalWidth)
     * @param logicalHeight height half of the request (matches MapProjector's logicalHeight)
     * @param scale         1 or 2 -- 2 requests the "@2x" retina image
     */
    public BufferedImage fetch(GeoPoint center, int zoom, int logicalWidth, int logicalHeight, int scale)
            throws IOException, InterruptedException {
        String retina = scale >= 2 ? "@2x" : "";
        String url = "https://api.mapbox.com/styles/v1/" + STYLE + "/static/"
                + center.lng + "," + center.lat + "," + zoom + ",0,0/"
                + logicalWidth + "x" + logicalHeight + retina
                + "?access_token=" + accessToken;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Mapbox Static Images API returned HTTP " + response.statusCode()
                    + " -- check that the access token is valid.");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (image == null) {
            throw new IOException("Mapbox Static Images API response wasn't a readable image "
                    + "(the token or request was likely rejected).");
        }
        return image;
    }
}
