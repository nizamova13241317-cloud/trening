package com.example

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val context = LocalContext.current
        var pendingHtmlContent by remember { mutableStateOf("") }
        var webViewRef by remember { mutableStateOf<WebView?>(null) }

        val createDocumentLauncher =
          rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/html")
          ) { uri ->
            if (uri != null) {
              runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                  stream.write(pendingHtmlContent.toByteArray(StandardCharsets.UTF_8))
                }
              }.onSuccess {
                webViewRef?.post {
                  webViewRef?.evaluateJavascript(
                    "window.__showSandboxToast && window.__showSandboxToast('Файл index.html збережено')",
                    null
                  )
                }
              }.onFailure {
                webViewRef?.post {
                  webViewRef?.evaluateJavascript(
                    "window.__showSandboxToast && window.__showSandboxToast('Помилка збереження')",
                    null
                  )
                }
              }
            }
          }

        val bridge = remember(context) {
          SandboxAndroidBridge(
            context = context,
            onRequestSaveHtml = { html ->
              pendingHtmlContent = html
              runOnUiThread {
                createDocumentLauncher.launch("index.html")
              }
            }
          )
        }

        DisposableEffect(Unit) {
          onDispose {
            webViewRef?.destroy()
            webViewRef = null
          }
        }

        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .windowInsetsPadding(WindowInsets.safeDrawing)
              .imePadding()
        ) {
          AndroidView(
            modifier = Modifier.fillMaxSize().testTag("html_sandbox_webview"),
            factory = { ctx ->
              createSandboxWebView(ctx, bridge).also { webViewRef = it }
            }
          )
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createSandboxWebView(
  context: Context,
  bridge: SandboxAndroidBridge
): WebView {
  return WebView(context).apply {
    layoutParams =
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    isFocusable = true
    isFocusableInTouchMode = true
    settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      databaseEnabled = true
      allowFileAccess = true
      builtInZoomControls = false
      displayZoomControls = false
      setSupportZoom(false)
    }
    webChromeClient = WebChromeClient()
    webViewClient = WebViewClient()
    addJavascriptInterface(bridge, "AndroidBridge")
    loadUrl("file:///android_asset/index.html")
  }
}

private class SandboxAndroidBridge(
  private val context: Context,
  private val onRequestSaveHtml: (String) -> Unit
) {
  @JavascriptInterface
  fun copyText(text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("HTML Code", text))
  }

  @JavascriptInterface
  fun saveHtmlFile(html: String) {
    onRequestSaveHtml(html)
  }
}
