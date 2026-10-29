package com.example.phoneaudio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import java.net.ServerSocket
import kotlin.concurrent.thread

class AudioService : Service() {
    private val secret = BuildConfig.AUDIO_SECRET
    override fun onBind(i: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(i: Intent, f: Int, id: Int): Int {
        val ch = "audio"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ch, "Audio stream", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch)
            .setContentTitle("Streaming phone audio to PC")
            .setSmallIcon(android.R.drawable.ic_media_play).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(i.getIntExtra("code", 0), i.getParcelableExtra("data")!!)
            ?: run { stopSelf(); return START_NOT_STICKY }
        proj.registerCallback(object : MediaProjection.Callback() {}, null)
        thread { stream(proj) }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun stream(proj: MediaProjection) {
        val cfg = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(48000)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        val minBuf = AudioRecord.getMinBufferSize(
            48000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord.Builder()
            .setAudioFormat(fmt)
            .setAudioPlaybackCaptureConfig(cfg)
            .setBufferSizeInBytes(minBuf * 2).build()

        val server = ServerSocket(5556)
        val buf = ByteArray(3840) // 20 ms of audio
        while (true) {
            val s = server.accept()
            try {
                s.tcpNoDelay = true
                val auth = s.getInputStream().bufferedReader().readLine()
                if (auth != "AUTH $secret") { s.close(); continue }
                rec.startRecording()
                val out = s.getOutputStream()
                while (true) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            } catch (_: Exception) {
            } finally {
                try { rec.stop() } catch (_: Exception) {}
                s.close()
            }
        }
    }
}