package com.example.lanshare

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import org.webrtc.*

object WebRTCManager {
    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private lateinit var rootEglBase: EglBase

    fun initialize(context: Context) {
        rootEglBase = EglBase.create()
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun startScreenCapture(context: Context, resultCode: Int, data: Intent) {
        val videoSource = factory.createVideoSource(false)
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
        val capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {})
        capturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        capturer.startCapture(720, 1280, 30)
        localVideoTrack = factory.createVideoTrack("screenshare", videoSource)
    }

    fun onPeerConnected(name: String, ip: String, port: Int) {
        createPeerConnection(name)
    }

    fun onSignalMessage(msg: org.json.JSONObject) {
        // 处理 SDP 交换
    }

    private fun createPeerConnection(peerId: String) {
        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {
                channel?.registerObserver(object : DataChannel.Observer {
                    override fun onMessage(buffer: DataChannel.Buffer) {}
                    override fun onBufferedAmountChange(p0: Long) {}
                    override fun onStateChange() {}
                })
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRemoveTrack(receiver: RtpReceiver?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}  // 必加方法
        }
        peerConnection = factory.createPeerConnection(rtcConfig, observer)
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("screenshare")) }
    }
}
