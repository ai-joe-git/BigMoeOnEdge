package io.bigmoeonedge.example

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared state between the RunService (writer) and MainActivity (reader). The service
 * updates it as CLI output arrives; the UI polls on a short tick. Kept deliberately
 * tiny — a single generation runs at a time.
 */
object RunBus {
    @Volatile var telemetry: Telemetry = Telemetry()
    @Volatile var summary: String = ""
    @Volatile var answer: String = ""
    @Volatile var error: String? = null
    val running = AtomicBoolean(false)

    fun reset() {
        telemetry = Telemetry()
        summary = ""
        answer = ""
        error = null
    }
}
