package com.github.aw00987.tokyosandwich.electricity.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.electricity.data.TepcoAuth

private val RequestSaver = listSaver<TepcoAuth.Request, String>(
    save = { listOf(it.url, it.state, it.codeVerifier) },
    restore = { TepcoAuth.Request(it[0], it[1], it[2]) },
)

/**
 * TEPCO's own login page in a WebView. The user types their email and password (and later the one-time code) there
 * themselves; Android autofill and password managers can fill the fields. The app never reads the password.
 *
 * The redirect back to the official web app is caught before it loads, and its code goes to [onCode] together with
 * the PKCE verifier. [onError] gets Auth0's error code. The login state the app keeps is the token it redeems, so the
 * caller clears the WebView's session ([clearTepcoWebSession]) after every login.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TepcoLoginScreen(
    onCode: (code: String, codeVerifier: String) -> Unit,
    onError: (code: String) -> Unit,
    onBack: () -> Unit,
) {
    val request by rememberSaveable(stateSaver = RequestSaver) { mutableStateOf(TepcoAuth.newRequest()) }
    var progress by remember { mutableFloatStateOf(0f) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val currentRequest by rememberUpdatedState(request)
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnError by rememberUpdatedState(onError)
    BackHandler { if (canGoBack) webView?.goBack() else onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tepco_login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.back)
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // Let Android autofill / the user's password manager offer the saved TEPCO login.
                            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                            var handledState: String? = null

                            // Returns true if the URL is the login redirect, which must not load: the official web
                            // app would redeem the code itself.
                            fun intercept(url: String?): Boolean {
                                val attempt = currentRequest
                                val callback =
                                    url?.let { TepcoAuth.parseCallback(it, attempt.state) }
                                        ?: return false
                                if (handledState == attempt.state) return true
                                handledState = attempt.state
                                when (callback) {
                                    is TepcoAuth.Callback.Code ->
                                        currentOnCode(callback.code, attempt.codeVerifier)

                                    is TepcoAuth.Callback.Error -> currentOnError(callback.error)
                                }
                                return true
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean = intercept(request.url.toString())

                                override fun onPageStarted(
                                    view: WebView,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    if (intercept(url)) view.stopLoading()
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView,
                                    url: String?,
                                    isReload: Boolean
                                ) {
                                    canGoBack = view.canGoBack()
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress / 100f
                                }
                            }
                            loadUrl(currentRequest.url)
                            webView = this
                        }
                    },
                    onRelease = { it.destroy() },
                )
                if (progress < 1f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Forgets TEPCO's login session in the WebView (Auth0's cookies and the page's storage). The app's WebView is only
 * used for this login.
 */
fun clearTepcoWebSession() {
    CookieManager.getInstance().apply {
        removeAllCookies(null)
        flush()
    }
    WebStorage.getInstance().deleteAllData()
}
