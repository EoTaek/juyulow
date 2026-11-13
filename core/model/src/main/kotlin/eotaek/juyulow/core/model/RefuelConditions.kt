package eotaek.juyulow.core.model

enum class RefuelValueSource { MANUAL, TANK_LEVEL, FUEL_PERCENT, CUMULATIVE_ECONOMY, CURRENT_ECONOMY }
enum class ComparisonFuel(val label: String, val opinetCode: String) {
    GASOLINE("휘발유", "B027"),
    PREMIUM_GASOLINE("고급휘발유", "B034"),
    DIESEL("경유", "D047"),
}

/** Frozen calculation inputs; manual settings and SDK raw units remain separate. */
data class RefuelConditions(
    val liters: Double,
    val efficiencyKmPerL: Double,
    val refuelMinutes: Int,
    val litersSource: RefuelValueSource = RefuelValueSource.MANUAL,
    val efficiencySource: RefuelValueSource = RefuelValueSource.MANUAL,
    val energy: VehicleEnergy = VehicleEnergy(),
    val comparisonFuel: ComparisonFuel = ComparisonFuel.GASOLINE,
    val searchRadiusKm: Int = 3,
) {
    init {
        require(liters.isFinite() && liters >= 0)
        require(efficiencyKmPerL.isFinite() && efficiencyKmPerL > 0)
        require(refuelMinutes in 1..20)
    }

    val refuelSeconds: Int get() = refuelMinutes * 60
    val automaticLiters: Boolean get() = litersSource != RefuelValueSource.MANUAL
    val automaticEfficiency: Boolean get() = efficiencySource != RefuelValueSource.MANUAL
}

data class VehicleEnergy(
    val codes: List<Int> = emptyList(),
    val status: VehicleFetchStatus = VehicleFetchStatus.PENDING,
    val origin: VehicleValueOrigin = VehicleValueOrigin.UNSET,
) {
    val includesElectric: Boolean get() = 10 in codes
}

/** Only actual successful reads can replace the user's manual calculation defaults. */
fun resolveVehicleRefuelConditions(
    manual: RefuelSettings,
    fields: Map<VehicleProperty, VehicleField> = emptyMap(),
): RefuelConditions {
    fun fetchedNumber(property: VehicleProperty): Double? = fields[property]?.takeIf {
        it.status == VehicleFetchStatus.SUCCESS && it.origin == VehicleValueOrigin.FETCH && property.accepts(
            it.value
        )
    }?.let { (it.value as VehicleValue.Number).value }

    val capacity = fetchedNumber(VehicleProperty.FUEL_CAPACITY)
    val level =
        fetchedNumber(VehicleProperty.FUEL_LEVEL)?.takeIf { capacity != null && it <= capacity }
    val percent = fetchedNumber(VehicleProperty.FUEL_PERCENT)
    val liters = when {
        capacity != null && level != null -> (capacity - level) / 1000.0
        capacity != null && percent != null -> capacity / 1000.0 * (1.0 - percent / 100.0)
        else -> null
    }?.takeIf { it.isFinite() && it >= 0 }
    val cumulative = fetchedNumber(VehicleProperty.CUMULATIVE_ECONOMY)
    val current = fetchedNumber(VehicleProperty.CURRENT_ECONOMY)
    val fuelTypes = fields[VehicleProperty.FUEL_TYPES]
    return RefuelConditions(
        comparisonFuel = manual.comparisonFuel,
        searchRadiusKm = manual.searchRadiusKm,
        liters = liters ?: manual.liters.toDouble(),
        efficiencyKmPerL = (cumulative ?: current)?.let { 100.0 / it }
            ?: manual.efficiencyKmPerL.toDouble(),
        refuelMinutes = manual.refuelMinutes,
        litersSource = when {
            liters == null -> RefuelValueSource.MANUAL
            level != null -> RefuelValueSource.TANK_LEVEL
            else -> RefuelValueSource.FUEL_PERCENT
        },
        efficiencySource = when {
            cumulative != null -> RefuelValueSource.CUMULATIVE_ECONOMY
            current != null -> RefuelValueSource.CURRENT_ECONOMY
            else -> RefuelValueSource.MANUAL
        },
        energy = VehicleEnergy(
            (fuelTypes?.value as? VehicleValue.FuelTypes)?.codes?.distinct()?.sorted().orEmpty(),
            fuelTypes?.status ?: VehicleFetchStatus.PENDING,
            fuelTypes?.origin ?: VehicleValueOrigin.UNSET,
        ),
    )
}

/** Persisted settings supply active calculation inputs; vehicle values remain diagnostic. */
fun resolveRefuelConditions(manual: RefuelSettings, fields: Map<VehicleProperty, VehicleField> = emptyMap()): RefuelConditions =
    RefuelConditions(
        liters = manual.liters.toDouble(), efficiencyKmPerL = manual.efficiencyKmPerL.toDouble(),
        refuelMinutes = manual.refuelMinutes, comparisonFuel = manual.comparisonFuel,
        searchRadiusKm = manual.searchRadiusKm, energy = resolveVehicleRefuelConditions(manual, fields).energy,
    )
