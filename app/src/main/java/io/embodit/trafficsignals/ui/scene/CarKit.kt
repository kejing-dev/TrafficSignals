package io.embodit.trafficsignals.ui.scene

import io.embodit.trafficsignals.domain.StageLayout
import io.embodit.trafficsignals.domain.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * The cars as lofted, smooth-shaded meshes (QA q3-5, q3-6; design entities3d: rounded body, cabin, four wheels), lit by the
 * same IBL + sun as the officer ([TrafficScene] `Mats.paint`). A body is a stack of rounded cross-sections along z, closed at
 * both ends, so no angle shows a cut edge. No box helper here (ui_gates box-helper: buildings and road only).
 */
internal object CarKit {
    /** A cross-section at [z]: an arch from the floor on one side, over the top, to the floor on the other. */
    private fun arch(cx: Float, halfW: Float, yBottom: Float, yTop: Float, z: Float, e: Float = 3f, n: Int = 14): List<Vec3> =
        (0..n).map { i ->
            val a = PI * i / n
            val c = cos(a)
            val s = sin(a)
            val x = cx + halfW * (if (c >= 0) 1f else -1f) * abs(c).pow(2.0 / e).toFloat()
            val y = yBottom + (yTop - yBottom) * abs(s).pow(2.0 / e).toFloat()
            Vec3(x, y, z)
        }

    /** Smooth surface through [rings] (same point count), both ends capped, floor closed. Normals point away from the axis. */
    private fun loft(b: MeshBuilder, color: Int, rings: List<List<Vec3>>) {
        val uv = Palette.uv(color, 0)
        val nr = rings.size
        val np = rings[0].size
        val axis = rings.map { r -> r.reduce { acc, v -> acc + v } * (1f / np) }
        val base = b.vertexCount
        for (j in 0 until nr) for (i in 0 until np) {
            val p = rings[j][i]
            val du = rings[j][minOf(i + 1, np - 1)] - rings[j][maxOf(i - 1, 0)]
            val dv = rings[minOf(j + 1, nr - 1)][i] - rings[maxOf(j - 1, 0)][i]
            var n = du.cross(dv)
            if (n.length < 1e-6f) n = p - axis[j]
            n = n.normalized()
            if (n.dot(p - axis[j]) < 0f) n = n * -1f
            b.vertex(p, n, uv)
        }
        for (j in 0 until nr - 1) for (i in 0 until np - 1) {
            val a = base + j * np + i
            b.quad(a, a + 1, a + np + 1, a + np)
        }
        // floor between the two feet of each section, then the end caps
        for (j in 0 until nr - 1) {
            val a = base + j * np
            b.quad(a, a + np - 1, a + np + np - 1, a + np)
        }
        fun cap(j: Int, nz: Float) {
            val c = axis[j]
            val cv = b.vertex(c, Vec3(0f, 0f, nz), uv)
            val first = b.vertexCount
            for (p in rings[j]) b.vertex(p, Vec3(0f, 0f, nz), uv)
            for (i in 0 until np - 1) b.tri(cv, first + i, first + i + 1)
            b.tri(cv, first + np - 1, first)
        }
        cap(0, -1f)
        cap(nr - 1, 1f)
    }

    /** A flat disc facing [n] (one of the axes) at [c]. */
    private fun disc(b: MeshBuilder, color: Int, c: Vec3, r: Float, n: Vec3, ry: Float = r, seg: Int = 18) {
        val uv = Palette.uv(color, 0)
        val u = if (abs(n.x) > 0.5f) Vec3(0f, 0f, 1f) else Vec3(1f, 0f, 0f)
        val v = Vec3(0f, 1f, 0f)
        val cv = b.vertex(c, n, uv)
        val first = b.vertexCount
        for (i in 0..seg) {
            val a = 2 * PI * i / seg
            b.vertex(c + u * (r * cos(a).toFloat()) + v * (ry * sin(a).toFloat()), n, uv)
        }
        for (i in 0 until seg) b.tri(cv, first + i, first + i + 1)
    }

    /** A wheel, axis along X: tyre tread with round shoulders, a hub on the outer face. [side] -1 left, 1 right. */
    private fun wheel(b: MeshBuilder, x: Float, z: Float, r: Float, w: Float, side: Float) {
        val seg = 22
        val tyre = Palette.uv(Palette.TYRE, 0)
        val base = b.vertexCount
        val xs = floatArrayOf(-w / 2, -w / 2 + 0.03f, w / 2 - 0.03f, w / 2)
        val rs = floatArrayOf(r - 0.035f, r, r, r - 0.035f)
        for (k in xs.indices) for (i in 0..seg) {
            val a = 2 * PI * i / seg
            val cy = cos(a).toFloat()
            val sz = sin(a).toFloat()
            val n = Vec3(if (k == 0) -0.5f else if (k == 3) 0.5f else 0f, cy, sz).normalized()
            b.vertex(Vec3(x + xs[k], r + rs[k] * cy, z + rs[k] * sz), n, tyre)
        }
        val m = seg + 1
        for (k in 0 until xs.size - 1) for (i in 0 until seg) {
            val a = base + k * m + i
            b.quad(a, a + 1, a + m + 1, a + m)
        }
        val out = x + side * w / 2
        val n = Vec3(side, 0f, 0f)
        disc(b, Palette.TYRE, Vec3(out, r, z), r - 0.03f, n)
        disc(b, Palette.LIGHT_GREY, Vec3(out + side * 0.004f, r, z), r * 0.56f, n)
        disc(b, Palette.POLE, Vec3(out + side * 0.008f, r, z), r * 0.2f, n, seg = 10)
    }

