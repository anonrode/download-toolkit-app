package com.anonrode.downloader

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.anonrode.downloader.data.net.HttpClient
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnonApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
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
                    ytdlpReady = true
                } catch (e: YoutubeDLException) {
                    // Left false: the HLS/social path surfaces a clear error
                    // rather than crashing, and the native file engine is
                    // unaffected.
                    Log.e("AnonApp", "youtubedl-android init failed", e)
                }
            }
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
