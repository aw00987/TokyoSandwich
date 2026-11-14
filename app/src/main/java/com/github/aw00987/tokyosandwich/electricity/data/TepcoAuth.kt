package com.github.aw00987.tokyosandwich.electricity.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The Auth0 login of the official くらしTEPCO web app (authorization code + PKCE, public SPA client; see
 * poc/api/东京电力api.md §1). The user signs in on TEPCO's own page in a WebView, including the one-time code or
 * passkey; the app only builds the `/authorize` URL and catches the redirect that carries the code. The values below
 * are public (they ship in the official web front end).
 */
object TepcoAuth {
    const val DOMAIN = "epauth.tepco.co.jp"
    const val CLIENT_ID = "fNP5deyTGItHnp53Cm9lGoA7TIDwPRfh"
    const val REDIRECT_URI = "https://www.app.kurashi.tepco.co.jp/login/after"
    const val AUDIENCE = "https://kcx-api.tepco-z.com/"
    private const val SCOPE = "openid profile email"

    /** One login attempt: the page to open and the secrets needed to check and redeem its callback. */
    data class Request(val url: String, val state: String, val codeVerifier: String)

    sealed interface Callback {
        data class Code(val code: String) : Callback

        /** Auth0 redirected back with an `error` (e.g. `access_denied`), or the callback did not match the request. */
        data class Error(val error: String, val description: String?) : Callback
    }

    fun newRequest(random: SecureRandom = SecureRandom()): Request {
        val verifier = randomToken(random, 32)
        val state = randomToken(random, 16)
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(DOMAIN)
            .addPathSegment("authorize")
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("audience", AUDIENCE)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("response_mode", "query")
            .addQueryParameter("state", state)
            .addQueryParameter("nonce", randomToken(random, 16))
            .addQueryParameter("code_challenge", codeChallenge(verifier))
            .addQueryParameter("code_challenge_method", "S256")
            .build()
        return Request(url.toString(), state, verifier)
    }

    /** Parses a navigation to [REDIRECT_URI]; returns null for any other URL (the login pages themselves). */
    fun parseCallback(url: String, expectedState: String): Callback? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val redirect = REDIRECT_URI.toHttpUrlOrNull()!!
        if (parsed.host != redirect.host || parsed.encodedPath != redirect.encodedPath) return null
        if (parsed.queryParameter("state") != expectedState) return Callback.Error(
            "state_mismatch",
            null
        )
        parsed.queryParameter("error")?.let {
            return Callback.Error(it, parsed.queryParameter("error_description"))
        }
        val code = parsed.queryParameter("code")?.takeIf { it.isNotBlank() }
            ?: return Callback.Error("no_code", null)
        return Callback.Code(code)
    }

    /** RFC 7636 S256: base64url(sha256(verifier)) without padding. */
    internal fun codeChallenge(verifier: String): String =
        base64Url(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )

    private fun randomToken(random: SecureRandom, bytes: Int): String =
        base64Url(ByteArray(bytes).also(random::nextBytes))

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
