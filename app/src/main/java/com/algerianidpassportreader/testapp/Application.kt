package com.algerianidpassportreader.testapp

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize the SDK asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = AlgerianIDPassportSDKManager.initialize(
                    context = this@MyApp,
                    token = ""  //enter your token here
                )
                Log.d("MyApp", "SDK initialized: $success")
            } catch (e: Exception) {
                Log.e("MyApp", "SDK initialization failed", e)
            }
        }
    }
}
