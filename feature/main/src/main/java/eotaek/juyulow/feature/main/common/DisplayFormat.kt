package eotaek.juyulow.feature.main.common

import eotaek.juyulow.core.model.JourneyMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

internal fun won(value: Double): String =
    NumberFormat.getIntegerInstance(Locale.KOREA).format(floor(value + .5).toLong())

internal fun minutes(seconds: Int): String = ceil(seconds / 60.0).toInt().toString()
internal fun kilometers(meters: Int): String =
    NumberFormat.getNumberInstance(Locale.KOREA).apply { maximumFractionDigits = 1 }
        .format(meters / 1000.0)

internal val JourneyMode.label: String
    get() = when (this) {
        JourneyMode.WAYPOINT -> "목적지 경유"
        JourneyMode.DESTINATION -> "주유소까지"
        JourneyMode.ROUND_TRIP -> "왕복"
    }
internal val JourneyMode.actionLabel: String
    get() = when (this) {
        JourneyMode.WAYPOINT -> "경유지로 추가"
        JourneyMode.DESTINATION -> "목적지로 설정"
        JourneyMode.ROUND_TRIP -> "왕복 경로 설정"
    }
