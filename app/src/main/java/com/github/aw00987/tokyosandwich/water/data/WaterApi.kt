package com.github.aw00987.tokyosandwich.water.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Thrown when the saved token is missing, expired or rejected; a fresh login is needed. */
class SessionExpiredException : IOException("Session expired")

/** No credentials are configured, so the app cannot log in. */
class NoCredentialsException : IOException("No credentials configured")

/** `/auth/login` rejected the credentials; [code] is the service's `result` code (see [WaterApi.LOGIN_INVALID] etc.). */
class LoginFailedException(val code: String) : IOException("Tokyo Waterworks login failed: $code")

/** The service answered, but with a non-success `result` code. */
class WaterApiException(val code: String) : IOException("Tokyo Waterworks API error: $code")

/**
 * Read-only client for the API behind the official Tokyo Waterworks web app
 * (`www.suidoapp.waterworks.metro.tokyo.lg.jp`). Not a public API: see poc/api/东京水道局api.md.
 */
class WaterApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    /** Every response is wrapped as `{result, data, token}`; `token` is the refreshed JWT. */
    @Serializable
    data class Envelope(
        val result: String = "",
        val data: JsonElement = JsonNull,
        val token: String? = null,
    )

    suspend fun get(
        path: String,
        token: String,
        query: Map<String, String> = emptyMap()
    ): Envelope =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url(path, query))
                .header("Authorization", token)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$WEB_ORIGIN/")
                .build()
            client.newCall(request).await().use { response ->
                if (response.code == 401 || response.code == 403) throw SessionExpiredException()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val envelope = json.decodeFromString<Envelope>(response.body.string())
                if (envelope.result != RESULT_OK) throw WaterApiException(envelope.result)
                envelope
            }
        }

    /**
     * Logs in like the official web app (`POST /user/auth/login` with `{loginId, password}`) and returns the JWT.
     * Rejected credentials come back as HTTP 400 with an error `result` and raise [LoginFailedException].
     */
    suspend fun login(loginId: String, password: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("loginId", loginId)
            put("password", password)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url("user/auth/login", emptyMap()))
            .post(body)
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", WEB_ORIGIN)
            .header("Referer", "$WEB_ORIGIN/")
            .build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful && response.code != 400) throw IOException("HTTP ${response.code}")
            val envelope = runCatching { json.decodeFromString<Envelope>(response.body.string()) }
                .getOrElse { throw IOException("HTTP ${response.code}") }
            if (envelope.result != RESULT_OK) throw LoginFailedException(envelope.result)
            envelope.token?.takeIf { it.isNotBlank() } ?: throw WaterApiException("no_token")
        }
    }

    /** Like `execute()`, but cancelling the coroutine cancels the HTTP call (so a logout does not wait for it). */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }

    private fun url(path: String, query: Map<String, String>): HttpUrl =
        API_BASE.toHttpUrl().newBuilder().apply {
            path.trim('/').split('/').forEach(::addPathSegment)
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

    companion object {
        const val WEB_ORIGIN = "https://www.suidoapp.waterworks.metro.tokyo.lg.jp"
        private const val API_BASE = "https://api.suidoapp.waterworks.metro.tokyo.lg.jp"
        private const val RESULT_OK = "00000"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** Login `result` codes, as handled by the official web app. */
        const val LOGIN_INVALID = "21300"
        const val LOGIN_LOCKED = "21301"
        const val LOGIN_REVOKED = "90006"

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
