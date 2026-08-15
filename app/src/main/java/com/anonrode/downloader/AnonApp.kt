package com.anonrode.downloader

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.anonrode.downloader.data.net.HttpClient
import com.yausername.ffmpeg.FFmpeg
import com.yausername.aria2c.Aria2c
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class AnonApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Write any uncaught crash to a file in Downloads BEFORE anything else,
        // so a launch crash we can't reproduce without a PC leaves a readable
        // stack trace on the device. Open Download/anon_crash.txt after a crash.
        installCrashLogger()
        // Initialise yt-dlp + ffmpeg once, off the main thread: the first run
        // unpacks a bundled python runtime and can take seconds, which would ANR
        // if done in onCreate synchronously. The download path awaits
        // ensureReady() before touching yt-dlp, so an early share-intent can't
        // race the unpack.
        appScope.launch {
            initMutex.withLock {
                if (ytdlpReady) return@withLock
                try {
                    YoutubeDL.getInstance().init(this@AnonApp)
                    FFmpeg.getInstance().init(this@AnonApp)
                    Aria2c.getInstance().init(this@AnonApp)
                    ytdlpReady = true
                } catch (e: YoutubeDLException) {
                    // Left false: the HLS/social path surfaces a clear error
                    // rather than crashing, and the native file engine is
                    // unaffected.
                    Log.e("AnonApp", "youtubedl-android init failed", e)
                }
            }
            // Keep the bundled yt-dlp current. Sites (Instagram especially)
            // change their pages constantly, and the binary frozen into the
            // library ages out fast -- a stale one reports "yt-dlp is out of
            // date" and fails the extract. Runs AFTER readiness is set and in
            // the background, so a slow/failed update (offline) never blocks the
            // fast native path; the next launch just tries again. Throttled so
            // it isn't a network hit every cold start.
            if (ytdlpReady) maybeUpdateYoutubeDL()
        }
    }

    /** STABLE-channel yt-dlp self-update, at most once per [UPDATE_INTERVAL_MS]. */
    private suspend fun maybeUpdateYoutubeDL() {
        val prefs = getSharedPreferences("anon_prefs", Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_YTDLP_UPDATE, 0L)
        val now = System.currentTimeMillis()
        if (now - last < UPDATE_INTERVAL_MS) return
        try {
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(this, YoutubeDL.UpdateChannel.STABLE)
            // Stamp only on a real outcome so a thrown failure retries next launch.
            prefs.edit().putLong(KEY_LAST_YTDLP_UPDATE, now).apply()
            Log.i("AnonApp", "yt-dlp update: $status")
        } catch (e: Exception) {
            // Offline or update-server hiccup: keep the existing binary, retry
            // next launch (timestamp not written).
            Log.w("AnonApp", "yt-dlp update failed, keeping bundled binary", e)
        }
    }

    /**
     * Global last-resort crash logger. Writes the full stack trace of any
     * uncaught exception to Download/anon_crash.txt, then delegates to the
     * previous handler so Android still shows its dialog. This is how we get a
     * real cause off the device without a PC/adb: after a crash, open that file.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS)
                val f = File(dir, "anon_crash.txt")
                f.appendText(
                    "\n===== crash on thread ${thread.name} =====\n" +
                    Log.getStackTraceString(throwable) + "\n")
            } catch (_: Throwable) {
                // If we can't even write the log, fall through to the OS handler.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(): ImageLoader {
        // Derive from the shared client so image loads reuse the same connection
        // pool as API/download traffic; just layer on the browser User-Agent that
        // some poster CDNs require.
        val client = HttpClient.shared.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", HttpClient.DEFAULT_UA)
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }

    companion object {
        private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val initMutex = Mutex()

        private const val KEY_LAST_YTDLP_UPDATE = "last_ytdlp_update"
        private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000  // once per day

        @Volatile
        var ytdlpReady: Boolean = false
            private set

        /**
         * Suspend until yt-dlp/ffmpeg finished initialising. Returns true if the
         * engine is usable, false if init failed (caller shows an error instead
         * of crashing). Cheap once ready -- just takes and releases the mutex.
         */
        suspend fun ensureReady(): Boolean {
            if (ytdlpReady) return true
            initMutex.withLock { return ytdlpReady }
        }
    }
}
