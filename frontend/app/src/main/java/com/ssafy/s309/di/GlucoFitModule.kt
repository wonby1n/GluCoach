package com.ssafy.s309.di

import android.content.Context
import com.ssafy.s309.feature.glucofit.health.HealthConnectManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * feature-glucofit 모듈의 매니저들을 Hilt 그래프에 노출한다.
 *
 * - [HealthConnectManager]: ApplicationContext 만 필요하므로 그대로 Singleton.
 * - SamsungHealthManager: Activity 인스턴스 필수 → SamsungHealthHolder 를 통해 라이프사이클로 관리.
 *   Holder 는 @Inject constructor 로 자동 생성되므로 별도 @Provides 불필요.
 *
 * feature-glucofit 모듈 자체는 Hilt 미사용 → 모듈 코드를 수정하지 않고 app 측에서만 wire.
 */
@Module
@InstallIn(SingletonComponent::class)
object GlucoFitModule {
    @Provides
    @Singleton
    fun provideHealthConnectManager(
        @ApplicationContext context: Context,
    ): HealthConnectManager = HealthConnectManager(context)
}
