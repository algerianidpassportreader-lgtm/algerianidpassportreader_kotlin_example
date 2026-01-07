package com.algerianidpassportreader.testapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import androidx.core.content.ContextCompat

class MRZInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mrz_info)

        supportActionBar?.title = "MRZ"
        supportActionBar?.setBackgroundDrawable(getDrawable(R.color.primary_color))
        supportActionBar?.elevation = 4f

        val btnScanMrz: Button = findViewById(R.id.btnScanMrz)

        btnScanMrz.setOnClickListener {
            val intent = Intent(this, MRZScreenActivity::class.java)
            startActivity(intent)
        }
    }
}
