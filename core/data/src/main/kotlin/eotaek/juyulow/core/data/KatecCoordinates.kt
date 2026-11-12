package eotaek.juyulow.core.data

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

data class Wgs84Coordinate(val latitude: Double, val longitude: Double)
data class KatecCoordinate(val x: Double, val y: Double)

/** Navigation KATEC (Bessel), not EPSG:5179/UTM-K. Axis order is always x/lon, y/lat. */
class KatecCoordinates {
    private val factory = CRSFactory()
    private val katec = factory.createFromParameters(
        "KATEC",
        "+proj=tmerc +lat_0=38 +lon_0=128 +k=0.9999 +x_0=400000 +y_0=600000 " +
                "+ellps=bessel +towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43 +units=m +no_defs"
    )
    private val wgs = factory.createFromParameters("WGS84", "+proj=longlat +datum=WGS84 +no_defs")
    private val transforms = CoordinateTransformFactory()

    fun toWgs84(x: Double, y: Double): Wgs84Coordinate {
        require(x.isFinite() && y.isFinite())
        val output = ProjCoordinate()
        transforms.createTransform(katec, wgs).transform(ProjCoordinate(x, y), output)
        require(output.x.isFinite() && output.y.isFinite() && output.y in 33.0..39.5 && output.x in 124.0..132.0)
        return Wgs84Coordinate(output.y, output.x)
    }

    fun toKatec(latitude: Double, longitude: Double): KatecCoordinate {
        require(latitude.isFinite() && longitude.isFinite() && latitude in 33.0..39.5 && longitude in 124.0..132.0)
        val output = ProjCoordinate()
        transforms.createTransform(wgs, katec)
            .transform(ProjCoordinate(longitude, latitude), output)
        require(output.x.isFinite() && output.y.isFinite())
        return KatecCoordinate(output.x, output.y)
    }
}
