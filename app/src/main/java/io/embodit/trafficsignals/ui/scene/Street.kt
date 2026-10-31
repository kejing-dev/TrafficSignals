package io.embodit.trafficsignals.ui.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector2
import io.embodit.trafficsignals.domain.Compass
import io.embodit.trafficsignals.domain.Move
import io.embodit.trafficsignals.domain.Vec3
import java.util.Random
import kotlin.math.abs

/**
 * The crossroads (design scene.intersection), intersection space: centre at the origin, metres, you wait at the south
 * approach (+Z), +X east. Four identical approaches: 3 entry lanes (left / straight / right, 3 m) + 3 exit lanes, road
 * edge at 9 m, crosswalk 9 - 12 m, stop line at 12.5 m.
 */
internal object Street {
    const val HALF = 9f
    const val CROSS_IN = 9f
    const val CROSS_OUT = 12f
    const val STOP = 12.5f
    const val GROUND = 60f
    const val TEX = 2048
    const val POST_R = 0.6f
    const val POST_H = 0.40f

    /** Unit vector of an approach (from the centre out). */
    fun out(c: Compass): Vec3 = c.vec

    /** Right of a heading (driving on the right). */
    fun rightOf(h: Vec3): Vec3 = Vec3(-h.z, 0f, h.x)

    /** Centre of an entry lane (laneX 1.5 / 4.5 / 7.5) of approach [c] at distance [d] from the centre. */
    fun entry(c: Compass, laneX: Float, d: Float): Vec3 {
        val o = out(c)
        val heading = -o
        return o * d + rightOf(heading) * laneX
    }

    /** The compass an approach turns to for a move. */
    fun exitOf(c: Compass, m: Move): Compass {
        val heading = c.opposite() // driving from c towards the opposite side
        return when (m) {
            Move.STRAIGHT -> heading
            Move.LEFT, Move.WAIT -> heading.leftOf()
            Move.RIGHT -> heading.leftOf().opposite()
        }
    }

    /**
     * Path of a car of approach [c] in lane [laneX] making [m]: a quadratic Bézier from the stop line through the corner
     * point to 40 m out on the exit road (outer exit lane for right turns, inner for left).
     */
    fun path(c: Compass, laneX: Float, m: Move, fromD: Float = STOP): Bezier {
        val p0 = entry(c, laneX, fromD)
        val heading = -out(c)
        if (m == Move.STRAIGHT) {
            val p2 = p0 + heading * (fromD + 40f)
            return Bezier(p0, (p0 + p2) * 0.5f, p2)
        }
        val e = exitOf(c, m)
        val eo = out(e)
        val exitX = if (m == Move.RIGHT) 7.5f else 1.5f
        val p2 = eo * 40f + rightOf(eo) * exitX
        // corner: where the entry line (p0 + heading * t) meets the exit line (p2 - eo * s)
        val k = cornerPoint(p0, heading, p2)
        return Bezier(p0, k, p2)
    }

    private fun cornerPoint(p0: Vec3, h: Vec3, p2: Vec3): Vec3 =
        // the two lines are perpendicular: keep p0's cross coordinate and take p2's along the heading
        if (abs(h.z) > 0.5f) Vec3(p0.x, 0f, p2.z) else Vec3(p2.x, 0f, p0.z)

    data class Bezier(val p0: Vec3, val p1: Vec3, val p2: Vec3) {
        fun at(t: Float): Vec3 {
            val u = 1 - t
            return p0 * (u * u) + p1 * (2 * u * t) + p2 * (t * t)
        }

        fun tangent(t: Float): Vec3 = ((p1 - p0) * (2 * (1 - t)) + (p2 - p1) * (2 * t)).normalized()

        /** Parameter where the curve is closest to [p] (coarse search). */
        fun nearest(p: Vec3): Float {
            var best = 0f
            var bd = Float.MAX_VALUE
            for (i in 0..200) {
                val t = i / 200f
                val d = (at(t) - p).length
                if (d < bd) {
                    bd = d
                    best = t
                }
            }
            return best
        }
    }

    // =========================================================================================
    // ground texture
    // =========================================================================================

