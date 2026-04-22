@file:Suppress("ktlint:standard:no-empty-file")

package com.ssafy.s309.di

// HealthRepository 는 @Singleton + @Inject constructor 가 붙어 있어
// Hilt 가 자동으로 주입 그래프에 올린다. 현재는 별도 모듈 등록이 불필요.
//
// TODO(BE 연동): 실제 Retrofit HealthApi 를 주입해야 할 시점에 아래 패턴으로 모듈을 추가한다.
//
// import com.ssafy.s309.data.api.HealthApi
// import dagger.Module
// import dagger.Provides
// import dagger.hilt.InstallIn
// import dagger.hilt.components.SingletonComponent
// import retrofit2.Retrofit
// import javax.inject.Singleton
//
// @Module
// @InstallIn(SingletonComponent::class)
// object HealthNetworkModule {
//     @Provides
//     @Singleton
//     fun provideHealthApi(retrofit: Retrofit): HealthApi = retrofit.create(HealthApi::class.java)
// }
