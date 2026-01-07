package com.algerianidpassportreader.testapp

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.yaakoubdjabidev.algerianidpassportreader.AlgerianIDPassportSDK
import java.io.InputStream
import org.jmrtd.lds.icao.MRZInfo

class MrzLocalActivity : AppCompatActivity() {

    private lateinit var galleryButton: Button
    private lateinit var cameraButton: Button
    private lateinit var detectMrzButton: Button
    private lateinit var detectOcrButton: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var imagePreview: ImageView
    private lateinit var nfcButton: Button
    private lateinit var resultsContainer: LinearLayout

    private var selectedImageBytes: ByteArray? = null

    private var mrzInfoModel: MrzInfoModel? = null
    private var mrzInfo: MRZInfo? = null

    private var textDetected: String? = null
    private var detectionType: String = "mrz" // or "ocr"

    private val pickGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handleImageUri(it, "Gallery") }
        }

    private val pickCameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                val stream = java.io.ByteArrayOutputStream()
                it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                selectedImageBytes = stream.toByteArray()
                updateImagePreview("Camera")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mrz_local)

        galleryButton = findViewById(R.id.galleryButton)
        cameraButton = findViewById(R.id.cameraButton)
        detectMrzButton = findViewById(R.id.detectMrzButton)
        detectOcrButton = findViewById(R.id.detectOcrButton)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        imagePreview = findViewById(R.id.imagePreview)
        nfcButton = findViewById(R.id.nfcButton)
        resultsContainer = findViewById(R.id.resultsContainer)

        galleryButton.setOnClickListener { pickGalleryLauncher.launch("image/*") }
        cameraButton.setOnClickListener { pickCameraLauncher.launch(null) }

        detectMrzButton.setOnClickListener { runMRZDetection() }
        detectOcrButton.setOnClickListener { runOCRDetection() }

        nfcButton.setOnClickListener {
            mrzInfo?.let {
                val intent = Intent(this, NfcReaderActivity::class.java)
                intent.putExtra("mrzInfo", it)   // now it works!
                startActivity(intent)
            }
        }
    }

    private fun handleImageUri(uri: Uri, source: String) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            inputStream?.let {
                selectedImageBytes = it.readBytes()
                updateImagePreview(source)
            }
        } catch (e: Exception) {
            statusText.text = "Error: ${e.message}"
        }
    }

    private fun updateImagePreview(source: String) {
        selectedImageBytes?.let { bytes ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            imagePreview.setImageBitmap(bitmap)
            statusText.text = "Selected from $source"
            resultsContainer.removeAllViews()
            nfcButton.visibility = View.GONE
        }
    }

    private fun runMRZDetection() {
        if (selectedImageBytes == null) {
            statusText.text = "Please select an image first"
            return
        }

        progressBar.visibility = View.VISIBLE
        detectionType = "mrz"
        statusText.text = "Running MRZ detection..."

        val bitmap = BitmapFactory.decodeByteArray(selectedImageBytes, 0, selectedImageBytes!!.size)

        AlgerianIDPassportSDKManager.detectMRZFromBitmap(
            this,
            bitmap
        ) { mrzInfo, error ->
            if (mrzInfo != null) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    this.mrzInfo = mrzInfo  // save REAL MRZInfo
                    mrzInfoModel = MrzInfoModel.fromJson(
                        AlgerianIDPassportSDKManager.extractPersonalInfo(this@MrzLocalActivity, mrzInfo)
                    )
                    textDetected = null
                    statusText.text = "MRZ Detection Successful"
                    displayMRZResults()
                    nfcButton.visibility = View.VISIBLE
                }
            } else {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusText.text = "MRZ Detection Failed: $error"
                }
            }
        }

    }

    private fun runOCRDetection() {
        if (selectedImageBytes == null) {
            statusText.text = "Please select an image first"
            return
        }

        progressBar.visibility = View.VISIBLE
        detectionType = "ocr"
        statusText.text = "Running OCR detection..."

        // Decode bitmap
        val bitmap = BitmapFactory.decodeByteArray(selectedImageBytes, 0, selectedImageBytes!!.size)

        // Use the SDK manager with proper callback
        AlgerianIDPassportSDKManager.detectTextFromBitmap(this, bitmap,
            object : AlgerianIDPassportSDK.TextDetectionCallback {
            override fun onTextDetected(text: String) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    textDetected = text
                    mrzInfoModel = null
                    statusText.text = "OCR Detection Successful"
                    displayOCRResults()
                }
            }

            override fun onTextDetectionFailed(error: String) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusText.text = "OCR Detection Failed: $error"
                }
            }

            override fun onTextDetectionError(exception: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusText.text = "OCR Detection Error: ${exception.message}"
                }
            }
        })
    }

    private fun displayMRZResults() {
        resultsContainer.removeAllViews()
        mrzInfoModel?.let { mrz ->
            val map = mapOf(
                "Document Number" to mrz.documentNumber,
                "Birth Date" to mrz.dateOfBirth,
                "Expiry Date" to mrz.dateOfExpiry,
                "Nationality" to mrz.nationality,
                "Gender" to mrz.gender,
                "Issuing State" to mrz.issuingState,
                "Document Code" to mrz.documentCode,
                "Primary Identifier" to mrz.primaryIdentifier,
                "Secondary Identifier" to mrz.secondaryIdentifier
            )

            map.forEach { (label, value) ->
                val tv = TextView(this)
                tv.text = "$label: $value"
                tv.setPadding(0, 8, 0, 8)
                resultsContainer.addView(tv)
            }
        }
    }

    private fun displayOCRResults() {
        resultsContainer.removeAllViews()
        textDetected?.let { text ->
            val tvLabel = TextView(this)
            tvLabel.text = "Detected Text:"
            tvLabel.setPadding(0, 0, 0, 8)
            resultsContainer.addView(tvLabel)

            val tvText = TextView(this)
            tvText.text = text
            tvText.setPadding(0, 0, 0, 8)
            resultsContainer.addView(tvText)

            val tvLength = TextView(this)
            tvLength.text = "Text Length: ${text.length} characters"
            resultsContainer.addView(tvLength)
        }
    }
}
