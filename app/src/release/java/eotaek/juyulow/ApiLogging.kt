package eotaek.juyulow

import eotaek.juyulow.core.data.ApiLog
import eotaek.juyulow.core.data.NoOpApiLogger

internal fun initializeApiLogging() {
    ApiLog.logger = NoOpApiLogger
}
