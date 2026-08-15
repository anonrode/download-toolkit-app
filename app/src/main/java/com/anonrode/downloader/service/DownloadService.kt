package com.anonrode.downloader.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.anonrode.downloader.MainActivity
import com.anonrode.downloader.engine.Aria2Engine
import com.anonrode.downloader.data.models.TaskStatus
import kotlinx.coroutines.*

class DownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        const val CHANNEL_ID = "anon_downloads_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWifiLock()
        observeDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Anon Downloader", "Download service active", 0, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun observeDownloads() {
        serviceScope.launch {
            Aria2Engine.instance.tasks.collect { tasks ->
                val active = tasks.filter { it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.RESOLVING }
                val completed = tasks.filter { it.status == TaskStatus.COMPLETED }

                // Scan completed files
                completed.forEach { task ->
                    MediaScannerConnection.scanFile(this@DownloadService, arrayOf(task.targetFilePath), null, null)
                }

                if (active.isNotEmpty()) {
                    val current = active.first()
                    val progress = (current.progressPercent * 100).toInt()
                    val speed = current.formattedSpeed
                    val title = "${current.showName}: ${current.episodeTitle}"
                    val text = if (speed.isNotBlank()) "Downloading • $speed • $progress%" else "Preparing download..."

                    val notif = buildNotification(title, text, progress, true)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notif)
                } else {
                    val notif = buildNotification("Anon Downloader", "All downloads completed", 100, false)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notif)
                }
            }
        }
    }

    private fun buildNotification(title: String, content: String, progress: Int, isDownloading: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(isDownloading)
            .setOnlyAlertOnce(true)

        if (isDownloading) {
            builder.setProgress(100, progress, progress == 0)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Anon Download Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download speeds and progress"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWifiLock() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AnonDownloader:WifiLock")
            wifiLock?.acquire()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        wifiLock?.release()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
