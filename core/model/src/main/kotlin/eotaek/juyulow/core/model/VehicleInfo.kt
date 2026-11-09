package eotaek.juyulow.core.model

enum class VehicleProvider { PLEOS, AAOS }
enum class VehicleUnit { LITERS_PER_100_KM, PERCENT, MILLILITERS, FUEL_TYPE_CODES }

enum class VehicleProperty(val provider: VehicleProvider, val unit: VehicleUnit) {
    CUMULATIVE_ECONOMY(VehicleProvider.PLEOS, VehicleUnit.LITERS_PER_100_KM),
    CURRENT_ECONOMY(VehicleProvider.PLEOS, VehicleUnit.LITERS_PER_100_KM),
    FUEL_PERCENT(VehicleProvider.PLEOS, VehicleUnit.PERCENT),
    FUEL_CAPACITY(VehicleProvider.AAOS, VehicleUnit.MILLILITERS),
    FUEL_LEVEL(VehicleProvider.AAOS, VehicleUnit.MILLILITERS),
    FUEL_TYPES(VehicleProvider.AAOS, VehicleUnit.FUEL_TYPE_CODES),
}

/** Fuel codes retain AAOS FuelType values, including unknown/future codes. No grade inference. */
sealed interface VehicleValue {
    data class Number(val value: Double) : VehicleValue
    data class FuelTypes(val codes: List<Int>) : VehicleValue
}

fun VehicleProperty.accepts(value: VehicleValue?): Boolean = when (this) {
    VehicleProperty.FUEL_TYPES -> value is VehicleValue.FuelTypes && value.codes.isNotEmpty() &&
            value.codes.all { it >= 0 }

    else -> value is VehicleValue.Number && value.value.isFinite() && when (this) {
        VehicleProperty.CUMULATIVE_ECONOMY, VehicleProperty.CURRENT_ECONOMY ->
            value.value > 0 && (100.0 / value.value).isFinite()

        VehicleProperty.FUEL_CAPACITY -> value.value > 0
        VehicleProperty.FUEL_LEVEL -> value.value >= 0
        VehicleProperty.FUEL_PERCENT -> value.value in 0.0..100.0
        VehicleProperty.FUEL_TYPES -> false
    }
}

enum class VehicleFetchStatus { PENDING, SUCCESS, FAILED }
enum class VehicleValueOrigin { FETCH, DEFAULT, UNSET }
enum class VehicleFailureReason { UNSUPPORTED, PERMISSION, CONNECTION, UNAVAILABLE, INVALID_VALUE, TIMEOUT, ERROR }
data class VehicleFailure(val reason: VehicleFailureReason, val stage: String, val message: String?)

data class VehicleField(
    val status: VehicleFetchStatus = VehicleFetchStatus.PENDING,
    val rawValue: VehicleValue? = null,
    val value: VehicleValue? = null,
    val origin: VehicleValueOrigin = VehicleValueOrigin.UNSET,
    val fetchedAtMillis: Long? = null,
    val failure: VehicleFailure? = null,
) {
    /** Only meaningful for an economy field; original L/100km remains in value/rawValue. */
    val efficiencyKmPerL: Double?
        get() = (value as? VehicleValue.Number)?.value
            ?.takeIf { it.isFinite() && it > 0 }?.let { 100.0 / it }?.takeIf { it.isFinite() }
}
