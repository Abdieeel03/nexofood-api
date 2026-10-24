package lat.nexofood.api.common.util;

import lat.nexofood.api.common.exception.BadRequestException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeoUtils {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private GeoUtils() {}

    public static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new BadRequestException("La latitud y longitud son obligatorias");
        }
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new BadRequestException("La latitud debe estar entre -90.0 y 90.0 grados");
        }
        if (Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new BadRequestException("La longitud debe estar entre -180.0 y 180.0 grados");
        }
    }

    public static Point createPoint(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        validateCoordinates(latitude, longitude);
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    public static Double getLatitude(Point point) {
        return point != null ? point.getY() : null;
    }

    public static Double getLongitude(Point point) {
        return point != null ? point.getX() : null;
    }
}
