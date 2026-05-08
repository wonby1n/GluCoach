package com.ssafy.s309.projector

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProjectorSocket"
private const val PREF_NAME = "projector_prefs"
private const val KEY_IP = "pi_ip"
private const val DEFAULT_IP = "192.168.0.100"
private const val PORT = 9999
private const val TIMEOUT_MS = 5_000

@Singleton
class ProjectorSocketClient
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        var piIp: String
            get() = prefs.getString(KEY_IP, DEFAULT_IP) ?: DEFAULT_IP
            set(value) = prefs.edit().putString(KEY_IP, value).apply()

        private var socket: Socket? = null
        private var writer: OutputStreamWriter? = null

        suspend fun connect(): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    disconnect()
                    socket = Socket(piIp, PORT).apply { soTimeout = TIMEOUT_MS }
                    writer = OutputStreamWriter(socket!!.getOutputStream(), Charsets.UTF_8)
                    Log.i(TAG, "연결 성공: $piIp:$PORT")
                }.isSuccess
            }

        suspend fun send(command: String): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (socket == null || socket!!.isClosed) connect()
                    writer?.run {
                        write("$command\n")
                        flush()
                    }
                    Log.d(TAG, "전송: $command")
                }.isSuccess
            }

        suspend fun show() = send("SHOW")

        suspend fun hide() = send("HIDE")

        suspend fun alert() = send("ALERT")

        suspend fun briefing(
            sleepScore: Int,
            glucose: Int,
        ) = send("BRIEFING:$sleepScore:$glucose")

        suspend fun disconnect() =
            withContext(Dispatchers.IO) {
                runCatching { writer?.close() }
                runCatching { socket?.close() }
                socket = null
                writer = null
            }
    }