    /** Painted road (asphalt, markings, crosswalks, arrows, waiting boxes, corner pavements); v = 0 is north. */
    fun groundTexture(): Bitmap {
        val bmp = Bitmap.createBitmap(TEX, TEX, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = TEX / GROUND // px per metre
        c.translate(TEX / 2f, TEX / 2f)
        c.scale(s, s)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // pavement everywhere, then the two roads
        p.color = 0xFFB9B4A8.toInt()
        c.drawRect(-GROUND / 2, -GROUND / 2, GROUND / 2, GROUND / 2, p)
        p.color = 0xFF3A3F45.toInt()
        c.drawRect(-HALF, -GROUND / 2, HALF, GROUND / 2, p)
        c.drawRect(-GROUND / 2, -HALF, GROUND / 2, HALF, p)
        for (angle in floatArrayOf(0f, 90f, 180f, 270f)) {
            c.save()
            c.rotate(angle)
            approach(c)
            c.restore()
        }
        return bmp
    }

    /** One approach drawn as the south one (+Z = down on the canvas). Canvas.rotate(90) turns it to the west. */
    private fun approach(c: Canvas) {
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F2EE.toInt() }
        val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2C230.toInt() }
        val far = GROUND / 2
        // centre double yellow
        c.drawRect(-0.22f, STOP, -0.1f, far, yellow)
        c.drawRect(0.1f, STOP, 0.22f, far, yellow)
        // entry lane dividers (solid near the stop line), exit lane dividers dashed
        for (x in floatArrayOf(3f, 6f)) c.drawRect(x - 0.075f, STOP, x + 0.075f, far, white)
        var z = CROSS_OUT + 1f
        while (z < far) {
            for (x in floatArrayOf(-3f, -6f)) c.drawRect(x - 0.075f, z, x + 0.075f, z + 2f, white)
            z += 6f
        }
        // road edges
        c.drawRect(HALF - 0.3f, CROSS_OUT, HALF - 0.15f, far, white)
        c.drawRect(-HALF + 0.15f, CROSS_OUT, -HALF + 0.3f, far, white)
        // stop line on the entry side
        c.drawRect(0.25f, STOP, HALF - 0.3f, STOP + 0.3f, white)
        // crosswalk: stripes along the road
        var x = -HALF + 0.5f
        while (x < HALF - 0.4f) {
            c.drawRect(x, CROSS_IN + 0.2f, x + 0.45f, CROSS_OUT - 0.2f, white)
            x += 0.9f
        }
        // lane arrows 16 - 21 m (left, straight, right)
        arrow(c, 1.5f, 16f, Move.LEFT, white)
        arrow(c, 4.5f, 16f, Move.STRAIGHT, white)
        arrow(c, 7.5f, 16f, Move.RIGHT, white)
        arrow(c, 1.5f, 26f, Move.LEFT, white)
        arrow(c, 4.5f, 26f, Move.STRAIGHT, white)
        arrow(c, 7.5f, 26f, Move.RIGHT, white)
        // left-turn waiting box: dashed edges from the crosswalk into the junction, words at its mouth
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF2F2EE.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 0.15f
            pathEffect = DashPathEffect(floatArrayOf(1f, 1f), 0f)
        }
        val box = Path().apply {
            moveTo(0.25f, CROSS_IN - 0.3f)
            lineTo(0.25f, 4.6f)
            lineTo(2.9f, 3.8f)
            lineTo(2.9f, CROSS_IN - 0.3f)
        }
        c.drawPath(box, dash)
        c.drawRect(0.25f, 3.8f, 2.9f, 4.0f, white)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF2F2EE.toInt()
            textSize = 0.62f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        // read from the stop line looking into the junction (towards -Z): words upright for the approaching driver
        c.drawText("待转区", 1.57f, 7.6f, text)
    }

    /** Filled road arrow pointing to -Z (ahead of the approaching driver) at lane centre [x], tail at [z]. */
    private fun arrow(c: Canvas, x: Float, z: Float, m: Move, paint: Paint) {
        val p = Path()
        val w = 0.15f
        val tail = z + 4.5f
        val tip = z
        when (m) {
            Move.STRAIGHT, Move.WAIT -> {
                p.moveTo(x - w, tail); p.lineTo(x - w, tip + 1.3f); p.lineTo(x - 0.45f, tip + 1.3f); p.lineTo(x, tip)
                p.lineTo(x + 0.45f, tip + 1.3f); p.lineTo(x + w, tip + 1.3f); p.lineTo(x + w, tail); p.close()
            }
            Move.LEFT, Move.RIGHT -> {
                val s = if (m == Move.LEFT) -1f else 1f
                // shaft up to the bend, then a short arm sideways ending in a head
                val bend = tip + 1.6f
                p.moveTo(x - w, tail); p.lineTo(x - w, bend - w * s * 0f); p.lineTo(x + w, bend); p.lineTo(x + w, tail); p.close()
                c.drawPath(p, paint)
                val q = Path()
                val y0 = bend + 0.5f
                val armEnd = x + s * 0.75f
                q.moveTo(x - w * s, y0 - w); q.lineTo(armEnd, y0 - w); q.lineTo(armEnd, y0 - 0.45f); q.lineTo(armEnd + s * 0.6f, y0)
                q.lineTo(armEnd, y0 + 0.45f); q.lineTo(armEnd, y0 + w); q.lineTo(x - w * s, y0 + w); q.close()
                c.drawPath(q, paint)
                // the shaft joins the arm
                c.drawRect(x - w, bend - 0.2f, x + w, y0 + w, paint)
                return
            }
        }
        c.drawPath(p, paint)
    }

    /** Sky: vertical gradient, light horizon to blue zenith (texture v = 0 at the top). */
    fun skyTexture(): Bitmap {
        val h = 256
        val bmp = Bitmap.createBitmap(4, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF5E8FD0.toInt(), 0xFF8DB6E6.toInt(), 0xFFD7E6F2.toInt(), 0xFFC9D3DC.toInt()), floatArrayOf(0f, 0.45f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 4f, h.toFloat(), p)
        return bmp
    }

    /** The painted ground plane (60 × 60 m, y = 0), UV u = east, v = south. */
    fun groundMesh(): MeshResource {
        val b = MeshBuilder(4)
        val n = Vec3.UP
        val h = GROUND / 2
        b.quad(
            b.vertex(Vec3(-h, 0f, h), n, Vector2(0f, 1f)),
            b.vertex(Vec3(h, 0f, h), n, Vector2(1f, 1f)),
            b.vertex(Vec3(h, 0f, -h), n, Vector2(1f, 0f)),
            b.vertex(Vec3(-h, 0f, -h), n, Vector2(0f, 0f)),
        )
        return b.build("ground")
    }

    /** A big flat ring of verge/fields around the painted square, slightly lower (no z-fighting). */
    fun outerGround(t: Toon) {
        val r = 400f
        val h = GROUND / 2
        val y = -0.02f
        t.quad(Palette.GRASS, Vec3(-r, y, r), Vec3(r, y, r), Vec3(r, y, h), Vec3(-r, y, h), 0)
        t.quad(Palette.GRASS, Vec3(-r, y, -h), Vec3(r, y, -h), Vec3(r, y, -r), Vec3(-r, y, -r), 0)
        t.quad(Palette.GRASS, Vec3(-r, y, h), Vec3(-h, y, h), Vec3(-h, y, -h), Vec3(-r, y, -h), 0)
        t.quad(Palette.GRASS, Vec3(h, y, h), Vec3(r, y, h), Vec3(r, y, -h), Vec3(h, y, -h), 0)
        // the four roads continue as asphalt strips
        for (c in Compass.entries) {
            val o = c.vec
            val side = rightOf(o)
            val a = o * h
            val bb = o * r
            val p0 = a + side * HALF + Vec3(0f, 0.005f, 0f)
            val p1 = a - side * HALF + Vec3(0f, 0.005f, 0f)
            val p2 = bb - side * HALF + Vec3(0f, 0.005f, 0f)
            val p3 = bb + side * HALF + Vec3(0f, 0.005f, 0f)
            t.quad(Palette.ASPHALT, p0, p1, p2, p3, 0)
            t.quad(Palette.ASPHALT, p3, p2, p1, p0, 0)
        }
    }

    /** Corner pavements (raised 0.15 m), the centre post, the two signal poles, trees and the low skyline. */
    fun props(t: Toon) {
        val h = GROUND / 2
        val curb = 0.15f
        for (sx in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f)) {
            val x0 = if (sx > 0) HALF else -h
            val x1 = if (sx > 0) h else -HALF
            val z0 = if (sz > 0) HALF else -h
            val z1 = if (sz > 0) h else -HALF
            t.box(Palette.CURB, Vec3(x0, 0f, z0), Vec3(x1, curb, z1), top = Palette.SIDEWALK)
        }
        // centre post Ø 1.2 × 0.40, white with two blue bands
        t.cylinder(Palette.POST_WHITE, 0f, 0f, POST_R, 0f, 0.13f, 28)
        t.cylinder(Palette.POST_BLUE, 0f, 0f, POST_R + 0.004f, 0.13f, 0.19f, 28, top = Palette.POST_BLUE)
        t.cylinder(Palette.POST_WHITE, 0f, 0f, POST_R, 0.19f, 0.26f, 28)
        t.cylinder(Palette.POST_BLUE, 0f, 0f, POST_R + 0.004f, 0.26f, 0.32f, 28, top = Palette.POST_BLUE)
        t.cylinder(Palette.POST_WHITE, 0f, 0f, POST_R, 0.32f, POST_H, 28)
        // signal poles only at the far corners (north-east, north-west), lamps off
        for (sx in floatArrayOf(-1f, 1f)) {
            val px = sx * (HALF + 1.2f)
            val pz = -(HALF + 1.2f)
            t.cylinder(Palette.POLE, px, pz, 0.11f, curb, 5.6f, 10)
            // arm over the road towards the centre line and a dark signal head facing the junction
            val armEnd = sx * 2.5f
            t.box(Palette.POLE, Vec3(minOf(px, armEnd), 5.45f, pz - 0.08f), Vec3(maxOf(px, armEnd), 5.6f, pz + 0.08f))
            val hx = sx * 4.5f
            t.box(Palette.LAMP_OFF, Vec3(hx - 0.55f, 4.95f, pz - 0.2f), Vec3(hx + 0.55f, 5.45f, pz + 0.12f))
            for (i in 0 until 3) {
                val cx = hx - 0.33f + i * 0.33f
                t.box(Palette.POLE, Vec3(cx - 0.12f, 5.08f, pz + 0.12f), Vec3(cx + 0.12f, 5.32f, pz + 0.15f))
            }
        }
        // trees along the pavements
        val rnd = Random(11)
        for (c in Compass.entries) {
            val o = c.vec
            val side = rightOf(o)
            var d = 14f
            while (d < 29f) {
                for (s in floatArrayOf(-1f, 1f)) {
                    val p = o * d + side * (s * (HALF + 1.6f))
                    val hgt = 4.2f + rnd.nextFloat() * 1.4f
                    t.cylinder(Palette.TRUNK, p.x, p.z, 0.12f, curb, curb + 1.6f, 8)
                    t.cone(if (rnd.nextBoolean()) Palette.LEAF else Palette.LEAF_DARK, p.x, p.z, 1.3f + rnd.nextFloat() * 0.3f, curb + 1.3f, curb + hgt, 10)
                }
                d += 7f
            }
        }
        // low skyline: blocks in the four corners beyond the pavements
        val colors = intArrayOf(Palette.BUILDING_A, Palette.BUILDING_B, Palette.BUILDING_C)
        for (sx in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f)) {
            var x = HALF + 6f
            while (x < 70f) {
                val w = 7f + rnd.nextFloat() * 8f
                val dz = HALF + 6f + rnd.nextFloat() * 10f
                val dd = 8f + rnd.nextFloat() * 10f
                val hh = 8f + rnd.nextFloat() * 22f
                val col = colors[rnd.nextInt(colors.size)]
                val xa = sx * x
                val xb = sx * (x + w)
                t.box(col, Vec3(minOf(xa, xb), 0f, minOf(sz * dz, sz * (dz + dd))), Vec3(maxOf(xa, xb), hh, maxOf(sz * dz, sz * (dz + dd))))
                // the same block mirrored along the other road
                t.box(col, Vec3(minOf(sz * dz, sz * (dz + dd)), 0f, minOf(xa, xb)), Vec3(maxOf(sz * dz, sz * (dz + dd)), hh * 0.8f, maxOf(xa, xb)))
                x += w + 3f + rnd.nextFloat() * 4f
            }
        }
    }

    /** A saloon 4.6 × 1.8 × 1.45 m, nose towards -Z, origin on the road under its centre. */
    fun car(t: Toon, color: Int) {
        val w = 0.9f
        t.box(color, Vec3(-w, 0.28f, -2.3f), Vec3(w, 0.82f, 2.3f))
        // cabin, narrower, glass all round
        t.box(Palette.CAR_GLASS, Vec3(-0.78f, 0.82f, -0.95f), Vec3(0.78f, 1.40f, 1.15f), top = color)
        t.box(color, Vec3(-0.8f, 1.40f, -0.85f), Vec3(0.8f, 1.45f, 1.05f))
        for (sx in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1.45f, 1.45f)) {
            t.box(Palette.TYRE, Vec3(sx * w - 0.12f, 0f, sz - 0.33f), Vec3(sx * w + 0.12f, 0.6f, sz + 0.33f))
        }
        // lamps
        t.box(Palette.LIGHT_GREY, Vec3(-0.8f, 0.6f, -2.32f), Vec3(-0.45f, 0.72f, -2.3f))
        t.box(Palette.LIGHT_GREY, Vec3(0.45f, 0.6f, -2.32f), Vec3(0.8f, 0.72f, -2.3f))
    }

    /**
     * Your own car's bonnet (design scene.intersection.hood): 1.1 - 1.9 m ahead, 0.45 m below the eye at its rear edge,
     * sloping down to the nose. It must stay BEHIND the 0.95 m panels (their lower edge is 0.49 m below the eye), so the
     * bonnet can never cover a panel (profile D20), 1.7 m wide with rounded shoulders and a front lip, so it reads
     * as the car you sit in rather than a board in the air. Driver frame, x right of the eye.
     */
    fun hood(t: Toon, color: Int, eyeH: Float) {
        val cx = 0.4f
        val nx = 10
        val nz = 6
        val z0 = -1.1f
        val z1 = -1.95f
        fun pt(i: Int, j: Int): Vec3 {
            val u = i / nx.toFloat() * 2 - 1
            val v = j / nz.toFloat()
            val y = eyeH - 0.45f - 0.08f * v - 0.08f * u * u * u * u - 0.03f * v * v
            return Vec3(cx + u * 0.85f, y, z0 + (z1 - z0) * v)
        }
        for (i in 0 until nx) for (j in 0 until nz) t.quad(color, pt(i, j), pt(i + 1, j), pt(i + 1, j + 1), pt(i, j + 1), 0)
        // nose and the two flanks down to the wheel-arch line
        for (i in 0 until nx) {
            val a = pt(i, nz)
            val b = pt(i + 1, nz)
            t.quad(color, Vec3(a.x, a.y - 0.25f, a.z), Vec3(b.x, b.y - 0.25f, b.z), b, a, 2)
        }
        for (side in intArrayOf(0, nx)) for (j in 0 until nz) {
            val a = pt(side, j)
            val b = pt(side, j + 1)
            t.quad(color, Vec3(a.x, a.y - 0.25f, a.z), Vec3(b.x, b.y - 0.25f, b.z), b, a, 2)
        }
    }

    /** The small 0.40 m lesson post (design lessonSpot) as its own mesh. */
    fun lessonPost(t: Toon) {
        t.cylinder(Palette.POST_WHITE, 0f, 0f, 0.5f, 0f, 0.15f, 24)
        t.cylinder(Palette.POST_BLUE, 0f, 0f, 0.504f, 0.15f, 0.22f, 24, top = Palette.POST_BLUE)
        t.cylinder(Palette.POST_WHITE, 0f, 0f, 0.5f, 0.22f, POST_H, 24)
    }

    /** A flat ribbon along [path] from [t0] to [t1], [w] wide, [lift] above the road, optional head. */
    fun ribbon(b: MeshBuilder, path: Bezier, t0: Float, t1: Float, w: Float, lift: Float, head: Boolean, seg: Int = 24, uv: Vector2 = MeshBuilder.ZERO_UV) {
        val n = Vec3.UP
        var prevL = -1
        var prevR = -1
        val headLen = if (head) 1.4f else 0f
        val total = arcLength(path, t0, t1)
        val tEnd = if (head && total > headLen * 1.5f) t0 + (t1 - t0) * (1 - headLen / total) else t1
        for (i in 0..seg) {
            val t = t0 + (tEnd - t0) * i / seg
            val p = path.at(t)
            val r = rightOf(path.tangent(t)) * (w / 2)
            val l = b.vertex(Vec3(p.x - r.x, lift, p.z - r.z), n, uv)
            val rr = b.vertex(Vec3(p.x + r.x, lift, p.z + r.z), n, uv)
            if (prevL >= 0) {
                b.tri(prevL, rr, prevR)
                b.tri(prevL, l, rr)
            }
            prevL = l
            prevR = rr
        }
        if (head && tEnd < t1) {
            val base = path.at(tEnd)
            val tip = path.at(t1)
            val r = rightOf(path.tangent(tEnd)) * w
            val a = b.vertex(Vec3(base.x - r.x, lift, base.z - r.z), n, uv)
            val c = b.vertex(Vec3(base.x + r.x, lift, base.z + r.z), n, uv)
            val d = b.vertex(Vec3(tip.x, lift, tip.z), n, uv)
            b.tri(a, d, c)
            b.tri(a, c, d)
        }
    }

    fun arcLength(path: Bezier, t0: Float, t1: Float): Float {
        var len = 0f
        var prev = path.at(t0)
        for (i in 1..20) {
            val p = path.at(t0 + (t1 - t0) * i / 20)
            len += (p - prev).length
            prev = p
        }
        return len
    }

    /** A flat bar across an approach's entry lanes at the stop line (red "stop" bar). */
    fun stopBar(b: MeshBuilder, c: Compass, lift: Float) {
        val n = Vec3.UP
        val a = entry(c, 0.3f, STOP - 0.05f)
        val bb = entry(c, HALF - 0.3f, STOP - 0.05f)
        val o = out(c) * 0.45f
        val v0 = b.vertex(Vec3(a.x, lift, a.z), n)
        val v1 = b.vertex(Vec3(bb.x, lift, bb.z), n)
        val v2 = b.vertex(Vec3(bb.x - o.x, lift, bb.z - o.z), n)
        val v3 = b.vertex(Vec3(a.x - o.x, lift, a.z - o.z), n)
        b.quad(v0, v1, v2, v3)
        b.quad(v3, v2, v1, v0)
    }

    /** Three chevrons across a lane (slow down), centred at distance [d] of the south approach. */
    fun chevrons(b: MeshBuilder, laneX: Float, d0: Float, lift: Float) {
        val n = Vec3.UP
        for (k in 0 until 3) {
            val z = d0 - k * 1.3f
            val pts = listOf(Vec3(laneX - 1.1f, lift, z + 0.5f), Vec3(laneX, lift, z - 0.3f), Vec3(laneX + 1.1f, lift, z + 0.5f))
            for (i in 0 until 2) {
                val a = pts[i]
                val c = pts[i + 1]
                val v0 = b.vertex(a, n)
                val v1 = b.vertex(c, n)
                val v2 = b.vertex(c + Vec3(0f, 0f, 0.35f), n)
                val v3 = b.vertex(a + Vec3(0f, 0f, 0.35f), n)
                b.quad(v0, v1, v2, v3)
                b.quad(v3, v2, v1, v0)
            }
        }
    }
}
