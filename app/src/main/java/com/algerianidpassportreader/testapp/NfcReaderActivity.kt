package com.algerianidpassportreader.testapp

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.jmrtd.lds.icao.MRZInfo

class NfcReaderActivity : AppCompatActivity() {

    private lateinit var txtSdkVersion: TextView
    private lateinit var txtNfcStatus: TextView
    private lateinit var btnPrepare: Button
    private lateinit var btnClear: Button
    private lateinit var imgFace: ImageView
    private lateinit var imgSignature: ImageView
    private lateinit var layoutPassportData: LinearLayout

    private var passportData: Map<String, Any>? = null
    private var mrzInfo: MRZInfo? = null
    private var isNfcPrepared = false

    // NFC variables
    private var nfcAdapter: NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null

    companion object {
        private const val TAG = "NfcReaderActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_reader)

        setupViews()
        setupCallbacks()

        // Initialize NFC
        initNfc()

        mrzInfo = intent.getSerializableExtra("mrzInfo") as? MRZInfo

        checkSDK()

        // Handle NFC intent if activity was started by NFC tag
        handleIntent(intent)
    }

    private fun initNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            txtNfcStatus.text = "NFC is not available on this device"
            btnPrepare.isEnabled = false
            return
        }

        // Create PendingIntent for NFC foreground dispatch
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        nfcPendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called with action: ${intent.action}")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        Log.d(TAG, "handleIntent - Action: $action")

        if (NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == action) {

            if (isNfcPrepared) {
                runOnUiThread {
                    txtNfcStatus.text = "Reading NFC tag..."
                    Toast.makeText(this, "Reading ID card...", Toast.LENGTH_SHORT).show()
                }

                // Process the NFC tag
                val handled = AlgerianIDPassportSDKManager.handleNFCTag(this, intent)
                Log.d(TAG, "handleNFCTag returned: $handled")

                if (!handled) {
                    runOnUiThread {
                        txtNfcStatus.text = "Failed to handle NFC tag"
                        Toast.makeText(this, "Failed to read NFC tag", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                runOnUiThread {
                    txtNfcStatus.text = "NFC not prepared. Tap 'Prepare NFC' first."
                    Toast.makeText(this, "Please prepare NFC first", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupViews() {
        txtSdkVersion = findViewById(R.id.txtSdkVersion)
        txtNfcStatus = findViewById(R.id.txtNfcStatus)
        btnPrepare = findViewById(R.id.btnPrepare)
        btnClear = findViewById(R.id.btnClear)
        imgFace = findViewById(R.id.imgFace)
        imgSignature = findViewById(R.id.imgSignature)
        layoutPassportData = findViewById(R.id.layoutPassportData)

        btnPrepare.setOnClickListener { prepareNFC() }
        btnClear.setOnClickListener { clearData() }
    }

    private fun setupCallbacks() {
        AlgerianIDPassportSDKManager.onNFCSessionStart = {
            runOnUiThread {
                txtNfcStatus.text = "NFC Session Started..."
                Toast.makeText(this@NfcReaderActivity, "NFC Session Started", Toast.LENGTH_SHORT).show()
            }
        }

        AlgerianIDPassportSDKManager.onNFCSessionFinish = {
            runOnUiThread {
                txtNfcStatus.text = "NFC Session Finished"
                Toast.makeText(this@NfcReaderActivity, "NFC Session Finished", Toast.LENGTH_SHORT).show()
            }
        }

        AlgerianIDPassportSDKManager.onPassportDataRead = { data ->
            runOnUiThread {
                txtNfcStatus.text = "Passport Data Read Successfully!"
                passportData = data

                // DEBUG: Log what we received
                Log.d(TAG, "=== NFC DATA RECEIVED ===")
                for ((key, value) in data) {
                    if (key.contains("Image")) {
                        val strVal = value.toString()
                        Log.d(TAG, "$key: [Length=${strVal.length}, First 30 chars: ${strVal.take(30)}...]")
                    } else {
                        Log.d(TAG, "$key: $value")
                    }
                }
                Log.d(TAG, "=== END DATA ===")

                showPassportData(data)

                // Show success message
                Toast.makeText(this, "Data read successfully!", Toast.LENGTH_SHORT).show()
            }
        }

        AlgerianIDPassportSDKManager.onNFCError = { error ->
            runOnUiThread {
                txtNfcStatus.text = "NFC Error: $error"
                Toast.makeText(this, "NFC Error: $error", Toast.LENGTH_LONG).show()
            }
        }

        AlgerianIDPassportSDKManager.onAccessDenied = { error ->
            runOnUiThread {
                txtNfcStatus.text = "Access Denied: $error"
                Toast.makeText(this, "Access Denied", Toast.LENGTH_SHORT).show()
            }
        }

        AlgerianIDPassportSDKManager.onBACDenied = { error ->
            runOnUiThread {
                txtNfcStatus.text = "BAC Denied: $error"
                Toast.makeText(this, "BAC Denied", Toast.LENGTH_SHORT).show()
            }
        }

        AlgerianIDPassportSDKManager.onPACEError = { error ->
            runOnUiThread {
                txtNfcStatus.text = "PACE Error: $error"
                Toast.makeText(this, "PACE Error", Toast.LENGTH_SHORT).show()
            }
        }

        AlgerianIDPassportSDKManager.onCardError = { error ->
            runOnUiThread {
                txtNfcStatus.text = "Card Error: $error"
                Toast.makeText(this, "Card Error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkSDK() {
        val version = AlgerianIDPassportSDKManager.getVersion()
        val supported = AlgerianIDPassportSDKManager.isNFCSupported(this)
        val enabled = AlgerianIDPassportSDKManager.isNFCEnabled(this)

        txtSdkVersion.text = "SDK Version: $version\nSupported: $supported\nEnabled: $enabled"

        if (!supported) {
            txtNfcStatus.text = "NFC not supported on this device"
            btnPrepare.isEnabled = false
        } else if (!enabled) {
            txtNfcStatus.text = "Please enable NFC in settings"
            btnPrepare.isEnabled = false
        }
    }

    private fun prepareNFC() {
        if (mrzInfo == null) {
            Toast.makeText(this, "Missing MRZ info", Toast.LENGTH_SHORT).show()
            return
        }

        // Set MRZ info
        AlgerianIDPassportSDKManager.setMRZInfoForNFC(this, mrzInfo!!)
        isNfcPrepared = true
        txtNfcStatus.text = "Ready — Tap your ID card on NFC reader"
        Toast.makeText(this, "NFC ready for reading. Hold your ID card near the NFC reader.", Toast.LENGTH_LONG).show()
    }

    private fun clearData() {
        passportData = null
        isNfcPrepared = false
        imgFace.setImageBitmap(null)
        imgSignature.setImageBitmap(null)
        layoutPassportData.removeAllViews()
        txtNfcStatus.text = "Ready"
        Toast.makeText(this, "Data cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        disableNfcForegroundDispatch()
    }

    private fun enableNfcForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            try {
                // Create intent filter for all NFC actions
                val intentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED).apply {
                    try {
                        addDataType("*/*")
                    } catch (e: IntentFilter.MalformedMimeTypeException) {
                        throw RuntimeException("Failed to add MIME type", e)
                    }
                }

                intentFilter.addAction(NfcAdapter.ACTION_TAG_DISCOVERED)
                intentFilter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)

                // Tech list for IsoDep (used by e-passports)
                val techLists = arrayOf(arrayOf(IsoDep::class.java.name))

                // Enable foreground dispatch
                adapter.enableForegroundDispatch(
                    this,
                    nfcPendingIntent,
                    arrayOf(intentFilter),
                    techLists
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error enabling NFC: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun disableNfcForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            try {
                adapter.disableForegroundDispatch(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showPassportData(data: Map<String, Any>) {
        layoutPassportData.removeAllViews()
// 1. Handle Face
        val faceBase64 = data["faceImage"] as? String
        if (!faceBase64.isNullOrEmpty()) {
            val bitmap = decodeImage(faceBase64)
            imgFace.setImageBitmap(bitmap)
        } else {
            imgFace.setImageResource(android.R.drawable.ic_menu_report_image)
        }

        // 2. Handle Signature
        val sigBase64 = data["signatureImage"] as? String
        if (!sigBase64.isNullOrEmpty()) {
            val bitmap = decodeImage(sigBase64)
            imgSignature.setImageBitmap(bitmap)
        }
/*        // FACE IMAGE - Use the simple decode function
        val faceData = data["faceImage"]?.toString()
        val faceBitmap = decodeImage(faceData)
        if (faceBitmap != null) {
            Log.d(TAG, "Face image decoded successfully: ${faceBitmap.width}x${faceBitmap.height}")
            imgFace.setImageBitmap(faceBitmap)
        } else {
            Log.d(TAG, "Failed to decode face image. Data was: ${faceData?.take(50)}...")
            // Use a simple placeholder
            imgFace.setImageResource(android.R.drawable.ic_menu_report_image)
        }

        // SIGNATURE IMAGE - Use the simple decode function
        val signatureData = data["signatureImage"]?.toString()
        val signatureBitmap = decodeImage(signatureData)
        if (signatureBitmap != null) {
            Log.d(TAG, "Signature image decoded successfully: ${signatureBitmap.width}x${signatureBitmap.height}")
            imgSignature.setImageBitmap(signatureBitmap)
        } else {
            Log.d(TAG, "Failed to decode signature image. Data was: ${signatureData?.take(50)}...")
            // Use a simple placeholder
            imgSignature.setImageResource(android.R.drawable.ic_menu_edit)
        }
*/
        // TEXT DATA - Filter out image data
        for ((key, value) in data) {
            if (key != "faceImage" && key != "signatureImage") {
                addTextItem(key, value.toString())
            }
        }

        // Add image availability info if present
        data["hasFaceImage"]?.let { addTextItem("hasFaceImage", it.toString()) }
        data["hasSignature"]?.let { addTextItem("hasSignature", it.toString()) }
    }

    private fun decodeImage(base64String: String?): Bitmap? {
        if (base64String == null || base64String.isEmpty()) {
            Log.d(TAG, "decodeImage: Input string is null or empty")
            return null
        }

        return try {
            // 1. Remove any whitespace and line breaks
            var cleanString = base64String.trim()
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")

            Log.d(TAG, "decodeImage: After cleanup, length=${cleanString.length}")

            // 2. Handle data URI format (data:image/jpeg;base64,...)
            if (cleanString.startsWith("data:")) {
                val commaIndex = cleanString.indexOf(',')
                if (commaIndex != -1) {
                    cleanString = cleanString.substring(commaIndex + 1)
                    Log.d(TAG, "decodeImage: Removed data URI prefix, new length=${cleanString.length}")
                }
            }

            // 3. Add padding if needed (Base64 should be multiple of 4)
            val padding = cleanString.length % 4
            if (padding > 0) {
                cleanString += "====".substring(padding)
                Log.d(TAG, "decodeImage: Added padding, new length=${cleanString.length}")
            }

            // 4. Try different Base64 decoding modes
            var bytes: ByteArray? = null
            var bitmap: Bitmap? = null

            // Try DEFAULT first
            try {
                bytes = Base64.decode(cleanString, Base64.DEFAULT)
                Log.d(TAG, "decodeImage: Decoded with DEFAULT, bytes=${bytes?.size}")
            } catch (e: Exception) {
                Log.d(TAG, "decodeImage: DEFAULT decode failed, trying NO_WRAP")
                try {
                    bytes = Base64.decode(cleanString, Base64.NO_WRAP)
                    Log.d(TAG, "decodeImage: Decoded with NO_WRAP, bytes=${bytes?.size}")
                } catch (e: Exception) {
                    Log.d(TAG, "decodeImage: NO_WRAP decode failed, trying URL_SAFE")
                    bytes = Base64.decode(cleanString, Base64.URL_SAFE)
                    Log.d(TAG, "decodeImage: Decoded with URL_SAFE, bytes=${bytes?.size}")
                }
            }

            if (bytes != null && bytes.isNotEmpty()) {
                // 5. Try decoding with options for better compatibility
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }

                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                if (bitmap == null) {
                    Log.d(TAG, "decodeImage: BitmapFactory returned null")
                    // Try alternative decoding
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }

                if (bitmap != null) {
                    Log.d(TAG, "decodeImage: Success! Bitmap: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.d(TAG, "decodeImage: All decoding attempts failed")
                }
            } else {
                Log.d(TAG, "decodeImage: No bytes to decode")
            }

            bitmap

        } catch (e: Exception) {
            Log.e(TAG, "decodeImage error: ${e.message}", e)
            Log.e(TAG, "decodeImage stacktrace:", e)
            null
        }
    }
    private fun addTextItem(title: String, value: String) {
        val tv = TextView(this)
        tv.text = "$title: $value"
        tv.textSize = 14f
        tv.setPadding(0, 8, 0, 8)
        layoutPassportData.addView(tv)
    }
}