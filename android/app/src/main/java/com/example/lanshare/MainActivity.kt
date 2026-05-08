package com.example.lanshare

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val REQUEST_MEDIA_PROJECTION = 100
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var isFirstRun = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("LanShare", MODE_PRIVATE)
        isFirstRun = prefs.getBoolean("first_run", true)

        if (isFirstRun) {
            AlertDialog.Builder(this)
                .setTitle("授权")
                .setMessage("需要屏幕录制和辅助功能权限，一旦同意，局域网内设备可直接查看和控制您的屏幕。")
                .setPositiveButton("授权") { _, _ ->
                    requestScreenCapture()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    prefs.edit().putBoolean("first_run", false).apply()
                }
                .setNegativeButton("退出") { finish() }
                .show()
        } else {
            // 自动启动信令服务
            startSignaling()
        }

        findViewById<Button>(R.id.start_btn).setOnClickListener {
            startSignaling()
        }
    }

    private fun requestScreenCapture() {
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
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
