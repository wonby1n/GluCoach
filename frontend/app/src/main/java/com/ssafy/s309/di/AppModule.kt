package com.ssafy.s309.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// TODO: Retrofit, OkHttpClient, Repository 등 DI 바인딩 추가
@Module
@InstallIn(SingletonComponent::class)
object AppModule
