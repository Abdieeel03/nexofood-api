package lat.nexofood.api.common.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeoUtils {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private GeoUtils() {}

    public static Point createPoint(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    public static Double getLatitude(Point point) {
        return point != null ? point.getY() : null;
    }

    public static Double getLongitude(Point point) {
        return point != null ? point.getX() : null;
    }
}
