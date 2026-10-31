package io.embodit.trafficsignals.ui.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.pico.spatial.core.math.Vector2
import io.embodit.trafficsignals.domain.Quat4
import io.embodit.trafficsignals.domain.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Flat colours of the procedural props (design palette.brandTokens + the street). Every colour is a block of three shades
 * in one small atlas (top / sunny side / shady side), so the whole street is drawn with one Unlit material and a sun from
 * the south-west (design scene.lighting) without any light computation.
 */
internal object Palette {
    // design brand tokens
    const val ASPHALT = 0
    const val LANE_WHITE = 1
    const val CENTER_YELLOW = 2
    const val SIDEWALK = 3
    const val CURB = 4
    const val POST_WHITE = 5
    const val POST_BLUE = 6
    const val POLE = 7
    const val LAMP_OFF = 8
    const val TRUNK = 9
    const val LEAF = 10
    const val LEAF_DARK = 11
    const val BUILDING_A = 12
    const val BUILDING_B = 13
    const val BUILDING_C = 14
    const val WINDOW = 15
    const val GRASS = 16
    const val TYRE = 17
    const val CAR_GLASS = 18
    const val CAR_WHITE = 19
    const val CAR_SILVER = 20
    const val CAR_NAVY = 21
    const val CAR_RED = 22
    const val CAR_BLACK = 23
    const val CAR_SLATE = 24
    const val LIGHT_GREY = 25

    val CARS = intArrayOf(CAR_WHITE, CAR_SILVER, CAR_NAVY, CAR_RED, CAR_BLACK, CAR_SLATE)
    val HOODS = intArrayOf(CAR_NAVY)

    private val COLORS =
        intArrayOf(
            0xFF3A3F45.toInt(), // asphalt (design brandTokens.asphalt)
            0xFFF2F2EE.toInt(), // laneWhite
            0xFFF2C230.toInt(), // centerYellow
            0xFFB9B4A8.toInt(), // sidewalk
            0xFFD6D2C6.toInt(), // curb
            0xFFF4F5F2.toInt(), // post white
            0xFF2F64B5.toInt(), // post stripes blue
            0xFF4A4F57.toInt(), // signal pole
            0xFF22262B.toInt(), // signal head, lamps off
            0xFF6B4E34.toInt(), // trunk
            0xFF5E9A4E.toInt(), // leaf
            0xFF467A3A.toInt(), // leaf dark
            0xFF8E9BB0.toInt(), // building grey-blue
            0xFFA7B2C4.toInt(), // building lighter
            0xFF738199.toInt(), // building darker
            0xFF55657E.toInt(), // window band
            0xFF7C9A63.toInt(), // verge grass
            0xFF1E1F22.toInt(), // tyre
            0xFF26313D.toInt(), // car glass
            0xFFEDEFF1.toInt(), // white car
            0xFFB8BEC6.toInt(), // silver
            0xFF22345C.toInt(), // dark blue
            0xFFB6312C.toInt(), // red
            0xFF26282C.toInt(), // black
            0xFF8FA3B8.toInt(), // light grey-blue
            0xFFCDD3DA.toInt(), // light grey
        )

    const val COLS = 8
    private const val BLOCK = 16

    val rows: Int
        get() = (COLORS.size + COLS - 1) / COLS

    /** Shade factors: top, sunny side, shady side. */
    private val SHADES = floatArrayOf(1.0f, 0.86f, 0.66f)

    fun atlas(): Bitmap {
        val w = COLS * 3 * BLOCK
        val h = rows * BLOCK
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        for (i in COLORS.indices) {
            val col = i % COLS
            val row = i / COLS
            for (s in 0 until 3) {
                val k = SHADES[s]
                val argb = COLORS[i]
                val r = (((argb shr 16) and 255) * k).toInt().coerceIn(0, 255)
                val g = (((argb shr 8) and 255) * k).toInt().coerceIn(0, 255)
                val b = ((argb and 255) * k).toInt().coerceIn(0, 255)
                p.color = android.graphics.Color.argb(255, r, g, b)
                val x0 = (col * 3 + s) * BLOCK
                val y0 = row * BLOCK
                c.drawRect(x0.toFloat(), y0.toFloat(), (x0 + BLOCK).toFloat(), (y0 + BLOCK).toFloat(), p)
            }
        }
        return bmp
    }

    /** UV of a colour block: v = 0 is the top row as drawn (the bitmap is flipped once on upload, see flipV). */
    fun uv(color: Int, shade: Int): Vector2 {
        val col = color % COLS
        val row = color / COLS
        val u = ((col * 3 + shade) + 0.5f) / (COLS * 3)
        val v = (row + 0.5f) / rows
        return Vector2(u, v)
    }

    /** Toward the sun (south-west, 45° up): the side shade of a face. */
    val SUN = Vec3(-0.5f, 0.7071f, 0.5f).normalized()

