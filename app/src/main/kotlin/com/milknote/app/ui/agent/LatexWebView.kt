package com.milknote.app.ui.agent

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.milknote.app.ui.theme.LocalMilkDark
import kotlinx.coroutines.delay

/**
 * 离线 KaTeX 容器(WebView):100% 不联网,脚本与字体全部来自 `assets/katex`。
 *
 * 两个入口:
 * - [KaTeXFormulaWebView] 单条公式,高度由 `window.MilkMathBridge.onHeight` 回报,Compose 据此精确量高;
 * - [MarkdownKaTeXWebView] 整篇 Markdown + LaTeX 的完整预览页,可整页自滚动或按内容高度量高。
 *
 * 任何失败(无 WebView / 加载异常 / 渲染进程崩溃)都回落到原生排版,不会出现空白或未渲染的源码。
 *
 * 高度单位:HTML 带 `<meta name="viewport" content="width=device-width, initial-scale=1">`,
 * 因此 `document.body.scrollHeight` 回报的是 **CSS px**,在 Android 上等价于 **dp**——
 * 直接 `dp` 消费,绝不能再经由 density 换算(否则会被除以屏幕密度,裁掉公式下半截)。
 */
private const val KATEX_BASE_URL = "file:///android_asset/katex/"

/** 高度尾随留白(CSS px = dp),抵消 DOM 横向滚动槽与亚像素舍入 */
private const val HEIGHT_SLACK_DP = 2

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KaTeXFormulaWebView(
    latex: String,
    displayMode: Boolean = true,
    dark: Boolean = LocalMilkDark.current,
    modifier: Modifier = Modifier,
    onReady: (Int) -> Unit = {},
    onFailed: () -> Unit = {},
) {
    val html = remember(latex, displayMode, dark) {
        MarkdownHtmlRenderer.renderMathFormulaHtml(latex, displayMode, dark)
    }
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnFailed by rememberUpdatedState(onFailed)
    // 公式变化时归零(外层有原生占位兜住);主题切换保留上一次高度,避免明暗切换瞬间塌陷
    var heightDp by remember(latex) { mutableIntStateOf(0) }

    AndroidView(
        modifier = modifier.height(if (heightDp > 0) (heightDp + HEIGHT_SLACK_DP).dp else 1.dp),
        factory = { context ->
            try {
                WebView(context).apply {
                    configureOffline(this, scrollable = false)
                    addJavascriptInterface(
                        MathHeightBridge { h ->
                            if (h > 0) {
                                heightDp = h
                                currentOnReady(h)
                            }
                        },
                        "MilkMathBridge",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) currentOnFailed()
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail,
                        ): Boolean {
                            currentOnFailed()
                            return true
                        }
                    }
                    tag = html
                    loadDataWithBaseURL(KATEX_BASE_URL, html, "text/html", "utf-8", null)
                }
            } catch (t: Throwable) {
                currentOnFailed()
                View(context)
            }
        },
        update = { view ->
            // 公式或主题变化都必须重载(主题换的是 CSS 变量,不重载不会生效)
            if (view is WebView && view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(KATEX_BASE_URL, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { view -> (view as? WebView)?.destroy() },
    )
}

/**
 * 整篇 Markdown + LaTeX 的离线预览页。
 *
 * @param selfScroll true = 用调用方给的高度(modifier,如 `fillMaxSize()`)并允许页内纵向滚动,
 *   适合固定高度的悬浮预览窗;false = 按内容高度 `heightDp` 量高,嵌入 Compose 可滚动 Column。
 * @param onHeight 内容高度(CSS px,等价 dp);`selfScroll = true` 时仅作信息回报。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownKaTeXWebView(
    markdown: String,
    dark: Boolean = LocalMilkDark.current,
    modifier: Modifier = Modifier,
    selfScroll: Boolean = true,
    onHeight: (Int) -> Unit = {},
) {
    val html = remember(markdown, dark) { MarkdownHtmlRenderer.renderFullHtml(markdown, dark) }
    val currentOnHeight by rememberUpdatedState(onHeight)
    var failed by remember { mutableStateOf(false) }
    // ready 与高度分开:超时兜底看 ready,量高看 heightDp
    var ready by remember(html) { mutableStateOf(false) }
    var heightDp by remember(html) { mutableIntStateOf(0) }

    // KaTeX 脚本被拦(极端机型/策略)时,1.8s 内收不到首帧就回落到原生块渲染
    LaunchedEffect(html) {
        if (!ready) {
            delay(1800)
            if (!ready) failed = true
        }
    }

    if (failed) {
        // 回落原生块渲染;selfScroll 模式下给同样的可滚动容器,保证整篇能看完
        MilkMarkdownView(
            text = markdown,
            modifier = if (selfScroll) modifier.verticalScroll(rememberScrollState()) else modifier,
            streaming = false,
        )
        return
    }

    AndroidView(
        modifier = if (!selfScroll && heightDp > 0) {
            modifier.height((heightDp + HEIGHT_SLACK_DP).dp)
        } else {
            modifier
        },
        factory = { context ->
            try {
                WebView(context).apply {
                    configureOffline(this, scrollable = selfScroll)
                    addJavascriptInterface(
                        MathHeightBridge { h ->
                            if (h > 0) {
                                heightDp = h
                                ready = true
                                currentOnHeight(h)
                            }
                        },
                        "MilkMathBridge",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) failed = true
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail,
                        ): Boolean {
                            failed = true
                            return true
                        }
                    }
                    tag = html
                    loadDataWithBaseURL(KATEX_BASE_URL, html, "text/html", "utf-8", null)
                }
            } catch (t: Throwable) {
                failed = true
                View(context)
            }
        },
        update = { view ->
            if (view is WebView && view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(KATEX_BASE_URL, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { view -> (view as? WebView)?.destroy() },
    )
}

/**
 * 禁网络、只读 assets、无缩放;透明底并入 Compose 纸面。
 * [scrollable] = true 时保留纵向滚动条与惯性滚动(整页预览窗),否则按内容量高、不滚动。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun configureOffline(webView: WebView, scrollable: Boolean) {
    webView.setBackgroundColor(AndroidColor.TRANSPARENT)
    webView.settings.apply {
        javaScriptEnabled = true
        allowFileAccess = true
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        blockNetworkLoads = true
        javaScriptCanOpenWindowsAutomatically = false
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = false
        domStorageEnabled = false
        databaseEnabled = false
        textZoom = 100
    }
    webView.isVerticalScrollBarEnabled = scrollable
    webView.isHorizontalScrollBarEnabled = false
    webView.overScrollMode = if (scrollable) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
}

/** JS 回调在 WebView 线程,统一 post 回主线程再写 Compose 状态;参数为 CSS px(等价 dp) */
private class MathHeightBridge(private val onHeightDp: (Int) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onHeight(height: Int) {
        main.post { onHeightDp(height) }
    }
}