    private fun interp(table: Array<FloatArray>, z: Float): Float {
        for (i in 0 until table.size - 1) {
            val a = table[i]
            val c = table[i + 1]
            if (z <= c[0]) {
                val t = ((z - a[0]) / max(1e-5f, c[0] - a[0])).coerceIn(0f, 1f)
                val s = t * t * (3 - 2 * t)
                return a[1] + (c[1] - a[1]) * s
            }
        }
        return table.last()[1]
    }

    /** A saloon 4.6 × 1.8 × 1.45 m, nose towards -Z, origin on the road under its centre. */
    fun car(b: MeshBuilder, color: Int) {
        val zs = floatArrayOf(-2.3f, -2.27f, -2.2f, -2.05f, -1.8f, -1.4f, -0.9f, -0.2f, 0.6f, 1.3f, 1.8f, 2.1f, 2.24f, 2.3f)
        val top = arrayOf(floatArrayOf(-2.3f, 0.5f), floatArrayOf(-2.2f, 0.66f), floatArrayOf(-1.9f, 0.78f), floatArrayOf(-0.95f, 0.88f), floatArrayOf(0f, 0.9f), floatArrayOf(1.3f, 0.92f), floatArrayOf(2.1f, 0.88f), floatArrayOf(2.3f, 0.62f))
        val halfW = arrayOf(floatArrayOf(-2.3f, 0.62f), floatArrayOf(-2.1f, 0.82f), floatArrayOf(-1.5f, 0.9f), floatArrayOf(1.6f, 0.9f), floatArrayOf(2.15f, 0.82f), floatArrayOf(2.3f, 0.64f))
        // wheels first: the arches are dark discs on the flanks, the tyres stand in them
        loft(b, color, zs.map { z -> arch(0f, interp(halfW, z), 0.24f, interp(top, z), z, e = 3.2f) })
        // the glasshouse, then the roof over it
        val cabinZ = floatArrayOf(-1.15f, -0.85f, -0.45f, 0f, 0.6f, 1.0f, 1.35f, 1.7f)
        val cabinTop = arrayOf(floatArrayOf(-1.15f, 0.87f), floatArrayOf(-0.35f, 1.38f), floatArrayOf(0.4f, 1.44f), floatArrayOf(1.0f, 1.38f), floatArrayOf(1.7f, 0.92f))
        loft(b, Palette.CAR_GLASS, cabinZ.map { z -> arch(0f, 0.76f - 0.05f * interp(cabinTop, z) / 1.44f, 0.84f, interp(cabinTop, z), z, e = 3.6f, n = 12) })
        val roofZ = floatArrayOf(-0.42f, -0.3f, 0.1f, 0.6f, 0.95f, 1.08f)
        loft(b, color, roofZ.map { z ->
            val t = interp(cabinTop, z) + 0.012f
            arch(0f, 0.62f, t - 0.05f, t, z, e = 3.6f, n = 10)
        })
        for (side in floatArrayOf(-1f, 1f)) for (z in floatArrayOf(-1.42f, 1.42f)) {
            disc(b, Palette.TYRE, Vec3(side * 0.905f, 0.31f, z), 0.38f, Vec3(side, 0f, 0f))
            wheel(b, side * 0.81f, z, 0.31f, 0.24f, side)
        }
        for (side in floatArrayOf(-1f, 1f)) {
            disc(b, Palette.LIGHT_GREY, Vec3(side * 0.47f, 0.54f, -2.292f), 0.14f, Vec3(0f, 0f, -1f), ry = 0.06f, seg = 12)
            disc(b, Palette.CAR_RED, Vec3(side * 0.5f, 0.68f, 2.292f), 0.15f, Vec3(0f, 0f, 1f), ry = 0.05f, seg = 12)
        }
        groundContact(b, 0f, 0.96f, -2.28f, 2.28f)
    }

    /** A dark patch under the car: ties it to the road (the street is Unlit, it receives no shadow). */
    private fun groundContact(b: MeshBuilder, cx: Float, halfW: Float, z0: Float, z1: Float) {
        val uv = Palette.uv(Palette.TYRE, 0)
        val n = Vec3.UP
        b.quad(
            b.vertex(Vec3(cx - halfW, 0.015f, z1), n, uv),
            b.vertex(Vec3(cx + halfW, 0.015f, z1), n, uv),
            b.vertex(Vec3(cx + halfW, 0.015f, z0), n, uv),
            b.vertex(Vec3(cx - halfW, 0.015f, z0), n, uv),
        )
    }

