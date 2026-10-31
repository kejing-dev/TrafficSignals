package io.embodit.trafficsignals.platform

import com.pico.spatial.tracking.controller.ControllerActionData
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thumbstick X of either controller (design I8: lesson mode turns the officer). Read from
 * `ControllerTrackingProvider.ControllerActionListener` (same path as Figure Model's ControllerInput): the listener runs
 * on the data source's thread and only stores the value; the frame system reads [x]. The stick with the larger
 * deflection wins. Debug `stick X` overrides it (QA harness).
 */
class Stick {
    private var provider: ControllerTrackingProvider? = null
    private val right = AtomicInteger(0)
    private val left = AtomicInteger(0)

    @Volatile var debugX: Float? = null

    private val listener =
        ControllerTrackingProvider.ControllerActionListener { a: ControllerActionData ->
            right.set(java.lang.Float.floatToIntBits(runCatching { a.right.thumbstickValue.x }.getOrDefault(0f)))
            left.set(java.lang.Float.floatToIntBits(runCatching { a.left.thumbstickValue.x }.getOrDefault(0f)))
        }

    fun start() {
        if (provider != null) return
        val p = ControllerTrackingProvider()
        runCatching { p.addControllerActionListener(listener) }
        val r = runCatching { p.start() }
        TsLog.i("TRACK controller start=${r.getOrNull() ?: r.exceptionOrNull()} support=${runCatching { p.supportState }.getOrNull()}")
        provider = p
    }

    fun stop() {
        provider?.let { p ->
            runCatching { p.removeControllerActionListener(listener) }
            runCatching { p.stop() }
        }
        provider = null
        right.set(0)
        left.set(0)
    }

    /** -1 (left) .. 1 (right). */
    val x: Float
        get() {
            debugX?.let { return it }
            val r = java.lang.Float.intBitsToFloat(right.get())
            val l = java.lang.Float.intBitsToFloat(left.get())
            return if (kotlin.math.abs(r) >= kotlin.math.abs(l)) r else l
        }
}
