package com.malgra.reliquary.core.network

import com.malgra.reliquary.core.network.auth.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * One client for the API, images, streaming and downloads, so they share auth and connections.
 * [logHttp] logs request lines for debug builds, with credentials redacted.
 */
fun tubeArchivistHttpClient(
    authInterceptor: AuthInterceptor,
    reachabilityInterceptor: ReachabilityInterceptor,
    logHttp: Boolean,
): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .addInterceptor(reachabilityInterceptor)
    .addInterceptor(authInterceptor)
    .apply {
        if (logHttp) {
            addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    listOf("Authorization", "Cookie", "Set-Cookie", "X-CSRFToken").forEach(::redactHeader)
                },
            )
        }
    }
    .build()
