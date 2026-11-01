package com.mscsi.photto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mscsi.photto.data.PixelCalculator
import com.mscsi.photto.data.PixelResult
import com.mscsi.photto.data.SettingsManager
import com.mscsi.photto.databinding.ActivityMainBinding
import com.mscsi.photto.databinding.ItemNumberBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnCapture.setOnClickListener { takePhoto() }

        binding.resultContainer.visibility = View.GONE

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        // Ayarlar ekranından dönülünce placeholder'ı güncelle
        updatePlaceholder()
    }

    private fun updatePlaceholder() {
        val s = SettingsManager.load(this)
        binding.tvPlaceholder.text = getString(
            R.string.placeholder_format,
            s.count, s.min, s.max
        )
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Kamera başlatılamadı", e)
                Toast.makeText(this, R.string.camera_init_failed, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: run {
            Toast.makeText(this, R.string.camera_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnCapture.isEnabled = false
        binding.resultContainer.visibility = View.GONE

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()
                        val rotated = rotateBitmap(bitmap, image.imageInfo.rotationDegrees.toFloat())
                        processImage(rotated)
                    } catch (e: Exception) {
                        Log.e(TAG, "Görüntü işlenemedi", e)
                        showError(getString(R.string.capture_failed))
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Çekim hatası", exception)
                    showError(getString(R.string.capture_failed))
                }
            }
        )
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnCapture.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Çok büyük görselleri performans için küçültür. */
    private fun downsample(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val largest = maxOf(w, h)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }

    private fun processImage(bitmap: Bitmap) {
        val settings = SettingsManager.load(this)
        val scaled = downsample(bitmap, 1024)

        lifecycleScope.launch {
            val result: PixelResult = withContext(Dispatchers.Default) {
                PixelCalculator.calculate(scaled, settings.count, settings.min, settings.max)
            }
            displayResult(result)
            binding.progressBar.visibility = View.GONE
            binding.btnCapture.isEnabled = true
        }
    }

    private fun displayResult(result: PixelResult) {
        binding.tvPlaceholder.visibility = View.GONE
        binding.resultContainer.visibility = View.VISIBLE
        binding.numbersContainer.removeAllViews()

        result.numbers.forEachIndexed { i, value ->
            val item = ItemNumberBinding.inflate(layoutInflater, binding.numbersContainer, false)
            item.tvNumber.text = value.toString()
            item.tvLabel.text = result.labels[i]
            binding.numbersContainer.addView(item.root)
        }

        // Detaylı istatistik (ham metrikler)
        binding.tvStats.text = getString(
            R.string.stats_format,
            result.raw.red,
            result.raw.green,
            result.raw.blue,
            result.raw.brightness,
            (result.raw.saturation * 100) / 255,   // 0..255 -> %0..100
            result.raw.contrast
        )

        // Kullanılan aralık bilgisini güncelle
        val s = SettingsManager.load(this)
        binding.tvRangeInfo.text = getString(R.string.range_info_format, s.min, s.max, s.count)
    }

    override fun onDestroy() {
        super.onDestroy()
        // CameraX lifecycle'a bağlı olduğundan ek kapatma gerekmez.
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
