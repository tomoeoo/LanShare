package com.example.lanshare

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {
    private val REQUEST_MEDIA_PROJECTION = 100
    private lateinit var statusText: TextView
    private lateinit var startBtn: Button
    private var mediaProjectionResultCode = 0
    private var mediaProjectionData: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        startBtn = findViewById(R.id.start_btn)

        // 全局异常捕获，尽量捕捉 Java 层崩溃
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val errorMsg = "崩溃：${e.message}\n${sw.toString()}"
            runOnUiThread {
                statusText.text = errorMsg
            }
        }

        val prefs = getSharedPreferences("LanShare", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("first_run", true)

        // 第一次运行只引导权限，不自动启动任何服务
        if (isFirstRun) {
            AlertDialog.Builder(this)
                .setTitle("授权")
                .setMessage("需要屏幕录制和辅助功能权限，同意后请手动点击“启动信令”。")
                .setPositiveButton("授权") { _, _ ->
                    requestScreenCapture()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    prefs.edit().putBoolean("first_run", false).apply()
                }
                .setNegativeButton("退出") { _, _ -> finish() }
                .show()
        }

        // 点击按钮才正式初始化并启动服务，避免自动启动导致闪退
        startBtn.setOnClickListener {
            startBtn.isEnabled = false
            statusText.text = "正在初始化 WebRTC..."
            try {
                // 在后台线程初始化，避免阻塞 UI
                Thread {
                    try {
                        WebRTCManager.initialize(this@MainActivity)
                        runOnUiThread {
                            statusText.text = "WebRTC 初始化成功，正在启动信令..."
                            startServices()
                        }
                    } catch (e: Exception) {
                        val sw = StringWriter()
                        e.printStackTrace(PrintWriter(sw))
                        runOnUiThread {
                            statusText.text = "初始化失败：${e.message}\n${sw.toString()}"
                            startBtn.isEnabled = true
                        }
                    }
                }.start()
            } catch (e: Exception) {
                statusText.text = "启动异常：${e.message}"
                startBtn.isEnabled = true
            }
        }
    }

    private fun requestScreenCapture() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK) {
            mediaProjectionResultCode = resultCode
            mediaProjectionData = data
            Toast.makeText(this, "屏幕录制权限已获取", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startServices() {
        // 先启动屏幕录制服务（只有获得权限后才有效）
        if (mediaProjectionData != null) {
            val intent = Intent(this, MediaProjectionService::class.java).apply {
                putExtra("resultCode", mediaProjectionResultCode)
                putExtra("data", mediaProjectionData)
            }
            startForegroundService(intent)
        }
        // 启动信令服务
        startService(Intent(this, SignalingService::class.java))
        statusText.text = "服务已启动，等待连接..."
    }
}
