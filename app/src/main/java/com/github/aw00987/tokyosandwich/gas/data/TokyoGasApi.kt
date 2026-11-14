package com.github.aw00987.tokyosandwich.gas.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** The session cookies are missing or no longer accepted (`LOGIN_AUTH_UNAUTHENTICATED`). The user has to log in again. */
class GasSessionExpiredException : IOException("Tokyo Gas session expired")

/** The service is busy (HTTP 429/503/504); try again later. */
class GasBusyException(status: Int) : IOException("HTTP $status")

/** HTTP 200 with GraphQL `errors`; [code] is `extensions.code`, or the message when there is none. */
class GasApiException(val code: String) : IOException("Tokyo Gas API error: $code")

/**
 * The `members.tokyo-gas.co.jp` session cookies, taken from the WebView after the user's login and kept in memory only.
 * The service renews them while it is used, so every response's `Set-Cookie` is applied ([update]).
 */
class GasSession private constructor(private val cookies: MutableMap<String, String>) {
    /** The `Cookie` header for the next request. */
    @Synchronized
    fun header(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    @Synchronized
    fun names(): Set<String> = cookies.keys.toSet()

    /** Applies a response's `Set-Cookie` headers: new values replace old ones, expired cookies are removed. */
    @Synchronized
    fun update(url: HttpUrl, headers: Headers, now: Long = System.currentTimeMillis()) {
        Cookie.parseAll(url, headers).forEach { cookie ->
            if (cookie.expiresAt <= now) cookies.remove(cookie.name) else cookies[cookie.name] =
                cookie.value
        }
    }

    companion object {
        /**
         * Parses the WebView's `CookieManager.getCookie()` value (`a=1; b=2`). Returns null when it lacks the session
         * cookie, i.e. the login did not complete.
         */
        fun fromCookieHeader(header: String?): GasSession? {
            val cookies = header.orEmpty().split(';').mapNotNull { part ->
                val name = part.substringBefore('=', "").trim()
                if (name.isEmpty()) null else name to part.substringAfter('=').trim()
            }.toMap(LinkedHashMap())
            return if (cookies[TokyoGasAuth.SESSION_COOKIE].isNullOrEmpty()) null else GasSession(
                cookies
            )
        }
    }
}

/**
 * Read-only client for the GraphQL API behind myTOKYOGAS. Not a public API: see poc/api/东京燃气api.md. It only sends
 * GraphQL queries, never mutations.
 */
class TokyoGasApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
) {
    @Serializable
    internal data class Envelope(
        val data: JsonElement = JsonNull,
        val errors: List<GraphQlError> = emptyList()
    )

    @Serializable
    internal data class GraphQlError(val message: String = "", val extensions: Extensions? = null)

    @Serializable
    internal data class Extensions(val code: String? = null)

    /** Runs one GraphQL query and returns its `data` after checking the status and `errors`. */
    suspend fun query(
        session: GasSession,
        operationName: String,
        document: String,
        variables: JsonObject = JsonObject(emptyMap()),
    ): JsonElement = withContext(Dispatchers.IO) {
        require(!document.trimStart().startsWith("mutation")) { "Only queries are allowed" }
        val body = buildJsonObject {
            put("operationName", operationName)
            put("query", document)
            put("variables", variables)
        }.toString()
        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Accept", "*/*")
            .header("Origin", TokyoGasAuth.ORIGIN)
            .header("Referer", "${TokyoGasAuth.ORIGIN}/")
            .header("Cookie", session.header())
            .build()
        client.newCall(request).await().use { response ->
            session.update(request.url, response.headers)
            when (response.code) {
                401, 403 -> throw GasSessionExpiredException()
                429, 503, 504 -> throw GasBusyException(response.code)
            }
            // A redirect means the session was not accepted (the site sends logged-out users to the login).
            if (response.isRedirect) throw GasSessionExpiredException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            parseEnvelope(response.body.string())
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

    companion object {
        private val GRAPHQL_URL = "${TokyoGasAuth.ORIGIN}/graphql".toHttpUrl()
        private val JSON_MEDIA = "application/json".toMediaType()

        /** The service's "not logged in" error code; it comes with HTTP 200. */
        internal const val UNAUTHENTICATED = "LOGIN_AUTH_UNAUTHENTICATED"

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        /** The `data` of a response; any `errors` fail the call (an unauthenticated one as an expired session). */
        internal fun parseEnvelope(text: String): JsonElement {
            val envelope = json.decodeFromString<Envelope>(text)
            if (envelope.errors.any { it.extensions?.code == UNAUTHENTICATED }) throw GasSessionExpiredException()
            envelope.errors.firstOrNull()
                ?.let { throw GasApiException(it.extensions?.code ?: it.message) }
            return envelope.data
        }
    }
}

/**
 * The myTOKYOGAS login (see poc/api/东京燃气api.md §1). The user signs in on Tokyo Gas's own page in a WebView; the
 * code is redeemed by Tokyo Gas's server, which then sets the session cookies. The app only recognises the end of that
 * redirect and takes the cookies.
 */
object TokyoGasAuth {
    const val HOST = "members.tokyo-gas.co.jp"
    const val ORIGIN = "https://$HOST"
    const val LOGIN_URL = "$ORIGIN/api/mtg/v1/auth/login"
    const val SESSION_COOKIE = "mtg-session-id"
    private const val REDIRECT_PATH = "/api/mtg/v1/auth/redirect"

    /** The login callback that Tokyo Gas's server redeems (`…/auth/redirect?code=…`). */
    fun isRedirect(url: String): Boolean =
        url.toHttpUrlOrNull()?.let { it.host == HOST && it.encodedPath == REDIRECT_PATH } == true

    /** A page of the members site other than the login endpoints: where the browser goes once logged in. */
    fun isMembersPage(url: String): Boolean =
        url.toHttpUrlOrNull()
            ?.let { it.host == HOST && !it.encodedPath.startsWith("/api/mtg/v1/auth/") } == true

    /** The `error` of a callback such as `…/auth/redirect?error=access_denied`, or null. */
    fun redirectError(url: String): String? =
        url.toHttpUrlOrNull()?.takeIf { isRedirect(url) }?.queryParameter("error")
}
