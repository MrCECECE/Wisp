package com.wisp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<TextView>(R.id.tvVersionValue).text =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }
}
