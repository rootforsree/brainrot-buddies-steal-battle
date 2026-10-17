package com.sreecharan.brainrot_buddies_steal_battle.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/**
 * Reads and writes [SettingsProto] for the Proto DataStore.
 * Defaults to system theme with haptics on.
 */
object SettingsSerializer : Serializer<SettingsProto> {
    override val defaultValue: SettingsProto =
        SettingsProto
            .newBuilder()
            .setThemeMode(ThemeModeProto.SYSTEM)
            .setHapticsEnabled(true)
            .build()

    override suspend fun readFrom(input: InputStream): SettingsProto =
        try {
            SettingsProto.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read settings proto.", e)
        }

    override suspend fun writeTo(
        t: SettingsProto,
        output: OutputStream,
    ) {
        t.writeTo(output)
    }
}
