package lat.nexofood.api.common.util;

import lat.nexofood.api.common.exception.BadRequestException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeoUtils {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double EARTH_RADIUS_KM = 6371.0088;

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

    public static double calculateDistanceKm(Point from, Point to) {
        if (from == null || to == null) {
            throw new BadRequestException("Ambos puntos geográficos son obligatorios para calcular la distancia");
        }
        return calculateDistanceKm(from.getY(), from.getX(), to.getY(), to.getX());
    }

    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
