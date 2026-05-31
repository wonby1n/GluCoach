package com.ssafy.s309.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * 폰 → 워치(:wear 모듈) 혈당값 송신 헬퍼.
 *
 * 호출 측 책임:
 *  - 호출 시점 결정 (예: HealthRepository에서 새 혈당 도착 시)
 *  - 코루틴 스코프 제공
 *
 * 안전성: 워치 미연결 / Wearable API 미설치 / 노드 0개여도 false 반환만 하고 예외 전파 안 함.
 * GLUCOSE_PATH는 :wear 모듈의 GlucoseListenerService와 상수로 짝을 이룸.
 */
object WearDataSender {
    const val GLUCOSE_PATH = "/glucose"
    const val NOTIFICATION_PATH = "/notification"

    suspend fun sendNotification(
        context: Context,
        title: String,
        body: String,
    ): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) return false
            val payload =
                org.json.JSONObject().apply {
                    put("title", title)
                    put("body", body)
                }.toString().toByteArray(Charsets.UTF_8)
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, NOTIFICATION_PATH, payload)
                    .await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "알림 전송 실패", e)
            false
        }
    }

    suspend fun send(
        context: Context,
        value: Double,
    ): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            Log.d(TAG, "연결된 노드 ${nodes.size}개: ${nodes.map { "${it.displayName}(${it.id})" }}")
            if (nodes.isEmpty()) return false
            val bytes = value.toString().toByteArray(Charsets.UTF_8)
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, GLUCOSE_PATH, bytes)
                    .await()
                Log.d(TAG, "전송 완료 → ${node.displayName}: $value")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "전송 실패", e)
            false
        }
    }

    private const val TAG = "WearDataSender"
}
