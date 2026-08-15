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
    private val scannedFiles = mutableSetOf<String>()

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
                val failed = tasks.filter { it.status == TaskStatus.FAILED }

                // Scan completed files (only once per file)
                completed.forEach { task ->
                    if (task.targetFilePath !in scannedFiles) {
                        scannedFiles.add(task.targetFilePath)
                        MediaScannerConnection.scanFile(this@DownloadService, arrayOf(task.targetFilePath), null, null)
                    }
                }

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                when {
                    active.isNotEmpty() -> {
                        val current = active.first()
                        val progress = (current.progressPercent * 100).toInt()
                        val speed = current.formattedSpeed
                        val title = "${current.showName}: ${current.episodeTitle}"
                        val text = when {
                            current.status == TaskStatus.RESOLVING -> "Resolving download link..."
                            speed.isNotBlank() -> "Downloading \u2022 $speed \u2022 $progress%"
                            else -> "Starting download..."
                        }
                        manager.notify(NOTIFICATION_ID, buildNotification(title, text, progress, true))
                    }
                    failed.isNotEmpty() && completed.isEmpty() -> {
                        val failMsg = failed.first().errorMessage ?: "Download failed"
                        manager.notify(NOTIFICATION_ID, buildNotification("Download Failed", failMsg, 0, false))
                    }
                    completed.isNotEmpty() -> {
                        val count = completed.size
                        val text = if (count == 1) "${completed.first().episodeTitle} saved" else "$count downloads completed"
                        manager.notify(NOTIFICATION_ID, buildNotification("Downloads Complete", text, 100, false))
                    }
                    tasks.isEmpty() -> {
                        // No tasks at all — dismiss notification and stop service
                        manager.cancel(NOTIFICATION_ID)
                        stopSelf()
                    }
                    else -> {
                        // Queued or paused only
                        val queued = tasks.count { it.status == TaskStatus.QUEUED }
                        val paused = tasks.count { it.status == TaskStatus.PAUSED }
                        val text = buildString {
                            if (queued > 0) append("$queued queued")
                            if (paused > 0) {
                                if (isNotEmpty()) append(" \u2022 ")
                                append("$paused paused")
                            }
                        }.ifEmpty { "Idle" }
                        manager.notify(NOTIFICATION_ID, buildNotification("Anon Downloader", text, 0, false))
                    }
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
            .setSmallIcon(if (isDownloading) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
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
