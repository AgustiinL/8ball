package cl.guia8ball

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Det(
    val x: Float,
    val y: Float,
    val r: Int,
    val g: Int,
    val b: Int,
    var white: Boolean = false,
    var dark: Boolean = false,
    val sat: Float = 0f,
    val v: Float = 0f
)

/** Resultado de la detección automática (coordenadas en px de pantalla). */
class Auto(
    val tx: Int, val ty: Int, val tw: Int, val th: Int,
    val rad: Float,   // radio de bola
    val pc: Float,    // radio de tronera de esquina
    val ps: Float,    // radio de tronera central
    val dets: List<Det>
)

private class Prep(val w: Int, val h: Int, val d: IntArray)

/**
 * Visión por computador 100% en el celular, sin dependencias:
 *  - mesa: región más grande con el color dominante del paño
 *  - bolas: lo que se diferencia del paño + transformada de distancia (centros de discos)
 *  - radio de bola: mediana de los discos encontrados
 *  - troneras: zonas oscuras en esquinas y centros de los lados largos
 */
object BallDetector {

    // ---------- Detección automática completa ----------
    fun autoDetect(bmp: Bitmap, imgW: Int, imgH: Int, sens: Int): Auto? {
        val sw = min(bmp.width, imgW)
        val sh = min(bmp.height, imgH)
        if (sw < 100 || sh < 100) return null
        val s = min(1f, 360f / sw)
        val w = max(8, (sw * s).toInt())
        val h = max(8, (sh * s).toInt())
        val px = IntArray(w * h)
        for (j in 0 until h) {
            val sy = min(sh - 1, ((j + 0.5f) / s).toInt())
            for (i in 0 until w) {
                val sx = min(sw - 1, ((i + 0.5f) / s).toInt())
                px[j * w + i] = bmp.getPixel(sx, sy)
            }
        }

        // Color del paño: el más frecuente en la zona central (ignorando negros)
        val hist = IntArray(4096)
        for (j in h / 5 until h * 4 / 5) {
            for (i in w / 5 until w * 4 / 5) {
                val p = px[j * w + i]
                val r = (p shr 16) and 255
                val g = (p shr 8) and 255
                val b = p and 255
                if (max(r, max(g, b)) < 50) continue
                hist[((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)]++
            }
        }
        var best = 0
        for (k in 1 until 4096) if (hist[k] > hist[best]) best = k
        if (hist[best] == 0) return null
        val br = ((best shr 8) and 15) * 16 + 8
        val bg = ((best shr 4) and 15) * 16 + 8
        val bb = (best and 15) * 16 + 8
        var sr = 0L
        var sg = 0L
        var sb = 0L
        var cnt = 0
        for (j in h / 5 until h * 4 / 5) {
            for (i in w / 5 until w * 4 / 5) {
                val p = px[j * w + i]
                val r = (p shr 16) and 255
                val g = (p shr 8) and 255
                val b = p and 255
                if (abs(r - br) < 28 && abs(g - bg) < 28 && abs(b - bb) < 28) {
                    sr += r; sg += g; sb += b; cnt++
                }
            }
        }
        val fr = if (cnt > 0) (sr / cnt).toInt() else br
        val fg = if (cnt > 0) (sg / cnt).toInt() else bg
        val fb = if (cnt > 0) (sb / cnt).toInt() else bb

        // Máscara del paño + cierre para tapar las bolas
        val tol2 = 48 * 48
        var mask = BooleanArray(w * h)
        for (k in px.indices) {
            val p = px[k]
            val dr = ((p shr 16) and 255) - fr
            val dg = ((p shr 8) and 255) - fg
            val db = (p and 255) - fb
            mask[k] = dr * dr + dg * dg + db * db < tol2
        }
        mask = morph(morph(mask, w, h, true), w, h, false)

        // Mayor componente conexa
        val label = IntArray(w * h)
        val queue = IntArray(w * h)
        var bestLabel = 0
        var bestArea = 0
        var cur = 0
        for (k in 0 until w * h) {
            if (!mask[k] || label[k] != 0) continue
            cur++
            var qh = 0
            var qt = 0
            queue[qt++] = k
            label[k] = cur
            while (qh < qt) {
                val c = queue[qh++]
                val ci = c % w
                val cj = c / w
                if (ci > 0 && mask[c - 1] && label[c - 1] == 0) { label[c - 1] = cur; queue[qt++] = c - 1 }
                if (ci < w - 1 && mask[c + 1] && label[c + 1] == 0) { label[c + 1] = cur; queue[qt++] = c + 1 }
                if (cj > 0 && mask[c - w] && label[c - w] == 0) { label[c - w] = cur; queue[qt++] = c - w }
                if (cj < h - 1 && mask[c + w] && label[c + w] == 0) { label[c + w] = cur; queue[qt++] = c + w }
            }
            if (qt > bestArea) { bestArea = qt; bestLabel = cur }
        }
        if (bestArea < w * h * 0.08) return null

        val colCount = IntArray(w)
        val rowCount = IntArray(h)
        for (k in 0 until w * h) {
            if (label[k] == bestLabel) { colCount[k % w]++; rowCount[k / w]++ }
        }
        val maxC = colCount.max()
        val maxR = rowCount.max()
        var minX = 0
        while (minX < w - 1 && colCount[minX] < 0.5f * maxC) minX++
        var maxX = w - 1
        while (maxX > 0 && colCount[maxX] < 0.5f * maxC) maxX--
        var minY = 0
        while (minY < h - 1 && rowCount[minY] < 0.5f * maxR) minY++
        var maxY = h - 1
        while (maxY > 0 && rowCount[maxY] < 0.5f * maxR) maxY--
        if (maxX <= minX || maxY <= minY) return null

        val tx = (minX / s).toInt()
        val ty = (minY / s).toInt()
        val tw = ((maxX + 1 - minX) / s).toInt()
        val th = ((maxY + 1 - minY) / s).toInt()
        if (tw < sw * 0.3f || th < sh * 0.2f) return null

        // Radio de bola: percentil 75 de los discos encontrados dentro de la mesa
        val r0 = 0.0225f * th
        val s0 = min(1f, 8f / r0)
        val r0s = r0 * s0
        val p0 = prepare(bmp, tx, ty, tw, th, s0, sens)
        val pk0 = peaks(p0, (0.5f * r0s * 3f).toInt(), (2.2f * r0s * 3f).toInt(), r0s)
        var rad = r0
        if (pk0.size >= 2) {
            val sorted = pk0.map { it[2] / 3f / s0 }.sorted()
            rad = sorted[(sorted.size * 3) / 4]
        }
        rad = rad.coerceIn(0.6f * r0, 2f * r0)

        val dets = detect(bmp, tx, ty, tw, th, rad, sens)

        // Troneras: zonas oscuras alrededor de esquinas y centros de los lados largos
        val off = 0.5f * rad
        val x2 = (tx + tw).toFloat()
        val y2 = (ty + th).toFloat()
        val xm = tx + tw / 2f
        val corners = listOf(
            pocketRadius(bmp, tx - off, ty - off, rad),
            pocketRadius(bmp, x2 + off, ty - off, rad),
            pocketRadius(bmp, tx - off, y2 + off, rad),
            pocketRadius(bmp, x2 + off, y2 + off, rad)
        ).sorted()
        val sides = listOf(
            pocketRadius(bmp, xm, ty - off, rad),
            pocketRadius(bmp, xm, y2 + off, rad)
        )
        var pc = (corners[1] + corners[2]) / 2f
        var ps = (sides[0] + sides[1]) / 2f
        pc = if (pc < 1.0f * rad) 2.3f * rad else pc.coerceIn(1.5f * rad, 3.4f * rad)
        ps = if (ps < 0.8f * rad) 1.8f * rad else ps.coerceIn(1.2f * rad, 2.6f * rad)

        return Auto(tx, ty, tw, th, rad, pc, ps, dets)
    }

    private fun pocketRadius(bmp: Bitmap, cx: Float, cy: Float, rad: Float): Float {
        val rr = 3.5f * rad
        val st = max(1, (rad / 6f).toInt())
        val x0 = max(0, (cx - rr).toInt())
        val x1 = min(bmp.width - 1, (cx + rr).toInt())
        val y0 = max(0, (cy - rr).toInt())
        val y1 = min(bmp.height - 1, (cy + rr).toInt())
        var cnt = 0
        var y = y0
        while (y <= y1) {
            var x = x0
            while (x <= x1) {
                if (hypot(x - cx, y - cy) <= rr) {
                    val p = bmp.getPixel(x, y)
                    val mx = max((p shr 16) and 255, max((p shr 8) and 255, p and 255))
                    if (mx < 60) cnt++
                }
                x += st
            }
            y += st
        }
        val area = cnt.toFloat() * st * st
        return sqrt(area / Math.PI.toFloat())
    }

    // ---------- Detección de bolas en una región ----------
    fun detect(bmp: Bitmap, rx: Int, ry: Int, rw: Int, rh: Int, rad: Float, sens: Int): List<Det> {
        val x0 = max(0, rx)
        val y0 = max(0, ry)
        val x1 = min(bmp.width, rx + rw)
        val y1 = min(bmp.height, ry + rh)
        val w0 = x1 - x0
        val h0 = y1 - y0
        if (w0 < 40 || h0 < 40 || rad < 4f) return emptyList()

        val s = min(1f, 8f / rad)
        val p = prepare(bmp, x0, y0, w0, h0, s, sens)
        val w = p.w
        val h = p.h
        val d = p.d
        val radS = rad * s
        val minD = (0.7f * radS * 3f).toInt()
        val maxD = (1.4f * radS * 3f).toInt()
        val pks = peaks(p, minD, maxD, 1.5f * radS)

        // Troneras (en px de pantalla) para descartarlas
        val pk = arrayOf(
            floatArrayOf(x0.toFloat(), y0.toFloat(), 1.9f),
            floatArrayOf(x1.toFloat(), y0.toFloat(), 1.9f),
            floatArrayOf(x0.toFloat(), y1.toFloat(), 1.9f),
            floatArrayOf(x1.toFloat(), y1.toFloat(), 1.9f),
            floatArrayOf((x0 + x1) / 2f, y0.toFloat(), 1.5f),
            floatArrayOf((x0 + x1) / 2f, y1.toFloat(), 1.5f)
        )

        val result = ArrayList<Det>()
        for (q in pks) {
            if (result.size >= 16) break
            val ci = q[0]
            val cj = q[1]
            val peak = q[2]

            // Refinar el centro: promedio de píxeles cercanos al máximo
            var sxm = 0f
            var sym = 0f
            var n = 0
            val rr = radS.toInt() + 1
            for (jj in max(0, cj - rr)..min(h - 1, cj + rr)) {
                for (ii in max(0, ci - rr)..min(w - 1, ci + rr)) {
                    if (d[jj * w + ii] >= peak - 3 &&
                        hypot((ii - ci).toFloat(), (jj - cj).toFloat()) <= radS
                    ) {
                        sxm += ii; sym += jj; n++
                    }
                }
            }
            val cx = if (n > 0) sxm / n else ci.toFloat()
            val cy = if (n > 0) sym / n else cj.toFloat()
            val scx = x0 + (cx + 0.5f) / s
            val scy = y0 + (cy + 0.5f) / s

            var nearPocket = false
            for (t in pk) {
                if (hypot(t[0] - scx, t[1] - scy) < t[2] * rad) { nearPocket = true; break }
            }
            if (nearPocket) continue

            // Color promedio en un anillo (evita el círculo blanco del número)
            var cr = 0
            var cg = 0
            var cb = 0
            var cn = 0
            for (a in 0 until 24) {
                val ang = a * Math.PI / 12.0
                for (f in floatArrayOf(0.6f, 0.8f)) {
                    val qx = (scx + cos(ang) * rad * f).toInt()
                    val qy = (scy + sin(ang) * rad * f).toInt()
                    if (qx in 0 until bmp.width && qy in 0 until bmp.height) {
                        val c = bmp.getPixel(qx, qy)
                        cr += (c shr 16) and 255
                        cg += (c shr 8) and 255
                        cb += c and 255
                        cn++
                    }
                }
            }
            if (cn == 0) continue
            val mr = cr / cn
            val mg = cg / cn
            val mb = cb / cn
            val mx = max(mr, max(mg, mb)).toFloat()
            val mn = min(mr, min(mg, mb)).toFloat()
            val v = mx / 255f
            val sat = if (mx > 0f) (mx - mn) / mx else 0f
            result.add(Det(scx, scy, mr, mg, mb, false, false, sat, v))
        }

        // La blanca: la más clara y menos saturada. La 8: la más oscura.
        val whites = result.filter { it.v > 0.78f && it.sat < 0.22f }
        whites.maxByOrNull { it.v - it.sat }?.white = true
        val darks = result.filter { it.v < 0.30f && !it.white }
        darks.minByOrNull { it.v }?.dark = true
        return result
    }

    // ---------- Piezas comunes ----------

    // Reduce la región, separa "paño" de "objetos" y calcula la transformada de distancia
    private fun prepare(bmp: Bitmap, x0: Int, y0: Int, w0: Int, h0: Int, s: Float, sens: Int): Prep {
        val x1 = min(bmp.width, x0 + w0)
        val y1 = min(bmp.height, y0 + h0)
        val rw = max(1, x1 - x0)
        val rh = max(1, y1 - y0)
        val w = max(8, (rw * s).toInt())
        val h = max(8, (rh * s).toInt())
        val px = IntArray(w * h)
        for (j in 0 until h) {
            val sy = y0 + min(rh - 1, ((j + 0.5f) / s).toInt())
            for (i in 0 until w) {
                val sx = x0 + min(rw - 1, ((i + 0.5f) / s).toInt())
                px[j * w + i] = bmp.getPixel(sx, sy)
            }
        }

        val hist = IntArray(4096)
        for (p in px) {
            val r = (p shr 16) and 255
            val g = (p shr 8) and 255
            val b = p and 255
            hist[((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)]++
        }
        var best = 0
        for (k in 1 until 4096) if (hist[k] > hist[best]) best = k
        val br = ((best shr 8) and 15) * 16 + 8
        val bg = ((best shr 4) and 15) * 16 + 8
        val bb = (best and 15) * 16 + 8
        var sr = 0L
        var sg = 0L
        var sb = 0L
        var cnt = 0
        for (p in px) {
            val r = (p shr 16) and 255
            val g = (p shr 8) and 255
            val b = p and 255
            if (abs(r - br) < 28 && abs(g - bg) < 28 && abs(b - bb) < 28) {
                sr += r; sg += g; sb += b; cnt++
            }
        }
        val fr = if (cnt > 0) (sr / cnt).toInt() else br
        val fg = if (cnt > 0) (sg / cnt).toInt() else bg
        val fb = if (cnt > 0) (sb / cnt).toInt() else bb

        val thr2 = sens * sens
        var mask = BooleanArray(w * h)
        for (k in px.indices) {
            val p = px[k]
            val dr = ((p shr 16) and 255) - fr
            val dg = ((p shr 8) and 255) - fg
            val db = (p and 255) - fb
            mask[k] = dr * dr + dg * dg + db * db > thr2
        }
        mask = morph(morph(mask, w, h, true), w, h, false)

        // Transformada de distancia (chamfer 3-4), unidades de 1/3 px
        val inf = 1_000_000
        val d = IntArray(w * h) { if (mask[it]) inf else 0 }
        for (j in 0 until h) {
            for (i in 0 until w) {
                val idx = j * w + i
                if (d[idx] == 0) continue
                var v = d[idx]
                v = min(v, (if (i > 0) d[idx - 1] else 0) + 3)
                v = min(v, (if (j > 0) d[idx - w] else 0) + 3)
                v = min(v, (if (i > 0 && j > 0) d[idx - w - 1] else 0) + 4)
                v = min(v, (if (i < w - 1 && j > 0) d[idx - w + 1] else 0) + 4)
                d[idx] = v
            }
        }
        for (j in h - 1 downTo 0) {
            for (i in w - 1 downTo 0) {
                val idx = j * w + i
                if (d[idx] == 0) continue
                var v = d[idx]
                v = min(v, (if (i < w - 1) d[idx + 1] else 0) + 3)
                v = min(v, (if (j < h - 1) d[idx + w] else 0) + 3)
                v = min(v, (if (i < w - 1 && j < h - 1) d[idx + w + 1] else 0) + 4)
                v = min(v, (if (i > 0 && j < h - 1) d[idx + w - 1] else 0) + 4)
                d[idx] = v
            }
        }
        return Prep(w, h, d)
    }

    // Máximos locales de la transformada de distancia (supresión de no-máximos)
    private fun peaks(p: Prep, minD: Int, maxD: Int, sep: Float): List<IntArray> {
        val cand = ArrayList<Int>()
        for (k in p.d.indices) if (p.d[k] in minD..maxD) cand.add(k)
        cand.sortByDescending { p.d[it] }
        val out = ArrayList<IntArray>()
        for (k in cand) {
            if (out.size >= 40) break
            val ci = k % p.w
            val cj = k / p.w
            var ok = true
            for (o in out) {
                if (hypot((o[0] - ci).toFloat(), (o[1] - cj).toFloat()) < sep) { ok = false; break }
            }
            if (ok) out.add(intArrayOf(ci, cj, p.d[k]))
        }
        return out
    }

    private fun morph(src: BooleanArray, w: Int, h: Int, grow: Boolean): BooleanArray {
        val out = BooleanArray(w * h)
        for (j in 0 until h) {
            for (i in 0 until w) {
                var any = false
                var all = true
                for (dj in -1..1) {
                    for (di in -1..1) {
                        val ii = i + di
                        val jj = j + dj
                        val v = if (ii in 0 until w && jj in 0 until h) src[jj * w + ii] else false
                        if (v) any = true else all = false
                    }
                }
                out[j * w + i] = if (grow) any else all
            }
        }
        return out
    }
}
