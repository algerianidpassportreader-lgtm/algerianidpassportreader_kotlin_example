package com.algerianidpassportreader.testapp

import com.yaakoubdjabidev.algerianidpassportreader.AlgerianIDPassportSDK
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import io.fotoapparat.preview.Frame
import org.jmrtd.lds.icao.MRZInfo
import com.yaakoubdjabidev.algerianidpassportreader.data.Passport

object AlgerianIDPassportSDKManager {

    private var sdk: AlgerianIDPassportSDK? = null

    fun getInstance(context: Context): AlgerianIDPassportSDK {
        if (sdk == null) {
            sdk = AlgerianIDPassportSDK(context)
        }
        return sdk!!
    }

    // Initialization with token (suspend)
    suspend fun initialize(context: Context, token: String) {
        getInstance(context).initialize(token)
    }

    fun dispose() {
        sdk?.dispose()
        sdk = null
    }

    fun getVersion(): String {
        return AlgerianIDPassportSDK.getVersion()
    }

    fun isNFCSupported(context: Context): Boolean {
        return getInstance(context).isNFCSupported()
    }

    fun isNFCEnabled(context: Context): Boolean {
        return getInstance(context).isNFCEnabled()
    }

    fun setMRZInfoForNFC(context: Context, mrzInfo: MRZInfo) {
        getInstance(context).setMRZInfoForNFC(mrzInfo)
    }

    fun cleanMRZString(context: Context, mrz: String): String {
        return getInstance(context).cleanMRZString(mrz)
    }

    fun isValidMRZFormat(context: Context, text: String): Boolean {
        return getInstance(context).isValidMRZFormat(text)
    }

    fun validateMRZData(context: Context, mrzInfo: MRZInfo): Boolean {
        return getInstance(context).validateMRZData(mrzInfo)
    }

    fun getDocumentType(context: Context, mrzInfo: MRZInfo): String {
        return getInstance(context).getDocumentType(mrzInfo)
    }

    fun extractPersonalInfo(context: Context, mrzInfo: MRZInfo): Map<String, String> {
        return getInstance(context).extractPersonalInfo(mrzInfo)
    }

    suspend fun processMRZLines(
        context: Context,
        line1: String,
        line2: String,
        line3: String? = null
    ): MRZInfo? {
        var result: MRZInfo? = null
        getInstance(context).processMRZLines(line1, line2, line3, object : AlgerianIDPassportSDK.MRZDetectionCallback {
            override fun onMRZDetected(mrzInfo: MRZInfo) {
                result = mrzInfo
            }

            override fun onMRZDetectionFailed(error: String) {}
            override fun onMRZDetectionError(exception: Exception) {}
        })
        return result
    }

    // =====================================
    // OCR functions
    // =====================================

    fun detectTextFromBitmap(
        context: Context,
        bitmap: Bitmap,
        callback: AlgerianIDPassportSDK.TextDetectionCallback
    ) {
        getInstance(context).detectOCRTextFromBitmap(bitmap, callback)
    }

    fun detectTextFromFrame(
        context: Context,
        frame: Frame,
        rotation: Int,
        callback: AlgerianIDPassportSDK.MRZDetectionCallback
    ) {
        getInstance(context).detectTextFromFrame(frame, rotation, callback)
    }

    fun detectTextFromByteData(
        context: Context,
        byteData: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        callback: AlgerianIDPassportSDK.MRZDetectionCallback
    ) {
        getInstance(context).detectTextFromByteData(byteData, width, height, rotation, callback)
    }

    fun detectMRZFromBitmap(
        context: Context,
        bitmap: Bitmap,
        callback: (MRZInfo?, String?) -> Unit
    ) {
        getInstance(context).detectMRZFromBitmap(bitmap, callback)
    }

    // =====================================
    // NFC functions
    // =====================================
    var onNFCSessionStart: (() -> Unit)? = null
    var onNFCSessionFinish: (() -> Unit)? = null
    var onPassportDataRead: ((Map<String, Any>) -> Unit)? = null
    var onNFCError: ((String) -> Unit)? = null
    var onAccessDenied: ((String) -> Unit)? = null
    var onBACDenied: ((String) -> Unit)? = null
    var onPACEError: ((String) -> Unit)? = null
    var onCardError: ((String) -> Unit)? = null


