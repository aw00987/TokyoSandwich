package com.github.aw00987.tokyosandwich.core.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.github.aw00987.tokyosandwich.core.KeystoreCipher
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** The JSON of every saved file: unknown fields (of older or newer formats) are ignored. */
internal val storedJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** [value] as the JSON bytes that are saved (and encrypted, for the encrypted files). */
internal fun <T> encodeStored(serializer: KSerializer<T>, value: T): ByteArray =
    storedJson.encodeToString(serializer, value).encodeToByteArray()

internal fun <T> decodeStored(serializer: KSerializer<T>, bytes: ByteArray): T =
    storedJson.decodeFromString(serializer, bytes.decodeToString())

/**
 * A typed DataStore file in the app's private files (`files/datastore/`, excluded from backups), saved as the bytes
 * [write] makes. A file that cannot be read is replaced by [default]. Create only one per [fileName].
 */
private fun <T> fileDataStore(
    context: Context,
    fileName: String,
    default: T,
    write: (T) -> ByteArray,
    read: (ByteArray) -> T,
): DataStore<T> {
    val appContext = context.applicationContext
    return DataStoreFactory.create(
        serializer = object : Serializer<T> {
            override val defaultValue = default

            override suspend fun readFrom(input: InputStream): T {
                val bytes = input.readBytes()
                if (bytes.isEmpty()) return default
                return try {
                    read(bytes)
                } catch (e: Exception) {
                    throw CorruptionException("Cannot read $fileName", e)
                }
            }

            override suspend fun writeTo(t: T, output: OutputStream) {
                output.write(write(t))
            }
        },
        corruptionHandler = ReplaceFileCorruptionHandler { default },
        produceFile = { appContext.dataStoreFile(fileName) },
    )
}

/** A [fileDataStore] saved as plain JSON, for what needs no encryption (the settings). */
internal fun <T> jsonDataStore(
    context: Context,
    fileName: String,
    serializer: KSerializer<T>,
    default: T,
): DataStore<T> = fileDataStore(
    context, fileName, default,
    write = { encodeStored(serializer, it) },
    read = { decodeStored(serializer, it) },
)

/**
 * A [fileDataStore] saved as JSON encrypted as a whole with the Android Keystore key, for accounts, passwords and
 * sessions. A file that cannot be decrypted (e.g. the key was reset) is replaced by [default].
 */
internal fun <T> encryptedDataStore(
    context: Context,
    fileName: String,
    serializer: KSerializer<T>,
    default: T,
): DataStore<T> = fileDataStore(
    context, fileName, default,
    write = { KeystoreCipher.encrypt(encodeStored(serializer, it)) },
    read = { decodeStored(serializer, KeystoreCipher.decrypt(it)) },
)
