package com.example.lanshare

import android.content.Context
import kotlinx.coroutines.*
import org.jmdns.JmDNS
import org.jmdns.ServiceInfo
import java.net.InetAddress

object DeviceDiscovery {
    private var jmdns: JmDNS? = null
    val onlineDevices = mutableMapOf<String, Pair<String, Int>>()

    suspend fun register(context: Context, port: Int) {
        withContext(Dispatchers.IO) {
            try {
                jmdns = JmDNS.create(InetAddress.getLocalHost())
                val service = ServiceInfo.create(
                    "_lanshare._tcp.local.",
                    android.os.Build.MODEL,
                    port,
                    "Android LanShare"
                )
                jmdns!!.registerService(service)
            } catch (e: Exception) { }
        }
    }

    fun discover(callback: (Map<String, Pair<String, Int>>) -> Unit) {
        jmdns?.addServiceListener("_lanshare._tcp.local.", object : org.jmdns.ServiceListener {
            override fun serviceAdded(event: org.jmdns.ServiceEvent) {}
            override fun serviceRemoved(event: org.jmdns.ServiceEvent) {
                onlineDevices.remove(event.name)
                callback(onlineDevices.toMap())
            }
            override fun serviceResolved(event: org.jmdns.ServiceEvent) {
                onlineDevices[event.name] = Pair(
                    event.info.inetAddresses[0].hostAddress,
                    event.info.port
                )
                callback(onlineDevices.toMap())
            }
        })
    }
}
