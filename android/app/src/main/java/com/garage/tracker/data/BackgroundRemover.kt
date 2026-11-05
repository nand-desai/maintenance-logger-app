package com.garage.tracker.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

/**
 * Removes a flat background entirely on the phone. It samples the picture's corners for the
 * background colour and clears every pixel of that colour that connects to the edge, so colours
 * inside the car are left alone. Works best on a plain background such as a green screen.
 */
object BackgroundRemover {
    private const val MAX_DIST = 441.67295f // sqrt(3 * 255^2)

    /** True when the four corners are (nearly) the same colour, i.e. the picture probably has a flat background. */
    fun looksFlat(src: Bitmap): Boolean {
        val c = corners(src)
        val avg = IntArray(3) { i -> c.sumOf { it[i] } / c.size }
        return c.all { dist(it[0], it[1], it[2], avg[0], avg[1], avg[2]) < 40f }
    }

    /** [tolerance] is 0..1, the share of the maximum colour distance still counted as background. */
    fun remove(src: Bitmap, tolerance: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val c = corners(src)
        val br = c.sumOf { it[0] } / c.size
        val bg = c.sumOf { it[1] } / c.size
        val bb = c.sumOf { it[2] } / c.size
        val limit = tolerance.coerceIn(0.01f, 0.9f) * MAX_DIST

        fun isBg(i: Int): Boolean {
            val p = px[i]
            return dist(Color.red(p), Color.green(p), Color.blue(p), br, bg, bb) <= limit
        }

        val cleared = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun push(i: Int) {
            if (!cleared[i] && isBg(i)) { cleared[i] = true; queue[tail++] = i }
        }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }

        // Soften the edge: pixels touching the cleared area fade out the closer they are to the background colour.
        val out = IntArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            if (cleared[i]) { out[i] = 0; continue }
            val x = i % w
            val y = i / w
            val touches = (x > 0 && cleared[i - 1]) || (x < w - 1 && cleared[i + 1]) ||
                (y > 0 && cleared[i - w]) || (y < h - 1 && cleared[i + w])
            if (touches) {
                val d = dist(Color.red(p), Color.green(p), Color.blue(p), br, bg, bb)
                val alpha = ((d / (limit * 1.8f)).coerceIn(0f, 1f) * 255).toInt()
                out[i] = Color.argb(alpha, Color.red(p), Color.green(p), Color.blue(p))
            } else out[i] = p
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun corners(src: Bitmap): List<IntArray> {
        val w = src.width
        val h = src.height
        val s = (minOf(w, h) / 40).coerceIn(2, 12)
        fun block(x0: Int, y0: Int): IntArray {
            var r = 0; var g = 0; var b = 0; var n = 0
            for (y in y0 until y0 + s) for (x in x0 until x0 + s) {
                val p = src.getPixel(x, y)
                r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
            }
            return intArrayOf(r / n, g / n, b / n)
        }
        return listOf(block(0, 0), block(w - s, 0), block(0, h - s), block(w - s, h - s))
    }

    private fun dist(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Float {
        val dr = (r1 - r2).toFloat(); val dg = (g1 - g2).toFloat(); val db = (b1 - b2).toFloat()
        return sqrt(dr * dr + dg * dg + db * db)
    }
}
