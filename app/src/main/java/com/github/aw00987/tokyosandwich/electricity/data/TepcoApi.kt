package com.github.aw00987.tokyosandwich.electricity.data

import com.github.aw00987.tokyosandwich.core.jwtStringClaim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** The access token is missing, expired or rejected (HTTP 401/403). TEPCO needs the user to log in again. */
class TepcoSessionExpiredException : IOException("TEPCO session expired")

/** Exchanging the login code for a token failed; [code] is the HTTP status or Auth0's `error`. */
class TepcoLoginFailedException(val code: String) : IOException("TEPCO login failed: $code")

/** The service is busy (HTTP 429/504); try again later. */
class TepcoBusyException(status: Int) : IOException("HTTP $status")

/** HTTP 200 with a business error in `commonInfo.errorCode`. */
class TepcoApiException(val code: String) : IOException("TEPCO API error: $code")

/** What a login gives: the access token and the account's email from the ID token (null when it has none). */
data class TepcoLogin(val token: TepcoToken, val email: String?)

/** An access token for `kcx-api.tepco-z.com`, valid until [expiresAt] (epoch milliseconds, about 30 minutes). */
data class TepcoToken(val accessToken: String, val expiresAt: Long) {
    /** True if the token is expired or expires within [skewMillis] (so a sync started now can finish). */
    fun isExpired(now: Long, skewMillis: Long = 60_000): Boolean = now >= expiresAt - skewMillis
}

/**
 * Read-only client for the API behind the official くらしTEPCO web app. Not a public API: see
 * poc/api/东京电力api.md. Apart from the token exchange it only sends GET requests.
 */
class TepcoApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    @Serializable
    private data class TokenResponse(
        val access_token: String = "",
        val expires_in: Long = 0,
        val id_token: String? = null,
        val error: String? = null,
    )

    @Serializable
    internal data class CommonInfo(val timestamp: String = "", val errorCode: String? = null)

    /** Every response carries `commonInfo`; a non-empty `errorCode` is a business error. */
    @Serializable
    private data class Envelope(val commonInfo: CommonInfo = CommonInfo())

    /** Redeems the code from the login redirect (`POST /oauth/token`, as auth0-spa-js does). */
    suspend fun exchangeCode(code: String, codeVerifier: String, now: Long): TepcoLogin =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("client_id", TepcoAuth.CLIENT_ID)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", TepcoAuth.REDIRECT_URI)
                .build()
            val request = Request.Builder()
                .url("https://${TepcoAuth.DOMAIN}/oauth/token")
                .post(body)
                .header("Accept", "application/json")
                .header("Origin", WEB_ORIGIN)
                .header("Referer", "$WEB_ORIGIN/")
                .build()
            client.newCall(request).await().use { response ->
                val token =
                    runCatching { json.decodeFromString<TokenResponse>(response.body.string()) }.getOrNull()
                if (!response.isSuccessful || token == null || token.access_token.isBlank()) {
                    throw TepcoLoginFailedException(token?.error ?: "HTTP ${response.code}")
                }
                val lifetime = token.expires_in.takeIf { it > 0 } ?: DEFAULT_LIFETIME_SECONDS
                TepcoLogin(
                    TepcoToken(token.access_token, now + lifetime * 1000),
                    token.id_token?.let { jwtStringClaim(it, "email") }?.trim()
                        ?.takeIf { it.isNotEmpty() },
                )
            }
        }

    /** GETs `/kcx/<path>` and returns the body after checking the status and `commonInfo.errorCode`. */
    suspend fun get(
        path: String,
        token: String,
        clientId: String,
        query: Map<String, String>
    ): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url(path, query))
                .header("Accept", "application/json; charset=utf-8")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                // The API answers 400 without these; the official front end sends one per-browser UUID in both.
                .header("x-kcx-tracking-id", clientId)
                .header("X-API-Request-Id", clientId)
                .header("Origin", WEB_ORIGIN)
                .header("Referer", "$WEB_ORIGIN/")
                .build()
            client.newCall(request).await().use { response ->
                when (response.code) {
                    401, 403 -> throw TepcoSessionExpiredException()
                    429, 504 -> throw TepcoBusyException(response.code)
                }
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val text = response.body.string()
                val errorCode = json.decodeFromString<Envelope>(text).commonInfo.errorCode
                if (!errorCode.isNullOrBlank()) throw TepcoApiException(errorCode)
                text
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
        const val WEB_ORIGIN = "https://www.app.kurashi.tepco.co.jp"
        private const val API_BASE = "https://kcx-api.tepco-z.com/kcx"
        private const val DEFAULT_LIFETIME_SECONDS = 1800L

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}
