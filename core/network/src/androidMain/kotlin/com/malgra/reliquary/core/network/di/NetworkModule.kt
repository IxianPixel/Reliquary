package com.malgra.reliquary.core.network.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.malgra.reliquary.core.network.ReachabilityInterceptor
import com.malgra.reliquary.core.network.TubeArchivistJson
import com.malgra.reliquary.core.network.auth.AuthInterceptor
import com.malgra.reliquary.core.network.tubeArchivistHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = TubeArchivistJson

    @Provides
    @Singleton
    fun okHttpClient(
        authInterceptor: AuthInterceptor,
        reachabilityInterceptor: ReachabilityInterceptor,
        @ApplicationContext context: Context,
    ): OkHttpClient = tubeArchivistHttpClient(
        authInterceptor,
        reachabilityInterceptor,
        logHttp = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    )
}
