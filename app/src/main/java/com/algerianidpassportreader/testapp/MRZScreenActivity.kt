package com.algerianidpassportreader.testapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yaakoubdjabidev.algerianidpassportreader.AlgerianIDPassportSDK
import io.fotoapparat.preview.Frame
import org.jmrtd.lds.icao.MRZInfo
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import io.fotoapparat.parameter.Resolution
import android.content.Intent

class MRZScreenActivity : AppCompatActivity() {

    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var statusText: TextView
    private lateinit var statusIcon: ImageView
    private var isProcessing = false
    private var isMRZDetected = false

    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mrz_screen)

        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        statusIcon = findViewById(R.id.statusIcon)

        val galleryButton = findViewById<ImageView>(R.id.galleryButton)
        galleryButton.setOnClickListener {
            val intent = Intent(this, MrzLocalActivity::class.java)
            startActivity(intent)
            finish()
        }


        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check permission before starting camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                statusText.text = "Camera permission is required."
            }
        }
    }

    fun yuv420ToNV21(image: ImageProxy): ByteArray {
        val ySize = image.planes[0].buffer.remaining()
        val uSize = image.planes[1].buffer.remaining()
        val vSize = image.planes[2].buffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y
        image.planes[0].buffer.get(nv21, 0, ySize)

        val uvPixelStride = image.planes[1].pixelStride
        val uvRowStride = image.planes[1].rowStride
        val width = image.width
        val height = image.height

        var offset = ySize
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val uIndex = row * uvRowStride + col * uvPixelStride
                val vIndex = row * uvRowStride + col * uvPixelStride
                nv21[offset++] = vBuffer.get(vIndex)
                nv21[offset++] = uBuffer.get(uIndex)
            }
        }

        return nv21
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                statusText.text = "Camera initialization failed: ${exc.message}"
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun processFrame(imageProxy: ImageProxy) {
        if (isProcessing || isMRZDetected) {
            imageProxy.close()
            return
        }
        isProcessing = true

        val nv21Bytes = yuv420ToNV21(imageProxy)
        val resolution = Resolution(imageProxy.width, imageProxy.height)
        val frame = Frame(resolution, nv21Bytes, imageProxy.imageInfo.rotationDegrees)



        // Call the SDK
        try {
            AlgerianIDPassportSDKManager.detectTextFromFrame(
                context = this,
                frame = frame,
                rotation = imageProxy.imageInfo.rotationDegrees,
                callback = object : AlgerianIDPassportSDK.MRZDetectionCallback {
                    override fun onMRZDetected(mrzInfo: MRZInfo) {
                        isMRZDetected = true
                        runOnUiThread {
                            statusText.text = "MRZ Detected!"
                            statusIcon.setImageResource(android.R.drawable.checkbox_on_background)
                            // TODO: navigate to NFC screen
                            // Navigate to NFC screen
                            val intent = Intent(this@MRZScreenActivity, NfcReaderActivity::class.java)
                            intent.putExtra("mrzInfo", mrzInfo) // pass your MRZInfo
                            startActivity(intent)
                            finish() // optional, like Flutter pushReplacement




                        }
                        imageProxy.close()
                        isProcessing = false
                    }

                    override fun onMRZDetectionFailed(error: String) {
                        runOnUiThread {
                            statusText.text = "MRZ detection failed: $error"
                        }
                        imageProxy.close()
                        isProcessing = false
                    }

                    override fun onMRZDetectionError(exception: Exception) {
                        runOnUiThread {
                            statusText.text = "Error detecting MRZ: ${exception.message}"
                        }
                        imageProxy.close()
                        isProcessing = false
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            imageProxy.close()
            isProcessing = false
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
