/**
 * Converts real-world coordinates (GeoPoint: lat/lng) into pixel
 * coordinates (Point) that line up with a Google Static Maps image
 * fetched with the SAME center, zoom, size, and scale.
 *
 * This implements the Web Mercator projection Google Maps itself
 * uses (see "Map and Tile Coordinates" in the Google Maps Platform
 * docs): a lat/lng is first projected onto a 256x256 "world" tile,
 * then scaled by 2^zoom to get pixel coordinates, then offset from
 * the map's center point and multiplied by the "scale" (retina)
 * factor to land on the actual bitmap Google returns.
 */
public final class MapProjector {
    private static final double TILE_SIZE = 256.0;

    private final GeoPoint center;
    private final int zoom;
    private final int logicalWidth;
    private final int logicalHeight;
    private final int scale;

    /**
     * @param center        the "center" param used in the Static Maps request
     * @param zoom          the "zoom" param used in the Static Maps request
     * @param logicalWidth  width half of the "size" param (e.g. 640 in "640x640")
     * @param logicalHeight height half of the "size" param
     * @param scale         the "scale" param (1 or 2) -- the actual returned
     *                      bitmap is logicalWidth*scale by logicalHeight*scale pixels
     */
    public MapProjector(GeoPoint center, int zoom, int logicalWidth, int logicalHeight, int scale) {
        this.center = center;
        this.zoom = zoom;
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.scale = scale;
    }

    /** Width, in actual bitmap pixels, of the image this projector matches. */
    public int bitmapWidth() {
        return logicalWidth * scale;
    }

    /** Height, in actual bitmap pixels, of the image this projector matches. */
    public int bitmapHeight() {
        return logicalHeight * scale;
    }

    /** Projects a real-world point onto this map's pixel space. */
    public Point toPixel(GeoPoint p) {
        double[] centerWorld = project(center);
        double[] pointWorld = project(p);
        double factor = Math.pow(2, zoom);

        double dx = (pointWorld[0] - centerWorld[0]) * factor;
        double dy = (pointWorld[1] - centerWorld[1]) * factor;

        int screenX = (int) Math.round((logicalWidth / 2.0 + dx) * scale);
        int screenY = (int) Math.round((logicalHeight / 2.0 + dy) * scale);
        return new Point(screenX, screenY);
    }

    /** Projects a GeoPoint onto the 256x256 base Mercator "world" tile. */
    private static double[] project(GeoPoint p) {
        double siny = Math.sin(Math.toRadians(p.lat));
        siny = Math.min(Math.max(siny, -0.9999), 0.9999); // clamp near the poles
        double x = TILE_SIZE * (0.5 + p.lng / 360.0);
        double y = TILE_SIZE * (0.5 - Math.log((1 + siny) / (1 - siny)) / (4 * Math.PI));
        return new double[]{x, y};
    }
}
