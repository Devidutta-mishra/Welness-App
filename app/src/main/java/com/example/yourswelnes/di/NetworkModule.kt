package com.example.yourswelnes.di

import com.example.yourswelnes.BuildConfig
import com.example.yourswelnes.core.network.AuthInterceptor
import com.example.yourswelnes.feature.auth.data.remote.api.AuthApi
import com.example.yourswelnes.feature.dashboard.data.remote.api.DashboardApi
import com.example.yourswelnes.feature.home.data.remote.api.ClubApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://ywadvance.com/"

    // The dashboard login-url lives on the web-portal domain.
    private const val DASHBOARD_BASE_URL = "https://ywcenter.com/"

    private const val TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
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

    // The dashboard client must NOT follow redirects.
    // POST /api/login-url returns HTTP 302 + Location: <authenticated URL>.
    // If OkHttp follows the redirect it lands on the HTML page; GsonConverter then
    // throws MalformedJsonException (extends IOException) → "Unable to reach server".
    // With followRedirects=false we see the 302 directly and extract the Location header.
    @Named("dashboard")
    @Provides
    @Singleton
    fun provideDashboardOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .followRedirects(false)
        .followSslRedirects(false)
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

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideClubApi(retrofit: Retrofit): ClubApi = retrofit.create(ClubApi::class.java)

    @Provides
    @Singleton
    fun provideDashboardApi(
        @Named("dashboard") okHttpClient: OkHttpClient
    ): DashboardApi = Retrofit.Builder()
        .baseUrl(DASHBOARD_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DashboardApi::class.java)
}
