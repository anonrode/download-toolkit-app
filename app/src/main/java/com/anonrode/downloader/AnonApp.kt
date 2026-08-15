package com.anonrode.downloader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.anonrode.downloader.data.net.HttpClient

class AnonApp : Application(), ImageLoaderFactory {

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
}
