package com.milknote.agent

/**
 * 端点解析(AGENT-BACKEND §3.5):把用户填的 baseUrl + 协议翻译成实际 URL。
 *
 * 为什么需要它:同一个 baseUrl 在不同协议下路径不同(OpenAI 兼容 `/chat/completions`、
 * Anthropic `/v1/messages`、Responses `/responses`);而 OpenCode Go 一个 Key 通吃三家协议,
 * 路径前缀也各不相同(`/inference/go/openai/v1`、`/inference/go/anthropic/v1` …)。
 * 装配层(设置页「实际请求地址」提示)也复用同一套规则,避免 UI 与请求层各说各话。
 *
 * 支持的 baseUrl 写法(末尾斜杠自动归一):
 * - `https://opencode.ai/inference/go`、`…/inference/go/v1`、`…/inference/go/openai/v1`(现行)
 * - `https://opencode.ai/zen/go`、`…/zen/go/v1`(旧版 Zen 路径,仍在用)
 * - 任意标准端点:`https://api.openai.com/v1`、裸域、中转前缀(`…/anthropic`)
 */
object EndpointResolver {

    private const val OPENCODE_GO_SEG = "/inference/go"
    private const val OPENCODE_ZEN_SEG = "/zen/go"

    private val OPENCODE_HOSTS = listOf("opencode.ai", "opencode.ac.cn")

    /** 是否 OpenCode 域名(决定是否补 x-opencode-session 头) */
    fun isOpenCodeBaseUrl(baseUrl: String): Boolean {
        val host = hostOf(baseUrl)?.lowercase() ?: return false
        return OPENCODE_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /** 对话端点(chat = 流式补全,models = 模型列表) */
    fun resolveChatUrl(baseUrl: String, protocol: Protocol): String {
        val base = normalize(baseUrl)
        val root = openCodeRoot(base)
        if (root != null) {
            val zen = root.contains(OPENCODE_ZEN_SEG)
            return when (protocol) {
                Protocol.OPENAI_COMPATIBLE ->
                    if (zen) "$root/v1/chat/completions" else "$root/openai/v1/chat/completions"
                Protocol.OPENAI_RESPONSES ->
                    if (zen) "$root/v1/responses" else "$root/openai/v1/responses"
                Protocol.ANTHROPIC ->
                    if (zen) "$root/v1/messages" else "$root/anthropic/v1/messages"
            }
        }
        return when (protocol) {
            Protocol.OPENAI_COMPATIBLE -> "$base/chat/completions"
            Protocol.OPENAI_RESPONSES -> "$base/responses"
            Protocol.ANTHROPIC -> "${v1Prefix(base)}/messages"
        }
    }

    fun resolveModelsUrl(baseUrl: String, protocol: Protocol): String {
        val base = normalize(baseUrl)
        val root = openCodeRoot(base)
        if (root != null) return "$root/v1/models"
        return when (protocol) {
            Protocol.ANTHROPIC -> "${v1Prefix(base)}/models"
            Protocol.OPENAI_COMPATIBLE, Protocol.OPENAI_RESPONSES -> "$base/models"
        }
    }

    // ---- 内部 ----

    private fun normalize(baseUrl: String): String = baseUrl.trim().trimEnd('/')

    /** `…/v1` 已是前缀则不重复补(Anthropic 三种写法归一) */
    private fun v1Prefix(base: String): String = if (base.endsWith("/v1")) base else "$base/v1"

    /**
     * 截出 OpenCode Go 的根(`…/inference/go` 或 `…/zen/go`);
     * 协议段(`/openai/v1`、`/anthropic/v1`、`/v1` 等)与用户多余的后缀一律丢弃。
     */
    private fun openCodeRoot(base: String): String? {
        for (seg in listOf(OPENCODE_GO_SEG, OPENCODE_ZEN_SEG)) {
            val i = base.indexOf(seg)
            if (i >= 0) return base.substring(0, i + seg.length)
        }
        return null
    }

    private fun hostOf(baseUrl: String): String? {
        val raw = baseUrl.trim()
        if (raw.isEmpty()) return null
        runCatching { java.net.URI(raw) }.getOrNull()?.host
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        // 无 scheme 的简写(opencode.ai/inference/go)
        val hostPart = raw.substringBefore('/').substringBefore(':')
        return hostPart.takeIf { it.isNotBlank() && !it.contains('@') }
    }
}
