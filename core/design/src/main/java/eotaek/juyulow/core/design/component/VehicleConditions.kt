package eotaek.juyulow.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.core.model.RefuelConditions
import eotaek.juyulow.core.model.RefuelValueSource
import eotaek.juyulow.core.model.VehicleEnergy
import eotaek.juyulow.core.model.VehicleFetchStatus
import eotaek.juyulow.core.model.VehicleField
import eotaek.juyulow.core.model.VehicleProperty
import eotaek.juyulow.core.model.VehicleValue
import eotaek.juyulow.core.model.VehicleValueOrigin
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

fun vehicleNumber(value: Double): String =
    DecimalFormat("0.#", DecimalFormatSymbols(Locale.KOREA)).format(value)

fun RefuelValueSource.displayLabel(): String = when (this) {
    RefuelValueSource.MANUAL -> "수동"
    RefuelValueSource.TANK_LEVEL -> "자동 · 잔량 L"
    RefuelValueSource.FUEL_PERCENT -> "자동 · 잔량 %"
    RefuelValueSource.CUMULATIVE_ECONOMY -> "자동 · 누적"
    RefuelValueSource.CURRENT_ECONOMY -> "자동 · 현재"
}

fun VehicleEnergy.displayLabel(): String {
    if (codes.isEmpty()) return if (status == VehicleFetchStatus.PENDING) "조회 중" else "확인 불가"
    val names = codes.map { code ->
        when (code) {
            0 -> "알 수 없음"
            1 -> "휘발유(무연)"
            2 -> "휘발유(유연)"
            3, 4 -> "경유"
            5 -> "바이오디젤"
            6 -> "E85"
            7 -> "LPG"
            8 -> "CNG"
            9 -> "LNG"
            10 -> "전기"
            11 -> "수소"
            12 -> "기타"
            else -> "알 수 없음($code)"
        }
    }.distinct().joinToString(" · ")
    return names + if (origin == VehicleValueOrigin.DEFAULT) " (기본값)" else ""
}

@Composable
fun VehicleEnergySummary(
    energy: VehicleEnergy,
    modifier: Modifier = Modifier,
    showElectricNotice: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "차량 에너지원: ${energy.displayLabel()}",
            style = JUYULOWTheme.typography.labelSmall,
            color = JUYULOWTheme.colors.textDarkSecondary,
        )
        if (showElectricNotice && energy.includesElectric) Text(
            "전기차 전비·충전 비교는 지원하지 않아요. 검색 유종은 설정에서 선택해요.",
            style = JUYULOWTheme.typography.labelSmall,
            color = JUYULOWTheme.colors.textDarkTertiary,
        )
    }
}

@Composable
fun RefuelConditionsSummary(conditions: RefuelConditions, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        VehicleEnergySummary(conditions.energy)
        Text(
            "검색 반경: ${conditions.searchRadiusKm} km · 검색 유종: ${conditions.comparisonFuel.label}",
            style = JUYULOWTheme.typography.labelSmall
        )
        Text(
            "${vehicleNumber(conditions.liters)} L (${conditions.litersSource.displayLabel()}) · " +
                    "연비 ${vehicleNumber(conditions.efficiencyKmPerL)} km/L (${conditions.efficiencySource.displayLabel()})",
            style = JUYULOWTheme.typography.labelSmall,
            color = JUYULOWTheme.colors.textDarkSecondary,
        )
    }
}

/** Diagnostic/default values are visible here but never mislabeled as fetched calculation inputs. */
fun vehicleFieldDisplay(property: VehicleProperty, field: VehicleField?): String {
    val number = (field?.value as? VehicleValue.Number)?.value
        ?: return if (field == null || field.status == VehicleFetchStatus.PENDING) "조회 중" else "확인 불가"
    val text = when (property) {
        VehicleProperty.CUMULATIVE_ECONOMY, VehicleProperty.CURRENT_ECONOMY ->
            "${vehicleNumber(100.0 / number)} km/L"

        VehicleProperty.FUEL_CAPACITY, VehicleProperty.FUEL_LEVEL -> "${vehicleNumber(number / 1000.0)} L"
        VehicleProperty.FUEL_PERCENT -> "${vehicleNumber(number)} %"
        VehicleProperty.FUEL_TYPES -> error("Use VehicleEnergy.displayLabel for fuel types")
    }
    return text + if (field.origin == VehicleValueOrigin.DEFAULT) " (기본값)" else ""
}
