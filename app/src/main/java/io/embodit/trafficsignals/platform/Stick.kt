package io.embodit.trafficsignals.platform

import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.controller.ControllerActionData
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import io.embodit.trafficsignals.domain.Quat4
import io.embodit.trafficsignals.domain.Vec3
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

    /**
     * Ray of each tracked controller, Stage space (origin, unit direction along the controller's -Z), read once per frame
     * on the main thread. Empty with hands or on the 6.1 emulator (the provider says DEVICE_NOT_SUPPORTED there).
     */
    fun rays(): List<Pair<Vec3, Vec3>> {
        val p = provider ?: return emptyList()
        if (runCatching { p.supportState }.getOrNull() != DataProvider.SupportState.SUPPORTED) return emptyList()
        val data = runCatching { p.latestData }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<Vec3, Vec3>>(2)
        for (pose in listOfNotNull(data.right, data.left)) {
            val o = Vec3(pose.position.x, pose.position.y, pose.position.z)
            val d = Quat4(pose.rotation.x, pose.rotation.y, pose.rotation.z, pose.rotation.w).rotate(Vec3(0f, 0f, -1f))
            if (o.isFinite() && d.isFinite() && d.length > 0.5f) out += o to d
        }
        return out
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
