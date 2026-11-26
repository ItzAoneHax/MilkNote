package com.milknote.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 默认联网搜索客户端(零第三方依赖,仅 HttpURLConnection)。
 *
 * settings:
 * - engine: auto(默认)/ bing / duckduckgo / custom
 * - custom_url: custom 引擎的地址模板,必须含 {query}
 * - max_results: 默认结果数(参数优先)
 */
class DefaultWebSearchPort : WebSearchPort {

    override suspend fun search(
        query: String,
        maxResults: Int,
        settings: Map<String, String>,
    ): Result<List<WebSearchItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val q = query.trim()
            require(q.isNotEmpty()) { "搜索关键词为空" }
            val limit = maxResults.coerceIn(1, 10)
            val engine = settings["engine"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "auto"
            val items = when (engine) {
                "bing" -> searchBing(q, limit)
                "duckduckgo" -> searchDuckDuckGo(q, limit)
                "custom" -> searchCustom(q, limit, settings["custom_url"])
                else -> {
                    // auto:先 Bing(中文结果好),失败或空则 DuckDuckGo
                    val bing = runCatching { searchBing(q, limit) }.getOrDefault(emptyList())
                    if (bing.isNotEmpty()) bing else searchDuckDuckGo(q, limit)
                }
            }
            items.take(limit)
        }
    }

    private fun searchBing(query: String, limit: Int): List<WebSearchItem> {
        val url = "https://cn.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8")
        val html = fetch(url)
        val items = WebSearchHtml.extractBing(html, limit)
        return if (items.isNotEmpty()) items else WebSearchHtml.extractGeneric(html, limit)
    }

    private fun searchDuckDuckGo(query: String, limit: Int): List<WebSearchItem> {
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
        val html = fetch(url)
        val items = WebSearchHtml.extractDuckDuckGo(html, limit)
        return if (items.isNotEmpty()) items else WebSearchHtml.extractGeneric(html, limit)
    }

    private fun searchCustom(query: String, limit: Int, template: String?): List<WebSearchItem> {
        val urlTemplate = template?.takeIf { it.contains("{query}") }
            ?: throw IllegalArgumentException("custom 引擎需要在设置中填写包含 {query} 的 custom_url")
        val url = urlTemplate.replace("{query}", URLEncoder.encode(query, "UTF-8"))
        val body = fetch(url)
        // 支持 JSON API 与 HTML 页面两种自定义源
        val fromJson = runCatching { WebSearchHtml.extractJson(body) }.getOrDefault(emptyList())
        val items = if (fromJson.isNotEmpty()) fromJson else WebSearchHtml.extractGeneric(body, limit)
        return items.take(limit)
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            conn.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
            )
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }.getOrDefault("")
                throw IllegalStateException("搜索请求失败(HTTP $code)${body.take(200)}")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0 Mobile Safari/537.36 MilkNote/1.0"
    }
}

/** HTML/JSON 结果提取(独立出来便于 JVM 单测,不发网络请求) */
internal object WebSearchHtml {

    private val flagSet = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val bingBlock = Regex("""<li[^>]*class="[^"]*\bb_algo\b[^"]*"[^>]*>([\s\S]*?)</li>""", flagSet)
    private val bingAnchor = Regex(
        """<h2[^>]*>[\s\S]*?<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val bingSnippet = Regex("""<p[^>]*>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)

    private val ddgAnchor = Regex(
        """<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val ddgSnippet = Regex(
        """<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE,
    )

