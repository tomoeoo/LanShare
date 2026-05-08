package com.example.lanshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import java.net.ServerSocket
import kotlin.concurrent.thread

class SignalingService : Service() {
    private var serverSocket: ServerSocket? = null
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            DeviceDiscovery.register(this@SignalingService, serverSocket?.localPort ?: 0)
            startTCPServer()
        }
        return START_STICKY
    }

    private suspend fun startTCPServer() {
        withContext(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(0)
                while (isActive) {
                    val client = serverSocket!!.accept()
                    thread { handleClient(client) }
                }
            } catch (e: Exception) { }
        }
    }

    private fun handleClient(client: java.net.Socket) {
        val reader = client.getInputStream().bufferedReader()
        val writer = client.getOutputStream().bufferedWriter()
        var remoteName = ""
        try {
            reader.forEachLine { line ->
                val msg = org.json.JSONObject(line)
                when (msg.getString("type")) {
                    "hello" -> {
                        remoteName = msg.getString("name")
                        writer.write("""{"type":"accepted"}""")
                        writer.newLine()
                        writer.flush()
                        // 通知WebRTCManager有新对等端
                        WebRTCManager.onPeerConnected(remoteName, client.inetAddress.hostAddress, client.port)
                    }
                    "offer", "answer", "candidate" -> {
                        WebRTCManager.onSignalMessage(msg)
                    }
                    "control" -> {
                        val cmd = msg.getJSONObject("command")
                        executeRemoteControl(cmd)
                    }
                }
            }
        } catch (e: Exception) { }
    }

    private fun executeRemoteControl(cmd: org.json.JSONObject) {
        // 通过无障碍服务执行手势
        ControlAccessibilityService.instance?.let { service ->
            when (cmd.getString("type")) {
                "mousemove" -> {
                    // Android 触摸：move 可转为悬停，或忽略
                }
                "mousedown" -> {
                    service.performTap(cmd.getDouble("x").toFloat(), cmd.getDouble("y").toFloat())
                }
                // 更多手势可扩展
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("lanshare", "LanShare Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, "lanshare")
            .setContentTitle("LanShare 运行中")
            .setContentText("等待连接...")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .build()
    }
}
