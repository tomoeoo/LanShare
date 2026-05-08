package com.example.lanshare

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {
    private val REQUEST_MEDIA_PROJECTION = 100
    private var isFirstRun = true
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)

        // 全局异常捕获，让错误直接显示在界面上
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val errorMsg = "崩溃：${e.message}\n${sw.toString()}"
            runOnUiThread {
                statusText.text = errorMsg
            }
            // 不退出，保持界面显示错误
        }

        val prefs = getSharedPreferences("LanShare", MODE_PRIVATE)
        isFirstRun = prefs.getBoolean("first_run", true)

        try {
            if (isFirstRun) {
                AlertDialog.Builder(this)
                    .setTitle("授权")
                    .setMessage("需要屏幕录制和辅助功能权限，一旦同意，局域网内设备可直接查看和控制您的屏幕。")
                    .setPositiveButton("授权") { _, _ ->
                        requestScreenCapture()
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        prefs.edit().putBoolean("first_run", false).apply()
                    }
                    .setNegativeButton("退出") { _, _ -> finish() }
                    .show()
            } else {
                startSignaling()
            }
        } catch (e: Exception) {
            statusText.text = "初始化异常：${e.message}"
        }

        findViewById<android.widget.Button>(R.id.start_btn).setOnClickListener {
            startSignaling()
        }
    }

    private fun requestScreenCapture() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, MediaProjectionService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startForegroundService(intent)
            Toast.makeText(this, "屏幕录制已启动", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSignaling() {
        startService(Intent(this, SignalingService::class.java))
    }
}