    fun handleNFCTag(context: Context, intent: Intent): Boolean {
        return getInstance(context).handleNFCTag(intent, object : AlgerianIDPassportSDK.NFCReadCallback {

            override fun onNFCSessionStart() {
                onNFCSessionStart?.invoke()
            }

            override fun onNFCSessionFinish() {
                onNFCSessionFinish?.invoke()
            }

            override fun onPassportDataRead(passport: Passport?) {
                // Get basic data from SDK
                val baseData = getInstance(context).extractCompletePersonalInfo(passport)

                // Add actual image data
                val finalData = baseData.toMutableMap()
                addImageData(passport, finalData)

                onPassportDataRead?.invoke(finalData)
            }

            override fun onNFCError(error: Exception) {
                onNFCError?.invoke(error.message ?: "Unknown NFC error")
            }

            override fun onAccessDenied(exception: Exception) {
                onAccessDenied?.invoke(exception.message ?: "Access Denied")
            }

            override fun onBACDenied(exception: Exception) {
                onBACDenied?.invoke(exception.message ?: "BAC Denied")
            }

            override fun onPACEError(exception: Exception) {
                onPACEError?.invoke(exception.message ?: "PACE Error")
            }

            override fun onCardError(exception: Exception) {
                onCardError?.invoke(exception.message ?: "Card Error")
            }
        })
    }

    // NEW FUNCTION: Extract actual image data from Passport object
    private fun addImageData(passport: Passport?, data: MutableMap<String, Any>) {
        if (passport == null) return

        // Helper to extract image regardless of type
        fun extractImage(value: Any?): String? {
            return when (value) {
                is Bitmap -> {
                    val outputStream = java.io.ByteArrayOutputStream()
                    value.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
                }
                is ByteArray -> {
                    android.util.Base64.encodeToString(value, android.util.Base64.DEFAULT)
                }
                else -> null
            }
        }

        try {
            val faceField = passport::class.java.getDeclaredField("face")
            faceField.isAccessible = true
            val faceValue = faceField.get(passport)

            val faceBase64 = extractImage(faceValue)
            if (faceBase64 != null) {
                data["faceImage"] = faceBase64
            }
        } catch (e: Exception) { Log.e("SDK", "Face extract failed", e) }

        try {
            val sigField = passport::class.java.getDeclaredField("signature")
            sigField.isAccessible = true
            val sigBase64 = extractImage(sigField.get(passport))
            if (sigBase64 != null) {
                data["signatureImage"] = sigBase64
            }
        } catch (e: Exception) { Log.e("SDK", "Signature extract failed", e) }
    }
    /*    private fun addImageData(passport: Passport?, data: MutableMap<String, Any>) {
        if (passport == null) {
            println("addImageData: Passport is null")
            return
        }

        println("=== addImageData START ===")

        // SIMPLE CHECK: What fields does passport have?
        println("Passport class: ${passport::class.java.name}")

        // Try to check face field
        try {
            val faceField = passport::class.java.getDeclaredField("face")
            faceField.isAccessible = true
            val faceValue = faceField.get(passport)
            println("1. passport.face: ${faceValue?.let { it::class.java.simpleName } ?: "null"}")

            if (faceValue is ByteArray) {
                println("   ByteArray size: ${faceValue.size}")
                if (faceValue.isNotEmpty()) {
                    val base64 = android.util.Base64.encodeToString(faceValue, android.util.Base64.DEFAULT)
                    data["faceImage"] = base64
                    println("   ✓ Added face image: ${faceValue.size} bytes")
                }
            }
        } catch (e: Exception) {
            println("1. No passport.face: ${e.message}")
        }

        // Try portrait field
        try {
            val portraitField = passport::class.java.getDeclaredField("portrait")
            portraitField.isAccessible = true
            val portraitValue = portraitField.get(passport)
            println("2. passport.portrait: ${portraitValue?.let { it::class.java.simpleName } ?: "null"}")

            if (portraitValue is ByteArray) {
                println("   ByteArray size: ${portraitValue.size}")
                if (portraitValue.isNotEmpty() && !data.containsKey("faceImage")) {
                    val base64 = android.util.Base64.encodeToString(portraitValue, android.util.Base64.DEFAULT)
                    data["faceImage"] = base64
                    println("   ✓ Added portrait as face image: ${portraitValue.size} bytes")
                }
            }
        } catch (e: Exception) {
            println("2. No passport.portrait: ${e.message}")
        }

        // Try signature field
        try {
            val signatureField = passport::class.java.getDeclaredField("signature")
            signatureField.isAccessible = true
            val signatureValue = signatureField.get(passport)
            println("3. passport.signature: ${signatureValue?.let { it::class.java.simpleName } ?: "null"}")

            if (signatureValue is ByteArray) {
                println("   ByteArray size: ${signatureValue.size}")
                if (signatureValue.isNotEmpty()) {
                    val base64 = android.util.Base64.encodeToString(signatureValue, android.util.Base64.DEFAULT)
                    data["signatureImage"] = base64
                    println("   ✓ Added signature image: ${signatureValue.size} bytes")
                }
            }
        } catch (e: Exception) {
            println("3. No passport.signature: ${e.message}")
        }

        println("=== addImageData END ===")
        println("Added image keys: ${data.keys.filter { it.contains("Image", true) }}")
    }
*/
    fun isOCRAvailable(): Boolean {

        return AlgerianIDPassportSDK.checkDependencies()
    }

    private fun throwNotInitialized(): Nothing {
        throw IllegalStateException("SDK not initialized. Call initialize() first.")
    }
}
