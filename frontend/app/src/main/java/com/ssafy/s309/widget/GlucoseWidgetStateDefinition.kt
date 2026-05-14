package com.ssafy.s309.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.glance.state.GlanceStateDefinition
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object GlucoseWidgetStateDefinition : GlanceStateDefinition<GlucoseWidgetState> {
    private const val DATA_STORE_FILE = "glucose_widget_state"

    override suspend fun getDataStore(
        context: Context,
        fileKey: String,
    ): DataStore<GlucoseWidgetState> =
        DataStoreFactory.create(
            serializer = JsonSerializer,
            produceFile = { getLocation(context, fileKey) },
        )

    override fun getLocation(
        context: Context,
        fileKey: String,
    ): File = context.dataStoreFile("$DATA_STORE_FILE-$fileKey")

    private fun Context.dataStoreFile(name: String): File = File(filesDir, "datastore/$name")

    private object JsonSerializer : Serializer<GlucoseWidgetState> {
        override val defaultValue: GlucoseWidgetState = GlucoseWidgetState()

        override suspend fun readFrom(input: InputStream): GlucoseWidgetState =
            try {
                Json.decodeFromString(GlucoseWidgetState.serializer(), input.readBytes().decodeToString())
            } catch (e: Exception) {
                defaultValue
            }

        override suspend fun writeTo(
            t: GlucoseWidgetState,
            output: OutputStream,
        ) {
            output.write(Json.encodeToString(GlucoseWidgetState.serializer(), t).encodeToByteArray())
        }
    }
}
