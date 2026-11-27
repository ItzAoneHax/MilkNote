package com.milknote.notes.storage

import com.milknote.ink.model.DrawingChange
import com.milknote.ink.model.InkOp
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.Stroke
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files

/**
 * 帧数上限边界:恰好达到上限 + 干净 EOF 是正常文件(不能判 INVALID 并截断历史);
 * 只有第 limit+1 帧仍存在时才拒绝。默认读取(本地日志)不限制帧数,导入显式传上限。
 */
@RunWith(Parameterized::class)
class DrawingJournalFrameLimitTest(private val limit: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "maxFrames={0}")
        fun cases(): List<Array<Any>> = listOf(0, 1, 2, 5).map { arrayOf<Any>(it) }
    }

    private val dir: File = Files.createTempDirectory("milknote-jrnl-limit").toFile()
    private val journal: File = File(dir, "p0001.jrnl")

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun appendFrame(index: Int) {
        val stroke = Stroke(
            id = "s$index",
            tool = InkTool.PEN,
            color = 0xFF23221E,
            baseWidthMm = 0.7f,
            points = listOf(InkPoint(index.toFloat(), index.toFloat(), 0.5f, 0f, 0f, index.toLong())),
        )
        DrawingJournal.append(journal, DrawingChange.Apply(InkOp.Add(stroke)))
    }

    @Test
    fun `恰好上限且干净 EOF 正常`() {
        repeat(limit) { appendFrame(it) }

        val result = DrawingJournal.read(journal, maxFrames = limit)

        assertEquals(DrawingJournal.Damage.NONE, result.damage)
        assertEquals(limit, result.frames.size)
        assertEquals(journal.length(), result.validEnd)
    }

    @Test
    fun `超过上限判 INVALID 且保留前 limit 帧`() {
        repeat(limit + 1) { appendFrame(it) }

        val result = DrawingJournal.read(journal, maxFrames = limit)

        assertEquals(DrawingJournal.Damage.INVALID, result.damage)
        assertEquals(limit, result.frames.size)
    }

    @Test
    fun `本地默认读取不限制帧数`() {
        repeat(limit + 1) { appendFrame(it) }

        val result = DrawingJournal.read(journal)

        assertEquals(DrawingJournal.Damage.NONE, result.damage)
        assertEquals(limit + 1, result.frames.size)
    }
}