    fun shadeOf(n: Vec3): Int {
        if (n.y > 0.6f) return 0
        val flat = Vec3(n.x, 0f, n.z)
        return if (flat.dot(Vec3(SUN.x, 0f, SUN.z)) > -0.05f) 1 else 2
    }
}

/** Builds faceted props into one mesh, each face coloured from the atlas by its normal. */
internal class Toon(capacity: Int = 1024) {
    val b = MeshBuilder(capacity)
    private var rot = Quat4.IDENTITY
    private var off = Vec3.ZERO
    private var k = Vec3(1f, 1f, 1f)

    fun at(rotation: Quat4, offset: Vec3, scale: Vec3 = Vec3(1f, 1f, 1f), block: Toon.() -> Unit) {
        val r0 = rot
        val o0 = off
        val k0 = k
        rot = rotation
        off = offset
        k = scale
        block()
        rot = r0
        off = o0
        k = k0
    }

    private fun p(v: Vec3): Vec3 = off + rot.rotate(Vec3(v.x * k.x, v.y * k.y, v.z * k.z))

    private fun n(v: Vec3): Vec3 = rot.rotate(v).normalized()

    /** One flat quad a, b, c, d (counter-clockwise seen from the front). */
    fun quad(color: Int, a: Vec3, bb: Vec3, c: Vec3, d: Vec3, shade: Int? = null) {
        val pa = p(a)
        val pb = p(bb)
        val pc = p(c)
        val pd = p(d)
        val nrm = (pb - pa).cross(pc - pa).normalized()
        val uv = Palette.uv(color, shade ?: Palette.shadeOf(nrm))
        b.quad(b.vertex(pa, nrm, uv), b.vertex(pb, nrm, uv), b.vertex(pc, nrm, uv), b.vertex(pd, nrm, uv))
    }

    fun tri(color: Int, a: Vec3, bb: Vec3, c: Vec3) {
        val pa = p(a)
        val pb = p(bb)
        val pc = p(c)
        val nrm = (pb - pa).cross(pc - pa).normalized()
        val uv = Palette.uv(color, Palette.shadeOf(nrm))
        b.tri(b.vertex(pa, nrm, uv), b.vertex(pb, nrm, uv), b.vertex(pc, nrm, uv))
    }

    /** Axis-aligned box from [min] to [max] (local), all six faces. */
    fun box(color: Int, min: Vec3, max: Vec3, top: Int = color, bottom: Boolean = false) {
        val (x0, y0, z0) = Triple(min.x, min.y, min.z)
        val (x1, y1, z1) = Triple(max.x, max.y, max.z)
        quad(top, Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), Vec3(x0, y1, z0)) // top
        if (bottom) quad(color, Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x0, y0, z1))
        quad(color, Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1)) // +z
        quad(color, Vec3(x1, y0, z0), Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x1, y1, z0)) // -z
        quad(color, Vec3(x1, y0, z1), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1)) // +x
        quad(color, Vec3(x0, y0, z0), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0)) // -x
    }

    /** Vertical cylinder centred on (cx, cz) from y0 to y1. */
    fun cylinder(color: Int, cx: Float, cz: Float, r: Float, y0: Float, y1: Float, seg: Int = 16, top: Int = color) {
        for (i in 0 until seg) {
            val a0 = 2 * PI * i / seg
            val a1 = 2 * PI * (i + 1) / seg
            val p0 = Vec3(cx + (r * cos(a0)).toFloat(), 0f, cz + (r * sin(a0)).toFloat())
            val p1 = Vec3(cx + (r * cos(a1)).toFloat(), 0f, cz + (r * sin(a1)).toFloat())
            quad(color, Vec3(p1.x, y0, p1.z), Vec3(p0.x, y0, p0.z), Vec3(p0.x, y1, p0.z), Vec3(p1.x, y1, p1.z))
            tri(top, Vec3(cx, y1, cz), Vec3(p1.x, y1, p1.z), Vec3(p0.x, y1, p0.z))
        }
    }

    /** Vertical cone (tree crown). */
    fun cone(color: Int, cx: Float, cz: Float, r: Float, y0: Float, y1: Float, seg: Int = 10) {
        for (i in 0 until seg) {
            val a0 = 2 * PI * i / seg
            val a1 = 2 * PI * (i + 1) / seg
            val p0 = Vec3(cx + (r * cos(a0)).toFloat(), y0, cz + (r * sin(a0)).toFloat())
            val p1 = Vec3(cx + (r * cos(a1)).toFloat(), y0, cz + (r * sin(a1)).toFloat())
            tri(color, p1, p0, Vec3(cx, y1, cz))
        }
    }

    fun build(name: String) = b.build(name)

    @Suppress("unused")
    private fun unusedMax(a: Float, c: Float) = max(a, c)
}
