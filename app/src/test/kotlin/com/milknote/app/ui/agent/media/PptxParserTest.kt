package com.milknote.app.ui.agent.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 用真实构造的 .pptx(Opc zip)/ .ppt(OLE2 记录流)验证解析器:
 * 放映顺序、标题、要点、表格、备注、内嵌图片,以及各种损坏兜底。
 */
class PptxParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ---------------------------------------------------------------- pptx 正常路径

    @Test
    fun `按 presentation 顺序解析标题 要点 表格 备注 与内嵌图片`() {
        val png = tinyPng()
        val file = writeZip(
            mapOf(
                "ppt/presentation.xml" to presentationXml("rId2", "rId1"),
                "ppt/_rels/presentation.xml.rels" to presentationRels(
                    "rId1" to "slides/slide1.xml",
                    "rId2" to "slides/slide2.xml",
                ),
                "ppt/slides/slide1.xml" to slideXml(
                    title = "第一页标题",
                    bullets = listOf("要点一", "要点二"),
                    table = listOf(listOf("科目", "分数"), listOf("数学", "95")),
                ),
                "ppt/slides/_rels/slide1.xml.rels" to relationshipsXml(
                    Triple("rId1", "$REL_BASE/notesSlide", "../notesSlides/notesSlide1.xml"),
                ),
                "ppt/notesSlides/notesSlide1.xml" to notesXml("第 9 页占位", "讲课时强调这里"),
                "ppt/slides/slide2.xml" to slideXml("第二页标题", listOf("要点甲")),
                "ppt/slides/_rels/slide2.xml.rels" to relationshipsXml(
                    Triple("rId2", "$REL_BASE/image", "../media/image1.png"),
                ),
                "ppt/media/image1.png" to png,
            ),
        )

        val document = PptxParser.parse(file)
        assertNull(document.warning)
        assertEquals(2, document.slides.size)

        // presentation.xml 声明的是 rId2 → rId1,所以 slide2 排第一
        val first = document.slides[0]
        assertEquals(1, first.index)
        assertEquals("第二页标题", first.title)
        assertEquals(listOf("要点甲"), first.bullets)
        assertEquals(1, first.images.size)
        assertEquals("ppt/media/image1.png", first.images[0].path)
        assertTrue(first.images[0].bytes.contentEquals(png))

        val second = document.slides[1]
        assertEquals(2, second.index)
        assertEquals("第一页标题", second.title)
        assertEquals(listOf("要点一", "要点二"), second.bullets)
        assertEquals(1, second.tables.size)
        assertEquals(listOf("科目", "分数"), second.tables[0][0])
        assertEquals(listOf("数学", "95"), second.tables[0][1])
        // sldNum 占位(幻灯片编号)不能被当成备注
        assertEquals("讲课时强调这里", second.notes)

        val text = second.toPlainText()
        assertTrue(text.startsWith("第 2 页"))
        assertTrue(text.contains("标题: 第一页标题"))
        assertTrue(text.contains("• 要点一"))
        assertTrue(text.contains("| 科目 | 分数 |"))
        assertTrue(text.contains("| --- | --- |"))
        assertTrue(text.contains("| 数学 | 95 |"))
        assertTrue(text.contains("演讲备注: 讲课时强调这里"))
    }

    @Test
    fun `同一段落里被格式切碎的 run 直接拼接`() {
        // 正文段落被加粗切成两个 run:不应在中间插入空格
        val xml = slideXml("标题", listOf()).replace(
            "<p:spTree>",
            """
            <p:spTree>
            <p:sp><p:nvSpPr><p:cNvPr id="7" name="Body"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr/>
            <p:txBody><a:bodyPr/><a:lstStyle/>
            <a:p><a:r><a:rPr b="1"/><a:t>要点</a:t></a:r><a:r><a:t>乙</a:t></a:r></a:p>
            </p:txBody></p:sp>
            """.trim(),
        )
        val file = writeZip(mapOf("ppt/slides/slide1.xml" to xml.toByteArray(Charsets.UTF_8)))
        val document = PptxParser.parse(file)
        assertEquals(1, document.slides.size)
        assertEquals("标题", document.slides[0].title)
        assertEquals(listOf("要点乙"), document.slides[0].bullets)
    }

    @Test
    fun `没有标题占位符时用第一个文本框首行当标题`() {
        val xml = slideXml(
            title = "",
            bullets = listOf("流程图讲解", "第一点", "第二点"),
            titlePlaceholder = false,
        )
        val file = writeZip(mapOf("ppt/slides/slide1.xml" to xml.toByteArray(Charsets.UTF_8)))

        val slide = PptxParser.parse(file).slides.single()
        assertEquals("流程图讲解", slide.title)
        assertEquals(listOf("第一点", "第二点"), slide.bullets)
    }

    @Test
    fun `缺少 presentation xml 时按 slide 文件序号排序`() {
        val entries = mapOf(
            "ppt/slides/slide10.xml" to slideXml("第十页", listOf()).toByteArray(Charsets.UTF_8),
            "ppt/slides/slide2.xml" to slideXml("第二页", listOf()).toByteArray(Charsets.UTF_8),
        )
        val zipBytes = zipBytes(entries)

        val document = PptxParser.parse(zipBytes)
        assertEquals(2, document.slides.size)
        assertEquals("第二页", document.slides[0].title)
        assertEquals("第十页", document.slides[1].title)
        assertEquals(1, document.slides[0].index)
        assertEquals(2, document.slides[1].index)
    }

    @Test
    fun `矢量 emf 图片被跳过`() {
        val file = writeZip(
            mapOf(
                "ppt/slides/slide1.xml" to slideXml("标题", listOf("正文")).toByteArray(Charsets.UTF_8),
                "ppt/slides/_rels/slide1.xml.rels" to relationshipsXml(
                    Triple("rId1", "$REL_BASE/image", "../media/logo.emf"),
                ),
                "ppt/media/logo.emf" to ByteArray(64) { 1 },
            ),
        )
        val slide = PptxParser.parse(file).slides.single()
        assertTrue(slide.images.isEmpty())
    }

    // ---------------------------------------------------------------- 损坏兜底

    @Test
    fun `不是 zip 的字节不会崩溃`() {
        val document = PptxParser.parse("这根本不是演示文稿".toByteArray(Charsets.UTF_8))
        assertTrue(document.slides.isEmpty())
        assertNotNull(document.warning)
    }

    @Test
    fun `空字节给出提示`() {
        val document = PptxParser.parse(ByteArray(0))
        assertTrue(document.slides.isEmpty())
        assertNotNull(document.warning)
    }

    @Test
    fun `zip 里没有任何幻灯片时不算崩溃`() {
        val file = writeZip(mapOf("ppt/presentation.xml" to presentationXml("rId9").toByteArray()))
        val document = PptxParser.parse(file)
        assertTrue(document.slides.isEmpty())
        assertNotNull(document.warning)
    }

    // ---------------------------------------------------------------- 旧版 .ppt(OLE2 记录流)

    @Test
    fun `旧版 ppt 的 TextCharsAtom 与 TextBytesAtom 都能提取成页`() {
        val bytes = ByteArrayOutputStream().apply {
            write(OLE2_MAGIC)
            write(record(SLIDE_PERSIST, ByteArray(4)))
            write(record(TEXT_CHARS, "第一页标题\r要点一".toByteArray(Charsets.UTF_16LE)))
            write(record(SLIDE_PERSIST, ByteArray(4)))
            write(record(TEXT_BYTES, "Second slide\rBullet text".toByteArray(Charsets.ISO_8859_1)))
        }.toByteArray()

        val document = PptxParser.parse(bytes)
        assertEquals(2, document.slides.size)
        assertEquals("第一页标题", document.slides[0].title)
        assertEquals(listOf("要点一"), document.slides[0].bullets)
        assertEquals("Second slide", document.slides[1].title)
        assertEquals(listOf("Bullet text"), document.slides[1].bullets)
        assertNotNull(document.warning)
    }

    @Test
    fun `旧版 ppt 一个字的提取不到时给出一页说明而不是崩溃`() {
        val bytes = OLE2_MAGIC + ByteArray(128)
        val document = PptxParser.parse(bytes)
        assertEquals(1, document.slides.size)
        assertNotNull(document.warning)
        assertTrue(document.warning!!.contains("旧版"))
        assertEquals(1, document.slides[0].index)
    }

    @Test
    fun `scanLegacyTextRuns 对随机字节保持安静`() {
        val document = PptxParser.parseLegacyPpt(ByteArray(512) { (it * 31).toByte() })
        assertEquals(1, document.slides.size)
        assertNotNull(document.warning)
    }

    // ---------------------------------------------------------------- Symbol 字体 PUA 归一

    @Test
    fun `Symbol 私有区码位归一为标准 Unicode 数学符号`() {
        // α(F061) β(F062) ≤(F0A3) Σ(F053) ∫(F0F2) −(F02D)
        assertEquals("α β ≤ Σ ∫ −", PptxParser.normalizeSymbolPua("\uF061 \uF062 \uF0A3 \uF053 \uF0F2 \uF02D"))
    }

    @Test
    fun `项目符号映射为圆点 未收录字形直接丢弃`() {
        // •(F0B7) 保留为圆点;F0A7(♣ 装饰符)**不**进入文本;普通中文不受影响
        assertEquals("• 集合", PptxParser.normalizeSymbolPua("\uF0B7 集合"))
        // 未收录字形(F0A7 装饰符)在映射阶段直接消失,不残留豆腐块
        assertEquals("Δ=b²-4ac", PptxParser.normalizeSymbolPua("\uF044=b²\uF0A7-4ac"))
    }

    @Test
    fun `希腊字母 ν 与 υ 不会被当作项目符号抹掉`() {
        assertEquals("ν = υ / t", PptxParser.normalizeSymbolPua("\uF06E = \uF075 / t"))
    }

    @Test
    fun `没有私有区码位的文本原样返回`() {
        val text = "第四章 插值法 · 拉格朗日 (n=3)"
        assertEquals(text, PptxParser.normalizeSymbolPua(text))
    }

    @Test
    fun `解析 pptx 时标题 要点 表格 备注都经过符号归一`() {
        val document = PptxParser.parse(
            writeZip(
                mapOf(
                    "ppt/presentation.xml" to presentationXml("rId1"),
                    "ppt/_rels/presentation.xml.rels" to presentationRels("rId1" to "slides/slide1.xml"),
                    "ppt/slides/slide1.xml" to slideXml(
                        title = "\uF044\uF061\uF062",
                        bullets = listOf("\uF0B7 α=\uF0B1\uF0B1", "λ\uF0A3 1"),
                        table = listOf(listOf("\uF073", "σ²")),
                    ),
                    "ppt/slides/_rels/slide1.xml.rels" to relationshipsXml(
                        Triple("rId1", "$REL_BASE/notesSlide", "../notesSlides/notesSlide1.xml"),
                    ),
                    "ppt/notesSlides/notesSlide1.xml" to notesXml("", "讲义 \uF053\uF06E"),
                ),
            ),
        )
        val slide = document.slides.single()
        assertEquals("Δαβ", slide.title)
        assertEquals(listOf("• α=±±", "λ≤ 1"), slide.bullets)
        assertEquals(listOf(listOf(listOf("σ", "σ²"))), slide.tables)
        assertEquals("讲义 Σν", slide.notes)
        assertTrue("归一后不应残留任何 PUA 码位", slide.toPlainText().none { it in '\uF020'..'\uF0FF' })
    }

    // ---------------------------------------------------------------- 构造工具

    private val REL_BASE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun writeZip(entries: Map<String, Any>): File {
        val file = tempFolder.newFile("deck-${System.nanoTime()}.pptx")
        FileOutputStream(file).use { output ->
            ZipOutputStream(output).use { zip ->
                for ((name, value) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(payload(value))
                    zip.closeEntry()
                }
            }
        }
        return file
    }

    private fun zipBytes(entries: Map<String, Any>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, value) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(payload(value))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** 字符串按 UTF-8 写包,ByteArray 原样写(图片等二进制) */
    private fun payload(value: Any): ByteArray = when (value) {
        is ByteArray -> value
        is String -> value.toByteArray(Charsets.UTF_8)
        else -> error("不支持的 zip 条目类型: ${value::class.java.name}")
    }

    private fun presentationXml(vararg relIds: String): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:presentation xmlns:a="$NS_A" xmlns:r="$NS_R" xmlns:p="$NS_P">
          <p:sldIdLst>
            ${relIds.mapIndexed { i, id -> """<p:sldId id="${256 + i}" r:id="$id"/>""" }.joinToString("\n")}
          </p:sldIdLst>
        </p:presentation>
    """.trim()

    private fun presentationRels(vararg pairs: Pair<String, String>): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="$NS_REL">
          ${pairs.joinToString("\n") { (id, target) ->
        """<Relationship Id="$id" Type="$REL_BASE/slide" Target="$target"/>"""
    }}
        </Relationships>
    """.trim()

    private fun relationshipsXml(vararg rels: Triple<String, String, String>): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="$NS_REL">
          ${rels.joinToString("\n") { (id, type, target) ->
        """<Relationship Id="$id" Type="$type" Target="$target"/>"""
    }}
        </Relationships>
    """.trim()

    private fun slideXml(
        title: String,
        bullets: List<String>,
        table: List<List<String>>? = null,
        titlePlaceholder: Boolean = true,
    ): String {
        val titleShape = if (titlePlaceholder) {
            """
            <p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr/>
              <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>$title</a:t></a:r></a:p></p:txBody></p:sp>
            """.trim()
        } else {
            ""
        }
        val bodyShape = """
            <p:sp><p:nvSpPr><p:cNvPr id="3" name="Body"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr/>
              <p:txBody><a:bodyPr/><a:lstStyle/>
              ${bullets.joinToString("\n") { "<a:p><a:r><a:t>$it</a:t></a:r></a:p>" }}
              <a:p><a:endParaRPr/></a:p>
              </p:txBody></p:sp>
        """.trim()
        val tableShape = if (table == null) {
            ""
        } else {
            val rows = table.joinToString("\n") { row ->
                val cells = row.joinToString("") { cell ->
                    """<a:tc><a:txBody><a:bodyPr/><a:p><a:r><a:t>$cell</a:t></a:r></a:p></a:txBody></a:tc>"""
                }
                """<a:tr h="370840">$cells</a:tr>"""
            }
            """
            <p:graphicFrame>
              <p:nvGraphicFramePr><p:cNvPr id="4" name="Table"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>
              <p:xfrm/>
              <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table">
                <a:tbl><a:tblPr/><a:tblGrid><a:gridCol/><a:gridCol/></a:tblGrid>$rows</a:tbl>
              </a:graphicData></a:graphic>
            </p:graphicFrame>
            """.trim()
        }
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="$NS_A" xmlns:r="$NS_R" xmlns:p="$NS_P">
              <p:cSld><p:spTree>
                <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                <p:grpSpPr/>
                $titleShape
                $bodyShape
                $tableShape
              </p:spTree></p:cSld>
            </p:sld>
        """.trim()
    }

    private fun notesXml(slideNumber: String, notes: String): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <p:notes xmlns:a="$NS_A" xmlns:r="$NS_R" xmlns:p="$NS_P">
          <p:cSld><p:spTree>
            <p:sp><p:nvSpPr><p:cNvPr id="2" name="Slide Number"/><p:cNvSpPr/><p:nvPr><p:ph type="sldNum" idx="10"/></p:nvPr></p:nvSpPr>
              <p:txBody><a:bodyPr/><a:p><a:fld id="{1}" type="slidenum"><a:t>$slideNumber</a:t></a:fld></a:p></p:txBody></p:sp>
            <p:sp><p:nvSpPr><p:cNvPr id="3" name="Notes"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr>
              <p:txBody><a:bodyPr/><a:p><a:r><a:t>$notes</a:t></a:r></a:p></p:txBody></p:sp>
          </p:spTree></p:cSld>
        </p:notes>
    """.trim()

    /** 1×1 透明 PNG(避免在 Android 单测里依赖 AWT/ImageIO) */
    private fun tinyPng(): ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    /** 构造 PowerPoint 二进制记录头:verInstance(2) + type(2) + length(4) + payload */
    private fun record(type: Int, payload: ByteArray, verInstance: Int = 0): ByteArray {
        val header = ByteArray(8)
        header[0] = (verInstance and 0xFF).toByte()
        header[1] = ((verInstance shr 8) and 0xFF).toByte()
        header[2] = (type and 0xFF).toByte()
        header[3] = ((type shr 8) and 0xFF).toByte()
        header[4] = (payload.size and 0xFF).toByte()
        header[5] = ((payload.size shr 8) and 0xFF).toByte()
        header[6] = ((payload.size shr 16) and 0xFF).toByte()
        header[7] = ((payload.size shr 24) and 0xFF).toByte()
        return header + payload
    }

    private companion object {
        const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
        const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val NS_P = "http://schemas.openxmlformats.org/presentationml/2006/main"
        const val NS_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

        const val SLIDE_PERSIST = 0x03F3
        const val TEXT_CHARS = 0x0FA0
        const val TEXT_BYTES = 0x0FA8

        val OLE2_MAGIC = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
        )
    }
}
