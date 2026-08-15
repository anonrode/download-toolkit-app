package com.anonrode.downloader.data.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One OkHttpClient for the whole app.
 *
 * Previously AnonApp, VpsApiClient and Aria2Engine each built their own client
 * -- three connection pools, three thread pools, three copies of the User-Agent
 * config that could drift apart. OkHttp is explicitly designed to be shared: a
 * single instance pools and reuses connections across every call, which is
 * faster and lighter. Callers that need different timeouts derive a cheap copy
 * with newBuilder() -- that shares the underlying pools.
 */
object HttpClient {

    const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** The base shared client. Default OkHttp TLS (certs validated). */
    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    /**
     * A variant for large media transfers: same pools as [shared], just longer
     * read/write windows so a slow CDN chunk doesn't time out mid-file.
     */
    val download: OkHttpClient by lazy {
        shared.newBuilder()
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
