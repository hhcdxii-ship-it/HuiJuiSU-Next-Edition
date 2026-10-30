package com.huijui.sunext.edition.feature.module

import com.huijui.sunext.edition.core.ui.language.ZakoText

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.huijui.sunext.edition.core.ui.component.GlassCard
import com.huijui.sunext.edition.core.utils.AppSettings

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ModuleWebUIScreen(
    moduleName: String,
    htmlPath: String,
    onBack: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    webViewClient = WebViewClient()
                    loadUrl("file://$htmlPath")
                    webView = this
                }
            },
            update = { view ->
                if (view.url != "file://$htmlPath") {
                    view.loadUrl("file://$htmlPath")
                }
            }
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlassCard(
                modifier = Modifier.wrapContentSize(),
                onClick = onBack
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    ZakoText(moduleName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            GlassCard(
                modifier = Modifier.size(40.dp),
                onClick = { webView?.reload() }
            ) {
                Icon(Icons.Default.Refresh, null, tint = AppSettings.accentColor.color, modifier = Modifier.size(20.dp))
            }
        }
    }
}
