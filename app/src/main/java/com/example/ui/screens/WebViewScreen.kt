package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.service.AndroidInterface
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val urlState = viewModel.webViewUrlFlow.collectAsState(initial = "")
    val refreshTrigger by viewModel.refreshWebViewTrigger.collectAsState(initial = 0)
    
    var isLoading by remember { mutableStateOf(false) }
    var progressVal by remember { mutableIntStateOf(0) }
    
    var webErrorOccurred by remember { mutableStateOf(false) }
    var tryingCache by remember { mutableStateOf(false) }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var retryAttemptState by remember { mutableIntStateOf(0) }
    var refreshTriggerState by remember { mutableIntStateOf(0) }

    // Start auto reconnection logic when connection is broken
    LaunchedEffect(webErrorOccurred) {
        while (webErrorOccurred) {
            delay(4000)
            Log.i("WebViewScreen", "Reconnection ticker: retrying server connection...")
            retryAttempt++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("webview_screen_layout")
    ) {
        if (urlState.value.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "StreamPay no configurado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Por favor ingresa la dirección IP de tu servidor StreamPay en la sección de ajustes para empezar la reproducción.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.navigateTo(Screen.Config) },
                        modifier = Modifier.testTag("go_to_config_from_web_button")
                    ) {
                        Text("Ir a Ajustes")
                    }
                }
            }
        } else if (webErrorOccurred) {
            // Screen in pure black with server reconnecting status
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Conectando al servidor...",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Intentando reconexión con el servidor StreamPay en:\n${urlState.value}\nEsta pantalla se actualizará automáticamente hasta establecer conexión.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            webErrorOccurred = false
                            tryingCache = false
                            retryAttempt++
                        }
                    ) {
                        Text("Reintentar Ahora 🔄")
                    }
                }
            }
        } else {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = progressVal / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .testTag("webview_progress_bar"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            }

            AndroidView(
                factory = { ctx ->
                    try {
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                allowFileAccess = true
                                allowContentAccess = true
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    progressVal = 0
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }

                                @Deprecated("Deprecated in Java")
                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    Log.w("WebViewScreen", "onReceivedError ($errorCode): $description for URL: $failingUrl")
                                    if (failingUrl == urlState.value || failingUrl?.trimEnd('/') == urlState.value.trimEnd('/')) {
                                        if (!tryingCache) {
                                            tryingCache = true
                                            view?.settings?.cacheMode = WebSettings.LOAD_CACHE_ONLY
                                            view?.loadUrl(urlState.value)
                                        } else {
                                            webErrorOccurred = true
                                        }
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val failingUrl = request?.url?.toString() ?: ""
                                        val isMainFrame = request?.isForMainFrame ?: false
                                        Log.w("WebViewScreen", "onReceivedError M (${error?.errorCode}): ${error?.description} for: $failingUrl (mainFrame=$isMainFrame)")
                                        if (isMainFrame && (failingUrl == urlState.value || failingUrl.trimEnd('/') == urlState.value.trimEnd('/'))) {
                                            if (!tryingCache) {
                                                tryingCache = true
                                                view?.settings?.cacheMode = WebSettings.LOAD_CACHE_ONLY
                                                view?.loadUrl(urlState.value)
                                            } else {
                                                webErrorOccurred = true
                                            }
                                        }
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progressVal = newProgress
                                }
                            }

                            val androidBridge = AndroidInterface(context, viewModel)
                            addJavascriptInterface(androidBridge, "AndroidShare")
                            addJavascriptInterface(androidBridge, "AndroidInterface")

                            loadUrl(urlState.value)
                        }
                    } catch (t: Throwable) {
                        Log.e("WebViewScreen", "Fallo al inicializar componente WebView: ${t.message}", t)
                        android.widget.FrameLayout(ctx).apply {
                            val textView = android.widget.TextView(ctx).apply {
                                text = "El componente WebView no está disponible en este dispositivo.\nDetalles: ${t.message}."
                                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                                setTextColor(android.graphics.Color.parseColor("#FF5E5E"))
                                setPadding(48, 48, 48, 48)
                                layoutParams = android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                    android.view.Gravity.CENTER
                                )
                            }
                            addView(textView)
                        }
                    }
                },
                update = { webView ->
                    val castedWebView = webView as? WebView
                    if (castedWebView != null) {
                        // Process external refresh triggers
                        if (refreshTriggerState < refreshTrigger) {
                            refreshTriggerState = refreshTrigger
                            webErrorOccurred = false
                            tryingCache = false
                            castedWebView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                            castedWebView.reload()
                        }
                        
                        // Process auto retry connection triggers
                        if (retryAttemptState < retryAttempt) {
                            retryAttemptState = retryAttempt
                            webErrorOccurred = false
                            tryingCache = false
                            castedWebView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                            castedWebView.loadUrl(urlState.value)
                        }

                        val currentLoadedUrl = castedWebView.url ?: ""
                        if (currentLoadedUrl != urlState.value && urlState.value.isNotBlank() && currentLoadedUrl.isBlank()) {
                            castedWebView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                            castedWebView.loadUrl(urlState.value)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("webview_content_viewport")
            )
        }
    }
}
