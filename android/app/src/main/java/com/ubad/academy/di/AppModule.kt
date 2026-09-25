package com.ubad.academy.di

import com.ubad.academy.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (BuildConfig.DEBUG) addInterceptor { chain ->
                val r = chain.request()
                android.util.Log.d("UbadHttp", "${r.method} ${r.url}")
                chain.proceed(r)
            }
        }
        .build()

    /** Survives screens; used for fire-and-forget persistence (like the web's saveData()). */
    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
object AndroidModule {
    @Provides
    fun contentResolver(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): android.content.ContentResolver =
        context.contentResolver
}
