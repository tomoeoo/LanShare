package com.example.lanshare

import android.app.Service
import android.content.Intent
import android.os.IBinder

class MediaProjectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode != -1 && data != null) {
            WebRTCManager.startScreenCapture(this, resultCode, data)
        }
        return START_STICKY
    }
}
