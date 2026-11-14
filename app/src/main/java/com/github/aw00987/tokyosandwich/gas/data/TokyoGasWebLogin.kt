package com.github.aw00987.tokyosandwich.gas.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.coroutines.resume

/** The automatic login did not give a session; [reason] says why (see [GasLoginFailure]). */
class GasLoginFailedException(val reason: GasLoginFailure, val detail: String? = null) :
    IOException("Tokyo Gas login failed: $reason")

enum class GasLoginFailure {
    /** The login page came back with an error (wrong ID or password, locked account, …); its text is the detail. */
    Rejected,

    /** The service asks for a new password; the app does not change it. */
    PasswordChangeRequired,

    /** A page the app does not know came up after submitting (e.g. a one-time code). */
    InteractionRequired,

    /** The login page could not be filled in (it changed), or it did not finish in time. */
    PageNotRecognised,
}

/** Logs in to myTOKYOGAS with the saved ID and password and returns the session cookies (`Cookie` header form). */
fun interface GasLogin {
    suspend fun login(loginId: String, password: String): String
}

/**
 * The myTOKYOGAS login, done by the app in a WebView that is never shown. The login page (Oracle Identity Cloud at
 * `login.tokyo-gas.co.jp`) loads its own device fingerprint and fraud detection scripts, so it is not replayed over
 * HTTP: the page runs as in a browser, the app only types the ID and password into `#loginId`/`#password` and presses
 * `#submit-btn`, like the user would. The rest is the same as a manual login: Tokyo Gas's server redeems the code at
 * `…/auth/redirect` and sets the session cookies, and the members page after it is stopped before it loads.
 *
 * Every Tokyo Gas cookie is removed from the WebView before and after, so the app's in-memory session is the only copy.
 */
class TokyoGasWebLogin(context: Context) : GasLogin {
    private val context = context.applicationContext

    override suspend fun login(loginId: String, password: String): String =
        withContext(Dispatchers.Main) {
            val hosts = mutableSetOf(TokyoGasAuth.HOST)
            clearTokyoGasWebSession(hosts)
            val cookies = CompletableDeferred<String>()
            val webView = createWebView(hosts, cookies)
            try {
                withTimeoutOrNull(TIMEOUT_MS) { drive(webView, loginId, password, cookies) }
                    ?: throw GasLoginFailedException(GasLoginFailure.PageNotRecognised, "timeout")
            } finally {
                webView.stopLoading()
                webView.destroy()
                clearTokyoGasWebSession(hosts)
            }
        }

    /** Fills in and submits the login form, then waits for the session cookies or a page that explains the failure. */
    private suspend fun drive(
        webView: WebView,
        loginId: String,
        password: String,
        cookies: CompletableDeferred<String>,
    ): String {
        val fill = fillScript(loginId, password)
        var page = LoginPageState()
        // Until the form is submitted (the page and its scripts load first).
        val fillUntil = System.currentTimeMillis() + FILL_TIMEOUT_MS
        while (!page.submitted) {
            if (cookies.isCompleted) return cookies.await()
            page = webView.evaluate(fill)
            if (page.passwordChange) throw GasLoginFailedException(GasLoginFailure.PasswordChangeRequired)
            if (System.currentTimeMillis() > fillUntil) {
                throw GasLoginFailedException(GasLoginFailure.PageNotRecognised, page.error)
            }
            if (!page.submitted) delay(POLL_MS)
        }
        // After submitting: the redirect chain ends with the cookies, or the page shows why not.
        var withoutForm = 0
        while (true) {
            delay(POLL_MS)
            if (cookies.isCompleted) return cookies.await()
            page = webView.evaluate(INSPECT_SCRIPT)
            when {
                page.passwordChange -> throw GasLoginFailedException(GasLoginFailure.PasswordChangeRequired)
                page.loginForm && page.error != null ->
                    throw GasLoginFailedException(GasLoginFailure.Rejected, page.error)

                page.loginForm -> withoutForm = 0
                ++withoutForm * POLL_MS >= UNKNOWN_PAGE_MS ->
                    throw GasLoginFailedException(GasLoginFailure.InteractionRequired)
            }
        }
    }

