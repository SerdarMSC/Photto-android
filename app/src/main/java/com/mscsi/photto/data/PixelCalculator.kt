package com.mscsi.photto.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

/**
 * Tüm fotoğraftan hesaplanan ham metrikler (istatistik ekranı için).
 * Her değer 0-255 aralığındadır.
 */
data class RawMetrics(
    val red: Int,           // Ortalama Kırmızı
    val green: Int,         // Ortalama Yeşil
    val blue: Int,          // Ortalama Mavi
    val brightness: Int,    // Ortalama Parlaklık (BT.601 luminance)
    val saturation: Int,    // Ortalama Doygunluk (HSV S, 0..255)
    val contrast: Int       // Parlaklığın standart sapması
)

/**
 * Hesaplama sonucu: [min, max] aralığında N adet BENZERSİZ sayı + ham metrikler.
 */
data class PixelResult(
    val numbers: List<Int>,     // Benzersiz sayılar (loto!)
    val labels: List<String>,   // "Kare 1", "Kare 2", ... (hangi kareden üretildi)
    val raw: RawMetrics
)

/**
 * Fotoğrafı [count] adet kareye böler, her kareden FNV-1a 64-bit hash ile
 * bir sayı üretir ve sayıların BENZERSİZ olmasını garanti eder.
 *
 * Algoritma:
 *  1. Fotoğrafı yaklaşık kare grid'e böl: cols = ceil(sqrt(count)).
 *     Örn. 6 sayı -> 3x2 grid, 7 sayı -> 3x3 (ilk 7 kare kullanılır).
 *  2. Her karede pikseller örneklenir; R/G/B değerleri piksel piksel
 *     FNV-1a akışına beslenir. Ardından toplu metrikler (ort. R, G, B,
 *     parlaklık, doygunluk, kontrast) da hash'e katılır.
 *  3. Hash, [min, max] aralığına modulo ile map'lenir.
 *  4. Aynı sayı iki kez çıkarsa altın oran adımı (golden-ratio probing)
 *     ile bir sonraki boş sayı seçilir. Sonuç: sayılar her zaman benzersiz.
 *
 * Deterministiktir: aynı fotoğraf her zaman aynı sayıları verir.
 *
 * @throws IllegalArgumentException aralıkta count adet benzersiz sayı yoksa
 *         (max - min + 1 < count).
 */
object PixelCalculator {

    /** Tüm görsel için maksimum örnek sayısı (global metrikler). */
    private const val MAX_SAMPLES = 100_000

    /** Kare (tile) başına maksimum örnek sayısı (hash akışı). */
    private const val MAX_TILE_SAMPLES = 5000

    /** FNV-1a 64-bit offset basis (bit deseni korunur). */
    private val FNV_OFFSET = 0xcbf29ce484222325uL.toLong()

    /** FNV-1a 64-bit asal çarpan. */
    private const val FNV_PRIME = 0x100000001b3L

    /** Altın oran adımı (benzersizlik probing). */
    private val GOLDEN_STEP = 0x9E3779B97F4A7C15uL.toLong()

    /**
     * Tüm bitmap'in global metriklerini üretir (istatistik paneli için).
     */
    fun computeRaw(bitmap: Bitmap): RawMetrics {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) {
            return RawMetrics(0, 0, 0, 0, 0, 0)
        }

        val totalPixels = w.toLong() * h
        val step = if (totalPixels > MAX_SAMPLES) {
            sqrt(totalPixels.toDouble() / MAX_SAMPLES).toInt().coerceAtLeast(1)
        } else 1

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var sumBright = 0L
        var sumBrightSq = 0L
        var sumSat = 0.0
        var sampleCount = 0L

        val hsv = FloatArray(3)

        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                sumR += r
                sumG += g
                sumB += b

                val bright = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                sumBright += bright
                sumBrightSq += bright.toLong() * bright

