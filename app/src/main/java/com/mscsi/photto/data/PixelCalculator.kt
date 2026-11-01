package com.mscsi.photto.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

/**
 * Fotoğraf piksellerinden hesaplanan ham metrikler.
 * Her değer 0-255 aralığındadır.
 */
data class RawMetrics(
    val red: Int,           // Ortalama Kırmızı
    val green: Int,         // Ortalama Yeşil
    val blue: Int,          // Ortalama Mavi
    val brightness: Int,    // Ortalama Parlaklık (BT.601 luminance)
    val saturation: Int,    // Ortalama Doygunluk (HSV S, 0..100 -> 0..255 ölçeklendi)
    val contrast: Int       // Parlaklığın standart sapması
)

/**
 * Hesaplama sonucu: kullanıcı aralığına ölçeklenmiş sayılar + ham metrikler.
 */
data class PixelResult(
    val numbers: List<Int>,     // Kullanıcının seçtiği aralıktaki n adet sayı
    val labels: List<String>,   // Her sayının hangi metriktan türetildiği
    val raw: RawMetrics
)

/**
 * Fotoğraf piksellerinden 6 temel metrik üretir ve bunları kullanıcının seçtiği
 * [min, max] aralığına ölçekleyerek [count] adet sayı verir.
 *
 * Üretilen 6 temel metrik:
 *  1. Ortalama Kırmızı (R)
 *  2. Ortalama Yeşil   (G)
 *  3. Ortalama Mavi    (B)
 *  4. Ortalama Parlaklık (0.299R + 0.587G + 0.114B)
 *  5. Ortalama Doygunluk (HSV S kanalı)
 *  6. Kontrast         (Parlaklığın std. sapması)
 *
 * count > 6 ise metrikler döngüsel olarak tekrar eder (modül 6).
 */
object PixelCalculator {

    /** Çok büyük görsellerde performans için örneklem siniri. */
    private const val MAX_SAMPLES = 100_000

    /** Tek bir metrik 0..255 → min..max ölçeklemesi (yuvarlama). */
    private fun scaleToRange(value: Int, min: Int, max: Int): Int {
        val span = max - min
        if (span <= 0) return min
        val scaled = (value.toLong() * span + 127) / 255
        return (min + scaled).toInt().coerceIn(min, max)
    }

    /** Bitmap'i tarayıp ham metrikleri üretir. */
    fun computeRaw(bitmap: Bitmap): RawMetrics {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) {
            return RawMetrics(0, 0, 0, 0, 0, 0)
        }

        val totalPixels = w.toLong() * h
        val step = if (totalPixels > MAX_SAMPLES) {
            Math.sqrt(totalPixels.toDouble() / MAX_SAMPLES).toInt().coerceAtLeast(1)
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

    /** Ham metrikleri kullanıcı aralığına ölçekleyip [count] adet sayı üretir. */
    fun calculate(bitmap: Bitmap, count: Int, min: Int, max: Int): PixelResult {
        val raw = computeRaw(bitmap)

        // 6 temel metrik ve etiketleri (Türkçe)
        val rawValues = listOf(raw.red, raw.green, raw.blue, raw.brightness, raw.saturation, raw.contrast)
        val labels = listOf(
            "Kırmızı", "Yeşil", "Mavi",
            "Parlaklık", "Doygunluk", "Kontrast"
        )

        val safeCount = count.coerceAtLeast(1)
        val scaled = ArrayList<Int>(safeCount)
        val outLabels = ArrayList<String>(safeCount)

        for (i in 0 until safeCount) {
            val srcIndex = i % rawValues.size
            scaled.add(scaleToRange(rawValues[srcIndex], min, max))
            outLabels.add(labels[srcIndex])
        }

        return PixelResult(numbers = scaled, labels = outLabels, raw = raw)
    }
}