    private suspend fun WebView.evaluate(script: String): LoginPageState =
        suspendCancellableCoroutine { continuation ->
            evaluateJavascript(script) {
                if (continuation.isActive) continuation.resume(
                    parsePageState(it)
                )
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(
        hosts: MutableSet<String>,
        cookies: CompletableDeferred<String>
    ): WebView =
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Never shown, but laid out at a phone size so the page renders as it would on screen.
            measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, WIDTH, HEIGHT)
            var sawRedirect = false

            // Returns true if the URL must not load: the members page right after the login.
            fun intercept(url: String?): Boolean {
                if (url == null) return false
                if (cookies.isCompleted) return true
                url.toUri().host?.let(hosts::add)
                if (TokyoGasAuth.isRedirect(url)) {
                    TokyoGasAuth.redirectError(url)?.let {
                        cookies.completeExceptionally(
                            GasLoginFailedException(
                                GasLoginFailure.Rejected,
                                it
                            )
                        )
                        return true
                    }
                    sawRedirect = true
                    return false // Tokyo Gas's server redeems the code here
                }
                if (!sawRedirect || !TokyoGasAuth.isMembersPage(url)) return false
                cookies.complete(
                    CookieManager.getInstance().getCookie(TokyoGasAuth.ORIGIN).orEmpty()
                )
                return true
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean =
                    request.isForMainFrame && intercept(request.url.toString())

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (intercept(url)) view.stopLoading()
                }
            }
            loadUrl(TokyoGasAuth.LOGIN_URL)
        }

    private companion object {
        const val TIMEOUT_MS = 60_000L
        const val FILL_TIMEOUT_MS = 30_000L
        const val POLL_MS = 500L

        /** How long a page without the login form may stay after submitting before it counts as unknown. */
        const val UNKNOWN_PAGE_MS = 10_000L
        const val WIDTH = 1080
        const val HEIGHT = 2000
    }
}

/** What the page scripts report (see [fillScript] and [INSPECT_SCRIPT]). */
@Serializable
internal data class LoginPageState(
    /** The form was filled in and the button pressed. */
    val submitted: Boolean = false,
    /** The ID/password form is on the page. */
    val loginForm: Boolean = false,
    /** The page asks for a new password. */
    val passwordChange: Boolean = false,
    /** Visible error text on the page, if any. */
    val error: String? = null,
)

private val pageJson = Json { ignoreUnknownKeys = true }

/**
 * Parses `evaluateJavascript`'s result: the script returns a JSON string, which arrives JSON-encoded once more. Anything
 * unreadable (e.g. no page loaded yet) counts as a page without the form.
 */
internal fun parsePageState(raw: String?): LoginPageState = runCatching {
    pageJson.decodeFromString<LoginPageState>(pageJson.decodeFromString<String>(raw!!))
}.getOrDefault(LoginPageState())

/** Reads the page's state; shared by both scripts. */
private const val STATE_JS = """
  var visible = function (e) { return !!(e && e.offsetParent); };
  var state = function (submitted) {
    var errors = Array.prototype.slice.call(document.querySelectorAll('[id^="err"], [class*="error"], [role="alert"]'))
      .filter(function (e) { return visible(e) && e.tagName !== 'INPUT'; })
      .map(function (e) { return (e.innerText || '').replace(/\s+/g, ' ').trim(); })
      .filter(function (t) { return t.length > 0; });
    return JSON.stringify({
      submitted: submitted,
      loginForm: !!document.getElementById('loginId') && !!document.getElementById('password'),
      passwordChange: visible(document.getElementById('newPassword')),
      error: errors.length ? errors.join(' / ').slice(0, 300) : null
    });
  };
"""

internal const val INSPECT_SCRIPT = "(function () {$STATE_JS return state(false); })()"

/**
 * One step of filling in the login form: types the ID and password (through the native setter plus input events, as
 * typing would) the first time the form is there, and presses the login button once the page has enabled it. Run
 * repeatedly until it reports `submitted`.
 */
internal fun fillScript(loginId: String, password: String): String {
    val id = Json.encodeToString(String.serializer(), loginId)
    val pw = Json.encodeToString(String.serializer(), password)
    return """(function () {$STATE_JS
  var id = document.getElementById('loginId'), pw = document.getElementById('password');
  var button = document.getElementById('submit-btn');
  if (!id || !pw || !button) return state(false);
  if (!window.__tsFilled) {
    var setValue = function (el, v) {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, v);
      ['input', 'change', 'keyup', 'blur'].forEach(function (t) { el.dispatchEvent(new Event(t, { bubbles: true })); });
    };
    setValue(id, $id);
    setValue(pw, $pw);
    window.__tsFilled = true;
  }
  if (button.disabled) return state(false);
  button.click();
  return state(true);
})()"""
}

/**
 * Removes the cookies of Tokyo Gas's login from the WebView: those of [hosts] (the pages the login went through) and of
 * the `tokyo-gas.co.jp` domain. Unlike TEPCO's `clearTepcoWebSession`, it leaves other sites' cookies alone, so a kept
 * TEPCO login stays. `CookieManager` has no per-site removal, so each cookie is overwritten with an expired one.
 */
fun clearTokyoGasWebSession(hosts: Collection<String> = listOf(TokyoGasAuth.HOST)) {
    val manager = CookieManager.getInstance()
    (hosts + TokyoGasAuth.HOST).toSet().forEach { host ->
        val url = "https://$host/"
        val names = manager.getCookie(url).orEmpty().split(';')
            .map { it.substringBefore('=').trim() }
            .filter { it.isNotEmpty() }
        val domains = buildList {
            add(null)
            if (host.endsWith(".tokyo-gas.co.jp")) add(".tokyo-gas.co.jp")
            add(".$host")
        }
        names.forEach { name ->
            domains.forEach { domain ->
                val attributes = if (domain == null) "" else "; Domain=$domain"
                manager.setCookie(url, "$name=; Max-Age=0; Path=/$attributes")
            }
        }
    }
    manager.flush()
}