                Color.RGBToHSV(r, g, b, hsv)
                sumSat += hsv[1]   // 0..1
                sampleCount++
            }
        }

        if (sampleCount == 0L) sampleCount = 1

        val avgR = (sumR / sampleCount).toInt()
        val avgG = (sumG / sampleCount).toInt()
        val avgB = (sumB / sampleCount).toInt()
        val avgBright = (sumBright / sampleCount).toInt()
        val avgSat01 = sumSat / sampleCount                // 0..1
        val avgSat = (avgSat01 * 255).toInt()              // 0..255 ölçek
        val meanD = avgBright.toDouble()
        val variance = (sumBrightSq.toDouble() / sampleCount) - (meanD * meanD)
        val stdDev = sqrt(variance.coerceAtLeast(0.0)).toInt()

        return RawMetrics(
            red = avgR,
            green = avgG,
            blue = avgB,
            brightness = avgBright,
            saturation = avgSat,
            contrast = stdDev
        )
    }

    /**
     * Bir kareyi (tile) tarayıp FNV-1a 64-bit hash üretir.
     * Aynı kare her zaman aynı hash'i verir.
     */
    private fun tileHash(tile: Bitmap): Long {
        val w = tile.width
        val h = tile.height
        if (w <= 0 || h <= 0) return FNV_OFFSET

        val total = w.toLong() * h
        val step = if (total > MAX_TILE_SAMPLES) {
            sqrt(total.toDouble() / MAX_TILE_SAMPLES).toInt().coerceAtLeast(1)
        } else 1

        var hash = FNV_OFFSET
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var sumBright = 0L
        var sumBrightSq = 0L
        var sumSat = 0.0
        var n = 0L
        val hsv = FloatArray(3)

        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val p = tile.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)

                sumR += r
                sumG += g
                sumB += b

                val br = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                sumBright += br
                sumBrightSq += br.toLong() * br

                Color.RGBToHSV(r, g, b, hsv)
                sumSat += hsv[1]
                n++

                // Piksel değerlerini hash akışına kat (sıralı, deterministik)
                hash = fnvMix(hash, r.toLong())
                hash = fnvMix(hash, g.toLong())
                hash = fnvMix(hash, b.toLong())
            }
        }

        if (n == 0L) n = 1

        // Toplu metrikleri de hash'e kat
        hash = fnvMix(hash, sumR / n)
        hash = fnvMix(hash, sumG / n)
        hash = fnvMix(hash, sumB / n)
        val avgBright = (sumBright / n).toInt()
        hash = fnvMix(hash, avgBright.toLong())
        hash = fnvMix(hash, (sumSat / n * 10_000).toLong())
        val variance = (sumBrightSq.toDouble() / n) - avgBright.toDouble() * avgBright
        hash = fnvMix(hash, sqrt(variance.coerceAtLeast(0.0)).toRawBits())

        return hash
    }

    /** FNV-1a bir karıştırma adımı: hash = (hash xor value) * prime. */
    private fun fnvMix(hash: Long, value: Long): Long =
        (hash xor value) * FNV_PRIME

    /**
     * Fotoğrafı karelere böler, her kareden benzersiz bir sayı üretir.
     *
     * @throws IllegalArgumentException (max - min + 1) < count ise
     */
    fun calculate(bitmap: Bitmap, count: Int, min: Int, max: Int): PixelResult {
        val safeCount = count.coerceAtLeast(1)
        val span = max - min + 1

        if (span < safeCount) {
            throw IllegalArgumentException(
                "Aralık yetersiz: [$min, $max] aralığında $safeCount benzersiz sayı yok"
            )
        }

        val raw = computeRaw(bitmap)

        // Kare grid boyutları
        val cols = sqrt(safeCount.toDouble()).toInt().coerceAtLeast(1)
        val rows = ((safeCount + cols - 1) / cols).coerceAtLeast(1)

        val w = bitmap.width
        val h = bitmap.height
        val tileW = (w / cols).coerceAtLeast(1)
        val tileH = (h / rows).coerceAtLeast(1)

        val numbers = ArrayList<Int>(safeCount)
        val labels = ArrayList<String>(safeCount)
        val used = HashSet<Int>(safeCount)

        for (i in 0 until safeCount) {
            val col = i % cols
            val row = i / cols
            val x0 = (col * tileW).coerceAtMost((w - tileW).coerceAtLeast(0)).coerceAtLeast(0)
            val y0 = (row * tileH).coerceAtMost((h - tileH).coerceAtLeast(0)).coerceAtLeast(0)

            val tile = Bitmap.createBitmap(bitmap, x0, y0, tileW, tileH)
            val hash = tileHash(tile)

            // Hash -> [min, max] aralığı (negatif Long işaret biti temizlenir)
            val base = hash and Long.MAX_VALUE
            var value = min + (base % span).toInt()

            // Benzersizlik: çakışma varsa altın oran adımıyla gez
            var k = 0L
            while (!used.add(value)) {
                k++
                val mixed = (base + k * GOLDEN_STEP) and Long.MAX_VALUE
                value = min + (mixed % span).toInt()
            }

            numbers.add(value)
            labels.add("Kare ${i + 1}")
        }

        // Sayıları küçükten büyüğe sırala (etiketler kendi sayısıyla birlikte taşınır)
        val sortedPairs = numbers.zip(labels).sortedBy { it.first }
        val sortedNumbers = sortedPairs.map { it.first }
        val sortedLabels = sortedPairs.map { it.second }

        return PixelResult(numbers = sortedNumbers, labels = sortedLabels, raw = raw)
    }
}
