package io.embodit.trafficsignals.ui.scene

import android.content.Context
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.animation.AnimationPlayConfig
import com.pico.spatial.core.ecs.animation.AnimationPlaybackController
import com.pico.spatial.core.ecs.resource.AnimationResource
import io.embodit.trafficsignals.domain.Gesture
import io.embodit.trafficsignals.platform.TsLog
import org.json.JSONObject

/** Clip timing baked with the GLB (tools/officer/bake_officer.py -> assets/officer_clips.json). */
internal class OfficerClips(val duration: Map<String, Float>, val steps: Map<String, List<Float>>, val heightM: Float) {
    companion object {
        fun load(context: Context): OfficerClips {
            val o = JSONObject(context.assets.open("officer_clips.json").bufferedReader().use { it.readText() })
            val clips = o.getJSONObject("clips")
            val d = HashMap<String, Float>()
            val s = HashMap<String, List<Float>>()
            for (k in clips.keys()) {
                val c = clips.getJSONObject(k)
                d[k] = c.getDouble("duration").toFloat()
                val a = c.getJSONArray("steps")
                s[k] = (0 until a.length()).map { a.getDouble(it).toFloat() }
            }
            return OfficerClips(d, s, o.getJSONObject("calibration").getDouble("heightM").toFloat())
        }
    }
}

/**
 * The Tripo officer (design entities3d.officer.modelCandidates.chosen): the GLB is calibrated at bake time (feet centre
 * at the origin, face +Z, 1.75 m; design D25) and carries one clip per gesture plus "stand". This class only plays,
 * freezes and times clips; where he stands and which way he faces is the scene's job (the parent entity).
 */
internal class Officer(val root: Entity, private val clips: OfficerClips) {
    private var skinned: List<Entity> = emptyList()
    private val byName = HashMap<String, AnimationResource>()
    private var controllers: List<AnimationPlaybackController> = emptyList()
    var playing: String? = null
        private set
    private var startedAtMs = 0L
    private var speed = 1f
    var frozenAt: Float? = null
        private set

    val ready: Boolean
        get() = byName.isNotEmpty()

    fun bind() {
        skinned = root.findSkinnedMeshEntity().toList()
        val res = skinned.firstOrNull()?.getAnimationResources()?.toList() ?: emptyList()
        for (r in res) byName[runCatching { r.getName() }.getOrDefault("?")] = r
        TsLog.i("OFFICER skinned=${skinned.size} clips=${byName.keys.sorted()}")
    }

    private fun resolve(name: String): AnimationResource? = byName[name] ?: byName.entries.firstOrNull { it.key.startsWith(name) }?.value

    /** Plays [name] from the start; [fade] seconds of crossfade from the current pose. */
    fun play(name: String, nowMs: Long, speed: Float, fade: Float = 0.25f) {
        val r = resolve(name) ?: return
        this.speed = speed
        controllers =
            skinned.mapNotNull { e ->
                runCatching { e.playAnimation(r, AnimationPlayConfig(fade)).also { it.setSpeed(speed) } }.onFailure { TsLog.w("OFFICER play failed: $it") }.getOrNull()
            }
        playing = name
        startedAtMs = nowMs
        frozenAt = null
    }

    /** Holds the pose of [name] at time [t] (s): the step freeze of the lesson / breakdown. */
    fun freeze(name: String, t: Float, nowMs: Long) {
        if (playing != name || controllers.isEmpty()) play(name, nowMs, 1f, 0.15f)
        controllers.forEach {
            runCatching {
                it.setTime(t)
                it.pause()
            }
        }
        frozenAt = t
    }

    /** Seconds into the playing clip (wall clock × speed; the controller's own time is used when it answers). */
    fun time(nowMs: Long): Float {
        val c = controllers.firstOrNull()
        val t = c?.let { runCatching { it.getTime() }.getOrNull() }
        return t ?: ((nowMs - startedAtMs) / 1000f * speed)
    }

    fun duration(name: String): Float = clips.duration[name] ?: 1f

    fun steps(g: Gesture): List<Float> = clips.steps[g.id] ?: emptyList()

    fun done(nowMs: Long): Boolean {
        val name = playing ?: return true
        if (frozenAt != null) return false
        return (nowMs - startedAtMs) / 1000f * speed >= duration(name)
    }

    fun pause() = controllers.forEach { runCatching { it.pause() } }

    fun resume() = controllers.forEach { runCatching { it.resume() } }

    fun release() {
        controllers.forEach { runCatching { it.close() } }
        controllers = emptyList()
        skinned.forEach { runCatching { it.stopAllAnimations() } }
        byName.values.forEach { runCatching { it.close() } }
        byName.clear()
    }
}
