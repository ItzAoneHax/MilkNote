package com.milknote.app.ui.agent.media

/**
 * 页码范围解析/压缩(纯 Kotlin,无 Android 依赖,JVM 单测直接覆盖)。
 *
 * 输入容错:半角/全角逗号、分号、空格、顿号,连字符(`-`)、en dash(`–`)、em dash(`—`)、`~`、`～` 都视作区间;
 * `3-1` 这类倒序区间自动归一为 `1-3`。
 *
 * 约定:
 * - 空白输入不是错误,返回 `Result.success(emptyList())`(调用方据此判断「未选择页码」)。
 * - 越界(`<=0` 或 `> maxPage`)返回 `Result.failure(IllegalArgumentException("页码超出范围 (1-$maxPage)"))`。
 * - 非数字垃圾返回 `Result.failure(IllegalArgumentException("页码格式无效，示例: 1-3, 5"))`。
 * - 成功结果永远按 1 基页码升序、去重。
 */
object PageRangeParser {

    /** 区间分隔符(连字符族统一成 `-` 后再解析) */
    private val RANGE_SEPARATORS = charArrayOf('–', '—', '~', '～', '－', 'ー')

    /** token 分隔符(半角/全角逗号、分号、顿号、空白) */
    private val TOKEN_SEPARATORS = Regex("[,\\uFF0C\\u3001;\\uFF1B\\s]+")

    private val SINGLE_PATTERN = Regex("^\\d{1,6}$")
    private val RANGE_PATTERN = Regex("^(\\d{1,6})-(\\d{1,6})$")

    private const val ERROR_RANGE = "页码格式无效，示例: 1-3, 5"

    /** 解析页码范围表达式;见类注释的错误/边界约定。 */
    fun parse(input: String, maxPage: Int): Result<List<Int>> {
        val normalized = normalize(input)
        if (normalized.isBlank()) return Result.success(emptyList())

        val pages = LinkedHashSet<Int>()
        for (token in normalized.split(TOKEN_SEPARATORS)) {
            val piece = token.trim()
            if (piece.isEmpty()) continue

            val single = SINGLE_PATTERN.matchEntire(piece)
            if (single != null) {
                val page = piece.toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException(ERROR_RANGE))
                validate(page, maxPage)?.let { return Result.failure(it) }
                pages += page
                continue
            }

            val range = RANGE_PATTERN.matchEntire(piece)
                ?: return Result.failure(IllegalArgumentException(ERROR_RANGE))
            val start = range.groupValues[1].toIntOrNull()
                ?: return Result.failure(IllegalArgumentException(ERROR_RANGE))
            val end = range.groupValues[2].toIntOrNull()
                ?: return Result.failure(IllegalArgumentException(ERROR_RANGE))
            val from = minOf(start, end)
            val to = maxOf(start, end)
            validate(from, maxPage)?.let { return Result.failure(it) }
            validate(to, maxPage)?.let { return Result.failure(it) }
            for (page in from..to) pages += page
        }
        return Result.success(pages.sorted())
    }

    /** `[1,2,3,5,7,8]` → `"1-3, 5, 7-8"`;空集合 → `""` */
    fun format(pages: Collection<Int>): String {
        val sorted = pages.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val sb = StringBuilder()
        var runStart = sorted.first()
        var prev = runStart
        var runLength = 1
        for (i in 1 until sorted.size) {
            val page = sorted[i]
            if (page == prev + 1) {
                prev = page
                runLength++
                continue
            }
            appendRun(sb, runStart, prev, runLength)
            runStart = page
            prev = page
            runLength = 1
        }
        appendRun(sb, runStart, prev, runLength)
        return sb.toString()
    }

    /** `[2]` → `"第 2 页"`;`[1,2,3,5]` → `"第 1-3, 5 页"`;空集合 → `""` */
    fun formatChineseLabel(pages: Collection<Int>): String {
        val compact = format(pages)
        if (compact.isEmpty()) return ""
        return "第 $compact 页"
    }

    private fun appendRun(sb: StringBuilder, start: Int, end: Int, length: Int) {
        if (sb.isNotEmpty()) sb.append(", ")
        if (length <= 1) sb.append(start) else sb.append(start).append('-').append(end)
    }

    private fun validate(page: Int, maxPage: Int): IllegalArgumentException? =
        if (page in 1..maxPage) null else IllegalArgumentException("页码超出范围 (1-$maxPage)")

    private fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(if (ch in RANGE_SEPARATORS) '-' else ch)
        }
        // `1 - 3` / `1- 3` 也按区间处理:先吃掉连字符两侧空白,再切分 token
        return sb.toString()
            .replace(Regex("\\s*-\\s*"), "-")
            .replace(TOKEN_SEPARATORS, ",")
    }
}