    /**
     * The car you sit in, driver frame (design scene.intersection.hood, QA q3-5): one closed shell from the nose to behind
     * your seat, so turning your head never shows a cut edge: rounded bonnet, fenders flared over the front wheels, skirts
     * down to the sills, door tops level with [StageLayout.HOOD_DROP], a mirror on each door. The cockpit is [ownCockpit].
     */
    fun ownCar(b: MeshBuilder, color: Int) {
        val eye = StageLayout.EYE_H
        val cx = StageLayout.HOOD_CX
        val w = StageLayout.HOOD_HALF_W
        val nose = StageLayout.HOOD_NOSE_Z
        val zs = floatArrayOf(nose - 0.09f, nose - 0.07f, nose - 0.03f, nose, -1.42f, -1.3f, -1.15f, -1.0f, -0.85f, -0.7f, -0.4f, 0.3f, 0.9f, 1.3f, 1.5f, 1.56f)
        val halfW = arrayOf(floatArrayOf(nose - 0.09f, 0.6f), floatArrayOf(nose, 0.76f), floatArrayOf(-1.3f, 0.88f), floatArrayOf(-1.02f, w + 0.03f), floatArrayOf(-0.7f, w - 0.02f), floatArrayOf(1.3f, w - 0.02f), floatArrayOf(1.56f, 0.7f))
        loft(b, color, zs.map { z ->
            val top =
                when {
                    z < nose -> eye - StageLayout.HOOD_NOSE_DROP - (nose - z) / 0.09f * 0.2f
                    z > 1.3f -> eye - StageLayout.HOOD_DROP - (z - 1.3f) / 0.26f * 0.2f
                    else -> eye - StageLayout.hoodDrop(z)
                }
            arch(cx, interp(halfW, z), 0.2f, top, z, e = 5f, n = 18)
        })
        // tucked under the fenders: seen only if you lean out, as on a real car
        for (side in floatArrayOf(-1f, 1f)) for (z in floatArrayOf(-1.02f, 1.1f)) wheel(b, cx + side * 0.8f, z, 0.26f, 0.2f, side)
        // door mirrors: the first thing that says "my car" when you turn your head
        for (side in floatArrayOf(-1f, 1f)) {
            val x = cx + side * (w + 0.075f)
            val y = eye - StageLayout.HOOD_DROP + 0.07f
            val rings = floatArrayOf(-0.56f, -0.55f, -0.525f, -0.495f, -0.48f).map { z ->
                val k = 1f - 0.45f * ((z + 0.52f) / 0.04f).let { it * it }
                arch(x, 0.07f * k, y - 0.04f * k, y + 0.04f * k, z, e = 2.4f, n = 10)
            }
            loft(b, color, rings)
            // the stalk down to the door
            val arm = floatArrayOf(-0.54f, -0.5f).map { z -> arch(cx + side * (w + 0.0f), 0.08f, y - 0.1f, y - 0.03f, z, e = 3f, n = 8) }
            loft(b, color, arm)
            disc(b, Palette.CAR_GLASS, Vec3(x, y, -0.477f), 0.058f, Vec3(0f, 0f, 1f), ry = 0.032f, seg = 14)
        }
    }

    /** The dark well around your seat and the dash at its front; Unlit, so the sky never washes it grey. */
    fun ownCockpit(b: MeshBuilder) {
        val cx = StageLayout.HOOD_CX
        val deck = StageLayout.EYE_H - StageLayout.HOOD_DROP + 0.006f
        val uv = Palette.uv(Palette.CAR_BLACK, 0)
        val n = Vec3.UP
        val seg = 44
        val c = b.vertex(Vec3(cx, deck, 0.3f), n, uv)
        val first = b.vertexCount
        for (i in 0..seg) {
            val a = 2 * PI * i / seg
            val ca = cos(a)
            val sa = sin(a)
            val x = 0.78 * (if (ca >= 0) 1 else -1) * abs(ca).pow(0.4)
            val z = 0.88 * (if (sa >= 0) 1 else -1) * abs(sa).pow(0.4)
            b.vertex(Vec3(cx + x.toFloat(), deck, 0.3f + z.toFloat()), n, uv)
        }
        for (i in 0 until seg) b.tri(c, first + i, first + i + 1)
        // the dash: a low dark hump along the front of the well
        loft(b, Palette.CAR_BLACK, floatArrayOf(-0.58f, -0.54f, -0.46f, -0.38f, -0.34f).map { z ->
            val k = 1f - ((z + 0.46f) / 0.12f).let { it * it }
            arch(cx, 0.74f, deck - 0.01f, deck + 0.075f * k, z, e = 4f, n = 12)
        })
    }

}
