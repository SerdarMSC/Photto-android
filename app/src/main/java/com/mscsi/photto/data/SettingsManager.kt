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
 * Varsayılan değerler kullanıcı gereksiniminden:
 *   - count = 6
 *   - min   = 0
 *   - max   = 60
 */
object SettingsManager {

    private const val PREFS_NAME = "photto_settings"
    private const val KEY_COUNT = "count"
    private const val KEY_MIN = "min"
    private const val KEY_MAX = "max"

    const val DEFAULT_COUNT = 6
    const val DEFAULT_MIN = 0
    const val DEFAULT_MAX = 60

    /** Slider aralıkları (UI tarafında kullanılır). */
    const val COUNT_MIN = 1
    const val COUNT_MAX = 18
    const val RANGE_BOUND = 255   // min/max 0..255 arasında seçilebilir

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): AppSettings {
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_COUNT, DEFAULT_COUNT).coerceIn(COUNT_MIN, COUNT_MAX)
        var min = prefs.getInt(KEY_MIN, DEFAULT_MIN).coerceIn(0, RANGE_BOUND)
        var max = prefs.getInt(KEY_MAX, DEFAULT_MAX).coerceIn(0, RANGE_BOUND)
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
