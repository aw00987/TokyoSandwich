package com.github.aw00987.tokyosandwich.electricity.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TepcoAuthTest {
    @Test
    fun codeChallengeIsUnpaddedBase64UrlSha256() {
        // Expected value from: printf '<verifier>' | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '='
        assertEquals(
            "pwmsYhRT2i8Dd5ijrFXZQqA0oXi3fRFp5IhhS35yFZE",
            TepcoAuth.codeChallenge("dBjftJeZ4CVP-mJ92aRgYq1dE5pcIRi4gqiwszdQsGo"),
        )
    }

    @Test
    fun authorizeUrlCarriesPkceAndState() {
        val request = TepcoAuth.newRequest()
        val url = request.url.toHttpUrl()
        assertEquals("epauth.tepco.co.jp", url.host)
        assertEquals("/authorize", url.encodedPath)
        assertEquals(TepcoAuth.CLIENT_ID, url.queryParameter("client_id"))
        assertEquals(TepcoAuth.REDIRECT_URI, url.queryParameter("redirect_uri"))
        assertEquals(TepcoAuth.AUDIENCE, url.queryParameter("audience"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals(request.state, url.queryParameter("state"))
        assertEquals(
            TepcoAuth.codeChallenge(request.codeVerifier),
            url.queryParameter("code_challenge")
        )
        assertNotEquals(request.state, TepcoAuth.newRequest().state)
    }

    @Test
    fun requestAlwaysShowsTheLoginPage() {
        assertNull(TepcoAuth.newRequest().url.toHttpUrl().queryParameter("prompt"))
    }

    @Test
    fun parsesTheRedirectOnly() {
        assertNull(TepcoAuth.parseCallback("https://epauth.tepco.co.jp/u/login?state=s1", "s1"))
        assertEquals(
            TepcoAuth.Callback.Code("code-placeholder"),
            TepcoAuth.parseCallback(
                "${TepcoAuth.REDIRECT_URI}?code=code-placeholder&state=s1",
                "s1"
            ),
        )
        assertEquals(
            TepcoAuth.Callback.Error("access_denied", "cancelled"),
            TepcoAuth.parseCallback(
                "${TepcoAuth.REDIRECT_URI}?error=access_denied&error_description=cancelled&state=s1",
                "s1",
            ),
        )
    }

    @Test
    fun rejectsAForeignState() {
        assertEquals(
            TepcoAuth.Callback.Error("state_mismatch", null),
            TepcoAuth.parseCallback("${TepcoAuth.REDIRECT_URI}?code=c&state=other", "s1"),
        )
    }

    @Test
    fun tokenExpiresWithSkew() {
        val token = TepcoToken("t", expiresAt = 1_800_000)
        assertEquals(false, token.isExpired(now = 1_000_000))
        assertEquals(true, token.isExpired(now = 1_750_000))
    }
}
