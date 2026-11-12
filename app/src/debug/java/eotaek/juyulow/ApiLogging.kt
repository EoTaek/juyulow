package eotaek.juyulow

import eotaek.juyulow.core.data.ApiLog
import eotaek.juyulow.core.data.ApiLogger
import timber.log.Timber

internal fun initializeApiLogging() {
    Timber.plant(Timber.DebugTree())
    ApiLog.logger = ApiLogger { level, provider, operation, event, fields ->
        val text =
            "provider=$provider op=$operation event=$event " + fields().entries.joinToString(" ") {
                it.key + "=" + it.value.toString().replace("\n", "\\n").replace("\r", "\\r")
            }
        val chunks = text.chunked(700)
        chunks.forEachIndexed { index, part ->
            Timber.tag("JY.Api.$provider").log(
                when (level) {
                    "I" -> 4; "W" -> 5; "E" -> 6; else -> 3
                }, "part=%d/%d %s", index + 1, chunks.size, part
            )
        }
    }
}
