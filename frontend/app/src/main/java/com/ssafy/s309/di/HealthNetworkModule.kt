@file:Suppress("ktlint:standard:no-empty-file")

package com.ssafy.s309.di

// HealthRepository 는 @Singleton + @Inject constructor 가 붙어 있어
// Hilt 가 자동으로 주입 그래프에 올린다. 별도 모듈 등록 불필요.
//
// TODO(BE 연동): Retrofit HealthApi 를 AppModule 에 추가한다.