    private val anyAnchor = Regex(
        """<a[^>]+href="(https?://[^"]+)"[^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE,
    )

    private val skipHosts = listOf(
        "bing.com", "microsoft.com", "duckduckgo.com", "go.microsoft.com",
        "support.microsoft.com", "privacy.microsoft.com",
    )

    fun extractBing(html: String, limit: Int): List<WebSearchItem> {
        val items = ArrayList<WebSearchItem>()
        for (block in bingBlock.findAll(html)) {
            val chunk = block.groupValues[1]
            val a = bingAnchor.find(chunk) ?: continue
            val url = normalizeUrl(a.groupValues[1])
            val title = stripTags(a.groupValues[2])
            val snippet = bingSnippet.find(chunk)?.groupValues?.get(1)?.let { stripTags(it) } ?: ""
            if (url.startsWith("http") && title.isNotBlank()) {
                items += WebSearchItem(title.take(200), url, snippet.take(400))
            }
            if (items.size >= limit) break
        }
        return items.distinctBy { it.url }
    }

    fun extractDuckDuckGo(html: String, limit: Int): List<WebSearchItem> {
        val items = ArrayList<WebSearchItem>()
        var cursor = 0
        while (items.size < limit) {
            val m = ddgAnchor.find(html, cursor) ?: break
            cursor = m.range.last + 1
            val url = normalizeUrl(m.groupValues[1])
            val title = stripTags(m.groupValues[2])
            val snippet = ddgSnippet.find(html, cursor)?.groupValues?.get(1)?.let { stripTags(it) } ?: ""
            if (url.startsWith("http") && title.isNotBlank()) {
                items += WebSearchItem(title.take(200), url, snippet.take(400))
            }
        }
        return items.distinctBy { it.url }
    }

    /** 通用兜底:任何 HTML 里抓取正文链接(跳过导航/搜索自家人站点) */
    fun extractGeneric(html: String, limit: Int = 10): List<WebSearchItem> {
        val items = ArrayList<WebSearchItem>()
        for (m in anyAnchor.findAll(html)) {
            if (items.size >= limit) break
            val url = normalizeUrl(m.groupValues[1])
            val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
            if (skipHosts.any { host == it || host.endsWith(".$it") }) continue
            val title = stripTags(m.groupValues[2])
            if (title.length < 6 || title.length > 240) continue
            items += WebSearchItem(title, url, "")
        }
        return items.distinctBy { it.url }
    }

    /** 自定义 JSON 源:递归找对象数组,读取 title/name、url/link/href、snippet/description */
    fun extractJson(text: String): List<WebSearchItem> {
        val root = runCatching { BaseHttpProvider.json.parseToJsonElement(text) }.getOrNull()
            ?: return emptyList()
        val arrays = ArrayList<JsonArray>()
        fun collect(el: JsonElement?) {
            when (el) {
                is JsonArray -> arrays += el
                is JsonObject -> el.values.forEach { collect(it) }
                else -> Unit
            }
        }
        collect(root)
        val items = ArrayList<WebSearchItem>()
        for (arr in arrays) {
            for (el in arr) {
                val o = el as? JsonObject ?: continue
                val title = o.strOrNull("title") ?: o.strOrNull("name") ?: continue
                val url = o.strOrNull("url") ?: o.strOrNull("link") ?: o.strOrNull("href") ?: continue
                val snippet = o.strOrNull("snippet") ?: o.strOrNull("description")
                    ?: o.strOrNull("summary") ?: o.strOrNull("content") ?: ""
                if (url.startsWith("http") && title.isNotBlank()) {
                    items += WebSearchItem(
                        stripTags(title).take(200),
                        url,
                        stripTags(snippet).take(400),
                    )
                }
            }
        }
        return items.distinctBy { it.url }
    }

    fun stripTags(raw: String): String {
        val withoutBlocks = Regex(
            """<(script|style|noscript)[^>]*>[\s\S]*?</\1>""",
            RegexOption.IGNORE_CASE,
        ).replace(raw, " ")
        val noTags = withoutBlocks.replace(Regex("""<[^>]+>"""), " ")
        return decodeEntities(noTags).replace(Regex("""\s+"""), " ").trim()
    }

    fun decodeEntities(raw: String): String {
        var s = raw
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&hellip;", "…")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
        s = Regex("""&#x([0-9a-fA-F]+);""").replace(s) { m ->
            codePoint(m.groupValues[1].toIntOrNull(16))
        }
        s = Regex("""&#(\d+);""").replace(s) { m ->
            codePoint(m.groupValues[1].toIntOrNull())
        }
        return s
    }

    private fun codePoint(cp: Int?): String {
        if (cp == null || cp <= 0 || cp > 0x10FFFF) return ""
        return String(Character.toChars(cp))
    }

    /** DuckDuckGo 跳转链接还原 + 协议相对地址补全 */
    fun normalizeUrl(raw: String): String {
        var s = decodeEntities(raw.trim())
        if (s.startsWith("//")) s = "https:$s"
        if (s.contains("uddg=")) {
            val encoded = s.substringAfter("uddg=").substringBefore('&')
            val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
            if (!decoded.isNullOrBlank() && decoded.startsWith("http")) return decoded
        }
        return s
    }
}
