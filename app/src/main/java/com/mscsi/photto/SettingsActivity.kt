package com.mscsi.photto

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mscsi.photto.data.AppSettings
import com.mscsi.photto.data.SettingsManager
import com.mscsi.photto.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val current = SettingsManager.load(this)

        // --- Sayı adedi slider ---
        binding.sliderCount.apply {
            valueFrom = SettingsManager.COUNT_MIN.toFloat()
            valueTo = SettingsManager.COUNT_MAX.toFloat()
            stepSize = 1f
            value = current.count.toFloat().coerceIn(
                SettingsManager.COUNT_MIN.toFloat(),
                SettingsManager.COUNT_MAX.toFloat()
            )
            addOnChangeListener { _, value, _ ->
                binding.tvCount.text = value.toInt().toString()
            }
        }
        binding.tvCount.text = current.count.toString()

        // --- Minimum değer slider (0 yasak: en az 1) ---
        binding.sliderMin.apply {
            valueFrom = SettingsManager.MIN_VALUE_LIMIT.toFloat()
            valueTo = SettingsManager.RANGE_BOUND.toFloat()
            stepSize = 1f
            value = current.min.toFloat()
                .coerceIn(SettingsManager.MIN_VALUE_LIMIT.toFloat(), SettingsManager.RANGE_BOUND.toFloat())
            addOnChangeListener { _, value, _ ->
                binding.tvMin.text = value.toInt().toString()
                // min, max'ı geçmemeli
                if (value.toInt() >= binding.sliderMax.value.toInt()) {
                    binding.sliderMax.value = (value.toInt() + 1).toFloat()
                        .coerceAtMost(SettingsManager.RANGE_BOUND.toFloat())
                }
            }
        }
        binding.tvMin.text = current.min.toString()

        // --- Maksimum değer slider ---
        binding.sliderMax.apply {
            valueFrom = 0f
            valueTo = SettingsManager.RANGE_BOUND.toFloat()
            stepSize = 1f
            value = current.max.toFloat().coerceIn(0f, SettingsManager.RANGE_BOUND.toFloat())
            addOnChangeListener { _, value, _ ->
                binding.tvMax.text = value.toInt().toString()
                // max, min'den küçük olmamalı
                if (value.toInt() <= binding.sliderMin.value.toInt()) {
                    binding.sliderMin.value = (value.toInt() - 1).toFloat().coerceAtLeast(0f)
                }
            }
        }
        binding.tvMax.text = current.max.toString()

        binding.btnSave.setOnClickListener {
            val count = binding.sliderCount.value.toInt()
            val min = binding.sliderMin.value.toInt()
            val max = binding.sliderMax.value.toInt()

            if (min >= max) {
                Toast.makeText(this, R.string.err_min_max, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Loto için sayılar benzersiz olmalı -> aralık yeterli olmalı
            if (max - min + 1 < count) {
                Toast.makeText(this, R.string.err_range_too_small, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            SettingsManager.save(this, AppSettings(count = count, min = min, max = max))
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnReset.setOnClickListener {
            val d = SettingsManager.defaults()
            binding.sliderCount.value = d.count.toFloat()
            binding.sliderMin.value = d.min.toFloat()
            binding.sliderMax.value = d.max.toFloat()
            binding.tvCount.text = d.count.toString()
            binding.tvMin.text = d.min.toString()
            binding.tvMax.text = d.max.toString()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
