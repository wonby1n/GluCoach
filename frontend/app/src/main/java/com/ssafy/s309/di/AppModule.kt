package com.ssafy.s309.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ssafy.s309.data.api.AgentApi
import com.ssafy.s309.data.api.AuthApi
import com.ssafy.s309.data.api.FoodApi
import com.ssafy.s309.data.api.HealthApi
import com.ssafy.s309.data.api.PredictApi
import com.ssafy.s309.data.api.SleepSessionApi
import com.ssafy.s309.data.api.UserApi
import com.ssafy.s309.data.api.WeeklyReportApi
import com.ssafy.s309.data.network.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

private const val BASE_URL = "https://k14s309.p.ssafy.io/"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                },
            )
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    @Named("authenticated")
    fun provideAuthenticatedOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                },
            )
            .build()

    @Provides
    @Singleton
    @Named("authenticated")
    fun provideAuthenticatedRetrofit(
        @Named("authenticated") okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideUserApi(
        @Named("authenticated") retrofit: Retrofit,
    ): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideFoodApi(
        @Named("authenticated") retrofit: Retrofit,
    ): FoodApi = retrofit.create(FoodApi::class.java)

    @Provides
    @Singleton
    fun providePredictApi(
        @Named("authenticated") retrofit: Retrofit,
    ): PredictApi = retrofit.create(PredictApi::class.java)

    @Provides
    @Singleton
    fun provideHealthApi(
        @Named("authenticated") retrofit: Retrofit,
    ): HealthApi = retrofit.create(HealthApi::class.java)

    @Provides
    @Singleton
    fun provideSleepSessionApi(
        @Named("authenticated") retrofit: Retrofit,
    ): SleepSessionApi = retrofit.create(SleepSessionApi::class.java)

    @Provides
    @Singleton
    @Named("no-redirect")
    fun provideNoRedirectOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                },
            )
            .followRedirects(false)
            .build()

    @Provides
    @Singleton
    @Named("no-redirect")
    fun provideNoRedirectRetrofit(
        @Named("no-redirect") okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideWeeklyReportApi(
        @Named("no-redirect") retrofit: Retrofit,
    ): WeeklyReportApi = retrofit.create(WeeklyReportApi::class.java)

    @Provides
    @Singleton
    fun provideAgentApi(
        @Named("authenticated") retrofit: Retrofit,
    ): AgentApi = retrofit.create(AgentApi::class.java)
}
