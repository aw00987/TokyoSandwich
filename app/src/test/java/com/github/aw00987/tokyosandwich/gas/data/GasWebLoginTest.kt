package com.github.aw00987.tokyosandwich.gas.data

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure parts of the hidden-WebView login, with placeholder credentials. */
class GasWebLoginTest {
    @Test
    fun parsesTheDoublyEncodedScriptResult() {
        // evaluateJavascript hands back the script's string result JSON-encoded once more.
        val inner =
            """{"submitted":false,"loginForm":true,"passwordChange":false,"error":"ログインIDまたはパスワードが違います"}"""
        val raw = Json.encodeToString(String.serializer(), inner)
        assertEquals(
            LoginPageState(loginForm = true, error = "ログインIDまたはパスワードが違います"),
            parsePageState(raw),
        )
    }

    @Test
    fun unreadableResultsCountAsAPageWithoutTheForm() {
        assertEquals(LoginPageState(), parsePageState(null))
        assertEquals(LoginPageState(), parsePageState("null")) // no page loaded yet
        assertEquals(LoginPageState(), parsePageState("{}")) // not a string
    }

    @Test
    fun credentialsAreEscapedAsJsStringLiterals() {
        val password = "p\"a'ss\\word</script>\n"
        val script = fillScript("user@example.com", password)
        assertTrue(script.contains(Json.encodeToString(String.serializer(), password)))
        assertTrue(script.contains("\"user@example.com\""))
        assertFalse(script.contains("p\"a'ss")) // never inserted raw
        assertTrue(script.contains("getElementById('submit-btn')"))
    }

    @Test
    fun inspectScriptIsSelfContained() {
        assertTrue(INSPECT_SCRIPT.startsWith("(function () {"))
        assertTrue(INSPECT_SCRIPT.trimEnd().endsWith("})()"))
    }
}
