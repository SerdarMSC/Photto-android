package com.mscsi.photto.data

import android.content.Context
import android.content.SharedPreferences

/** Kullanıcı ayarları: üretilecek sayı adedi ve min-max aralığı. */
data class AppSettings(
    val count: Int,
    val min: Int,
    val max: Int
)

/**
 * Ayarları SharedPreferences üzerinde saklar.
 *
 * Varsayılan değerler:
 *   - count = 6
 *   - min   = 1  (0 asla üretilmemesi için minimum sınır 1'dir)
 *   - max   = 60
 */
object SettingsManager {

    private const val PREFS_NAME = "photto_settings"
    private const val KEY_COUNT = "count"
    private const val KEY_MIN = "min"
    private const val KEY_MAX = "max"

    const val DEFAULT_COUNT = 6
    const val DEFAULT_MIN = 1
    const val DEFAULT_MAX = 60

    /**
     * Minimum değer için alt sınır: 0 HİÇBİR ZAMAN üretilmemeli.
     * Bu yüzden kullanıcı minimum olarak en az 1 seçebilir.
     */
    const val MIN_VALUE_LIMIT = 1

    /** Slider aralıkları (UI tarafında kullanılır). */
    const val COUNT_MIN = 1
    const val COUNT_MAX = 18
    const val RANGE_BOUND = 255   // max 1..255 arasında seçilebilir

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): AppSettings {
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_COUNT, DEFAULT_COUNT).coerceIn(COUNT_MIN, COUNT_MAX)
        // min asla 1'in altına inmez (0 yasak)
        var min = prefs.getInt(KEY_MIN, DEFAULT_MIN).coerceIn(MIN_VALUE_LIMIT, RANGE_BOUND)
        var max = prefs.getInt(KEY_MAX, DEFAULT_MAX).coerceIn(MIN_VALUE_LIMIT, RANGE_BOUND)
        // Tutarlılık: min < max olmalı
        if (min >= max) {
            min = DEFAULT_MIN
            max = DEFAULT_MAX
        }
        return AppSettings(count = count, min = min, max = max)
    }

    fun save(context: Context, settings: AppSettings) {
        getPrefs(context).edit().apply {
            putInt(KEY_COUNT, settings.count)
            putInt(KEY_MIN, settings.min)
            putInt(KEY_MAX, settings.max)
            apply()
        }
    }

    fun defaults(): AppSettings = AppSettings(DEFAULT_COUNT, DEFAULT_MIN, DEFAULT_MAX)
}
