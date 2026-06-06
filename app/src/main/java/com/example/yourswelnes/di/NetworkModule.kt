package com.example.yourswelnes.di

import com.example.yourswelnes.BuildConfig
import com.example.yourswelnes.core.network.AuthInterceptor
import com.example.yourswelnes.feature.monitoring.data.api.AppMonitoringApi
import com.example.yourswelnes.feature.auth.data.api.AuthApi
import com.example.yourswelnes.feature.dashboard.data.api.DashboardApi
import com.example.yourswelnes.feature.home.data.api.ClubApi
import com.example.yourswelnes.feature.home.data.api.GroupDetailsApi
import com.example.yourswelnes.feature.location.data.api.LocationApi
import com.example.yourswelnes.feature.notifications.data.api.NotificationApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://ywadvance.com/"
    private const val TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides @Singleton
    fun provideClubApi(retrofit: Retrofit): ClubApi = retrofit.create(ClubApi::class.java)

    @Provides @Singleton
    fun provideLocationApi(retrofit: Retrofit): LocationApi = retrofit.create(LocationApi::class.java)

    @Provides @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi = retrofit.create(NotificationApi::class.java)

    @Provides @Singleton
    fun provideDashboardApi(retrofit: Retrofit): DashboardApi = retrofit.create(DashboardApi::class.java)

    @Provides @Singleton
    fun provideGroupDetailsApi(retrofit: Retrofit): GroupDetailsApi = retrofit.create(GroupDetailsApi::class.java)

    @Provides @Singleton
    fun provideAppMonitoringApi(retrofit: Retrofit): AppMonitoringApi = retrofit.create(AppMonitoringApi::class.java)
}
