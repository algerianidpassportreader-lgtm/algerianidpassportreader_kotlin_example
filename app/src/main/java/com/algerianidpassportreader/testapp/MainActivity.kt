package com.algerianidpassportreader.testapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SDK initialized successfully, start the MRZInfoActivity

        // Initialize the SDK before starting the next activity
        mainScope.launch {
            try {
                 AlgerianIDPassportSDKManager.initialize(
                    context = this@MainActivity,
                    token = "" // Add your token here
                )
                // Log after initialization
                android.util.Log.d("SDK_INIT", "SDK successfully initialized with token")

                // SDK initialized successfully, start the MRZInfoActivity
                    startActivity(Intent(this@MainActivity, MRZInfoActivity::class.java))
                    finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    "Error initializing SDK: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}
