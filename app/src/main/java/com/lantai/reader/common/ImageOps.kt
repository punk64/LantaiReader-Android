package com.lantai.reader.common

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.roundToInt

object ImageOps {
    fun tune(src: Bitmap, crop: Boolean, enhance: Boolean): Bitmap {
        if (!crop && !enhance) return src
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val edges = IntArray(4)
        if (crop) {
            val dy = maxOf(6, (h * 0.1).toInt())
            val dx = maxOf(6, (w * 0.1).toInt())
            for (side in 0 until 4) {
                val vertical = side < 2
                val far = side == 1 || side == 3
                val depth = if (vertical) dy else dx
                val span = if (vertical) w else h
                val origin = (if (vertical) h else w) - depth
                val ref = if (far) origin else 0
                var sr = 0L
                var sg = 0L
                var sb = 0L
                var count = 0
                var t = 0
                while (t < span) {
                    val y = if (vertical) ref else t
                    val x = if (vertical) t else ref
                    val c = pixels[y * w + x]
                    sr += (c shr 16) and 0xFF
                    sg += (c shr 8) and 0xFF
                    sb += c and 0xFF
                    count++
                    t += 8
                }
                if (count == 0) continue
                val bgR = (sr / count).toInt()
                val bgG = (sg / count).toInt()
                val bgB = (sb / count).toInt()
                for (step in 0 until depth) {
                    val line = if (far) depth - 1 - step else step
                    val pos = if (far) origin + line else line
                    var diff = 0
                    var k = 0
                    while (k < span) {
                        val y = if (vertical) pos else k
                        val x = if (vertical) k else pos
                        val c = pixels[y * w + x]
                        val d = abs(((c shr 16) and 0xFF) - bgR) + abs(((c shr 8) and 0xFF) - bgG) + abs((c and 0xFF) - bgB)
                        if (d > 42) diff++
                        k += 12
                    }
                    if (diff > 2) {
                        edges[side] = if (far) depth - line else line
                        break
                    }
                }
            }
        }
        val top = edges[0]
        val bottom = edges[1]
        val left = edges[2]
        val right = edges[3]
        val nw = w - left - right
        val nh = h - top - bottom
        val doCrop = crop && nw > 0 && nh > 0 && nw >= w * 0.35f && nh >= h * 0.35f && (top + bottom + left + right) > 0
        val out = if (doCrop) Bitmap.createBitmap(src, left, top, nw, nh) else src
        if (!enhance) return out
        val lut = IntArray(256) { v -> ((v - 128) * 1.08f + 128 - 4).roundToInt().coerceIn(0, 255) }
        val ow = out.width
        val oh = out.height
        val buf = IntArray(ow * oh)
        out.getPixels(buf, 0, ow, 0, 0, ow, oh)
        for (i in buf.indices) {
            val c = buf[i]
            val a = (c ushr 24) and 0xFF
            val r = lut[(c shr 16) and 0xFF]
            val g = lut[(c shr 8) and 0xFF]
            val b = lut[c and 0xFF]
            buf[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val tuned = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        tuned.setPixels(buf, 0, ow, 0, 0, ow, oh)
        if (out !== src) out.recycle()
        return tuned
    }
}
