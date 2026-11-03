package com.milknote.ink

import com.milknote.ink.input.InkCommit
import com.milknote.ink.input.InkController
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkOp
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.LassoGeom
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.StrokeGeom
import com.milknote.ink.model.isShape
import com.milknote.ink.serialize.ProtoInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** 形状工具:两点 Stroke 的 proto 往返(工具 id 字符串直接进 Stroke.tool,无 schema 变更) */
class ShapeProtoTest {

    /** 两点形状笔迹(起点/终点 = 对角) */
    private fun shape(id: String, tool: InkTool): Stroke = Stroke(
        id = id,
        tool = tool,
        color = 0xFF3B5BEC,
        baseWidthMm = 1.2f,
        points = listOf(
            InkPoint(20f, 30f, 0.7f, 3f, 4f, 1_000L),
            InkPoint(220f, 180f, 0.2f, 0f, 0f, 1_050L),
        ),
    )

    @Test fun `三种形状工具 roundtrip 保真`() {
        for (tool in listOf(InkTool.RECT, InkTool.OVAL, InkTool.LINE)) {
            val out = ByteArrayOutputStream()
            ProtoInk.writeRecord(out, ProtoInk.recordOf(shape("s-${tool.id}", tool)))
            val back = ProtoInk.readPage(ByteArrayInputStream(out.toByteArray()))
            assertEquals(1, back.size)
            val s = back[0]
            assertEquals("s-${tool.id}", s.id)
            assertEquals(tool, s.tool)
            assertEquals(0xFF3B5BEC, s.color)
            assertEquals(1.2f, s.baseWidthMm, 1e-5f)
            assertEquals(2, s.points.size)
            assertEquals(20f, s.points[0].x, 1e-4f)
            assertEquals(220f, s.points[1].x, 1e-4f)
        }
    }

    @Test fun `形状与普通笔迹混合追加流 + tombstone 语义不变`() {
        val out = ByteArrayOutputStream()
        ProtoInk.writeRecord(out, ProtoInk.recordOf(shape("rect", InkTool.RECT)))
        ProtoInk.writeRecord(out, ProtoInk.recordOf(Stroke(
            id = "pen", tool = InkTool.PEN, color = 0xFF23221E, baseWidthMm = 0.7f,
            points = listOf(InkPoint(0f, 0f, 0.5f, 0f, 0f, 0L), InkPoint(9f, 9f, 0.6f, 0f, 0f, 8L)),
        )))
        ProtoInk.writeRecord(out, ProtoInk.recordOf(shape("oval", InkTool.OVAL)))
        ProtoInk.writeRecord(out, ProtoInk.tombstoneOf("pen"))
        ProtoInk.writeRecord(out, ProtoInk.recordOf(shape("line", InkTool.LINE)))
        val back = ProtoInk.readPage(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf("rect", "oval", "line"), back.map { it.id })
        assertTrue(back.all { it.tool.isShape })
    }

    @Test fun `PageSnapshot 携带形状笔迹`() {
        val snap = ProtoInk.snapshot(
            listOf(shape("r", InkTool.RECT), shape("o", InkTool.OVAL), shape("l", InkTool.LINE)),
            pageId = "p0001", paper = "dot",
        )
        assertEquals(3, snap.strokesCount)
        val back = ProtoInk.run { snap.strokesList.map { it.toModel() } }
        assertEquals(InkTool.RECT, back[0].tool)
        assertEquals(InkTool.OVAL, back[1].tool)
        assertEquals(InkTool.LINE, back[2].tool)
    }

    @Test fun `未知工具字符串回退 PEN(向前兼容不变)`() {
        // 旧版/新版工具字符串都走 InkTool.of:未知回退 PEN,流本身不崩
        val pb = com.milknote.ink.proto.Stroke.newBuilder()
            .setId("legacy")
            .setTool("fountain-of-the-future")
            .setColor("#23221E")
            .setBaseWidth(0.7f)
            .build()
        val model = ProtoInk.run { pb.toModel() }
        assertEquals("legacy", model.id)
        assertEquals(InkTool.PEN, model.tool)
        assertEquals(InkTool.PEN, InkTool.of("nonexistent"))
    }
}

/** 形状几何:擦除/套索按可见轮廓命中(普通笔迹语义不变) */
class ShapeGeomTest {

    private fun two(tool: InkTool, x0: Float, y0: Float, x1: Float, y1: Float) = Stroke(
        id = "shape", tool = tool, color = 0xFF23221E, baseWidthMm = 1f,
        points = listOf(
            InkPoint(x0, y0, 0.5f, 0f, 0f, 0L),
            InkPoint(x1, y1, 0.5f, 0f, 0f, 1L),
        ),
    )

    private fun eraser(vararg xy: Pair<Float, Float>) = Stroke(
        id = "e", tool = InkTool.ERASER, color = 0, baseWidthMm = 4f,
        points = xy.map { InkPoint(it.first, it.second, 0.5f, 0f, 0f, 1L) },
    )

    @Test fun `polyline 展开 - RECT 闭合四边 OVAL 采样闭合 LINE 两点`() {
        val rect = StrokeGeom.polyline(two(InkTool.RECT, 0f, 0f, 100f, 50f))
        assertEquals(5, rect.size)                                  // 4 角 + 闭合
        assertEquals(rect.first().x, rect.last().x, 1e-4f)          // 闭合
        assertEquals(0f, rect[0].x, 1e-4f)
        assertEquals(100f, rect[1].x, 1e-4f)

        val oval = StrokeGeom.polyline(two(InkTool.OVAL, 0f, 0f, 100f, 50f))
        assertEquals(33, oval.size)                                 // 32 采样 + 闭合
        assertEquals(oval.first().x, oval.last().x, 1e-3f)

        val line = StrokeGeom.polyline(two(InkTool.LINE, 3f, 4f, 30f, 40f))
        assertEquals(2, line.size)
    }

    @Test fun `擦矩形 - 划过边缘即整形状命中(不要求扫过对角线)`() {
        val page = PageInkState()
        page.apply(InkOp.Add(two(InkTool.RECT, 10f, 10f, 110f, 60f)))
        // 只擦上边中点附近,不碰两个端点、不与对角线(两点连线)相交
        val op = page.eraseStrokes(eraser(55f to 5f, 65f to 15f))
        assertNotNull(op)
        page.apply(op!!)
        assertEquals(0, page.strokes.size)
        page.undo()
        assertEquals(1, page.strokes.size)                          // 撤销恢复整形状
    }

    @Test fun `擦椭圆 - 轮廓命中 内部空白不命中`() {
        val page = PageInkState()
        page.apply(InkOp.Add(two(InkTool.OVAL, 0f, 0f, 200f, 100f)))
        // 中心空白区(100,50)擦除不命中
        assertNull(page.eraseStrokes(eraser(95f to 45f, 105f to 55f)))
        assertEquals(1, page.strokes.size)
        // 右端点(200,50)附近划过命中
        assertNotNull(page.eraseStrokes(eraser(190f to 45f, 200f to 55f)))
    }

    @Test fun `套索矩形 - 圈住可见部分(半边)即捕获 普通笔迹语义不变`() {
        val rect = two(InkTool.RECT, 0f, 0f, 100f, 50f)
        val halfLoop = listOf(
            InkPoint(-5f, -5f, 0f, 0f, 0f, 0L),
            InkPoint(60f, -5f, 0f, 0f, 0f, 1L),
            InkPoint(60f, 55f, 0f, 0f, 0f, 2L),
            InkPoint(-5f, 55f, 0f, 0f, 0f, 3L),
        )
        // 圈住左半边:左上/左下/右上角在圈内 → 轮廓点未全在 → 不捕获(完全包围语义对形状 = 覆盖整段轮廓)
        assertFalse(LassoGeom.captures(halfLoop, rect))
        val fullLoop = halfLoop.map { it.copy(x = if (it.x > 0f) 110f else it.x) }
        assertTrue(LassoGeom.captures(fullLoop, rect))
        // 普通笔迹两点语义不变
        val pen = Stroke("p", InkTool.PEN, 0, 0.7f, listOf(
            InkPoint(5f, 5f, 0.5f, 0f, 0f, 0L), InkPoint(8f, 8f, 0.5f, 0f, 0f, 1L),
        ))
        assertTrue(LassoGeom.captures(halfLoop, pen))
    }
}

/** 图片元素:撤销/重做 + 落盘镜像 + 整页加载 */
class ImageElementUndoTest {

    private fun img(id: String, x: Float = 0f) = ImageElement(
        id = id, src = "images/$id.png", x = x, y = 10f, width = 200f, height = 150f,
        createdAt = 42L,
    )

    @Test fun `PageInkState 图片 undo redo roundtrip`() {
        val page = PageInkState()
        page.apply(InkOp.AddImage(img("a")))
        page.apply(InkOp.AddImage(img("b")))
        assertEquals(listOf("a", "b"), page.images.map { it.id })
        page.undo()
        assertEquals(listOf("a"), page.images.map { it.id })
        page.redo()
        assertEquals(listOf("a", "b"), page.images.map { it.id })
        page.apply(InkOp.RemoveImage(page.images[0], 0))
        assertEquals(listOf("b"), page.images.map { it.id })
        page.undo()
        assertEquals(listOf("a", "b"), page.images.map { it.id })   // 删除可撤销
    }

    @Test fun `删除中间层图片撤销后原层位恢复`() {
        val page = PageInkState()
        page.apply(InkOp.AddImage(img("a")))
        page.apply(InkOp.AddImage(img("b")))
        page.apply(InkOp.AddImage(img("c")))
        page.apply(InkOp.RemoveImage(page.images[1], 1))            // 删中间的 b
        assertEquals(listOf("a", "c"), page.images.map { it.id })
        page.undo()
        assertEquals(listOf("a", "b", "c"), page.images.map { it.id })   // 层位不变
    }

    @Test fun `图片与笔迹同撤销栈 - 交叉操作互不干扰`() {
        val page = PageInkState()
        val stroke = Stroke("s1", InkTool.PEN, 0xFF23221E, 0.7f, listOf(
            InkPoint(0f, 0f, 0.5f, 0f, 0f, 0L), InkPoint(9f, 9f, 0.5f, 0f, 0f, 1L),
        ))
        page.apply(InkOp.Add(stroke))
        page.apply(InkOp.AddImage(img("img")))
        page.undo()
        assertEquals(listOf("s1"), page.strokes.map { it.id })      // 只撤图片
        assertTrue(page.images.isEmpty())
        page.undo()
        assertTrue(page.strokes.isEmpty())
        page.redo()
        page.redo()
        assertEquals(listOf("s1"), page.strokes.map { it.id })
        assertEquals(listOf("img"), page.images.map { it.id })
    }

    @Test fun `整笔划擦除不波及图片层`() {
        val page = PageInkState()
        page.apply(InkOp.AddImage(img("img", x = 0f)))
        page.apply(InkOp.Add(Stroke("s1", InkTool.PEN, 0xFF23221E, 0.7f, listOf(
            InkPoint(0f, 0f, 0.5f, 0f, 0f, 0L), InkPoint(9f, 9f, 0.5f, 0f, 0f, 1L),
        ))))
        val eraser = Stroke("e", InkTool.ERASER, 0, 4f, listOf(
            InkPoint(0f, 0f, 0.5f, 0f, 0f, 0L), InkPoint(50f, 50f, 0.5f, 0f, 0f, 1L),
        ))
        val op = page.eraseStrokes(eraser)
        page.apply(op!!)
        assertTrue(page.images.isNotEmpty())                        // 图片不受擦除影响
        assertTrue(page.strokes.isEmpty())
    }

    @Test fun `reset 支持图片并清空撤销栈`() {
        val page = PageInkState()
        page.apply(InkOp.AddImage(img("old")))
        page.reset(emptyList(), emptyList(), listOf(img("new")))
        assertFalse(page.canUndo)
        assertEquals(listOf("new"), page.images.map { it.id })
    }

    @Test fun `Controller 增删图片 - 镜像 ImageAdded 或 ImageRemoved 且可撤销`() {
        val c = InkController()
        val mirror = mutableListOf<InkCommit>()
        c.onCommit = { mirror.add(it) }

        c.addImageElement(img("a"))
        assertEquals(listOf("a"), c.images().map { it.id })
        assertTrue(mirror.single() is InkCommit.ImageAdded)

        assertTrue(c.deleteImageElement("a"))
        assertTrue(c.images().isEmpty())
        assertTrue(mirror.last() is InkCommit.ImageRemoved)
        assertEquals("a", (mirror.last() as InkCommit.ImageRemoved).imageId)

        // undo 删除 → 图片回来,镜像 = ImageAdded(重放恢复)
        c.undo()
        assertEquals(listOf("a"), c.images().map { it.id })
        assertTrue(mirror.last() is InkCommit.ImageAdded)
        // redo → 再删,镜像 = ImageRemoved
        c.redo()
        assertTrue(c.images().isEmpty())
        assertTrue(mirror.last() is InkCommit.ImageRemoved)

        assertFalse(c.deleteImageElement("nonexistent"))
    }

    @Test fun `loadStrokes 携带图片 - 版本号推进渲染缓存失效`() {
        val c = InkController()
        val stampBefore = c.renderStamp
        c.loadStrokes(emptyList(), emptyList(), listOf(img("a"), img("b")))
        assertEquals(listOf("a", "b"), c.images().map { it.id })
        assertTrue(c.renderStamp != stampBefore)
    }
}

/** 图片拖动:命中 → 预览 → 抬起落 Batch(与批注卡拖动同构;撤销/持久化镜像语义) */
class ImageDragTest {

    private fun img(id: String, x: Float, y: Float = 10f) = ImageElement(
        id = id, src = "images/$id.png", x = x, y = y, width = 200f, height = 150f,
    )

    private fun controllerWithImages(vararg images: ImageElement): Pair<InkController, MutableList<InkCommit>> {
        val c = InkController()
        c.loadStrokes(emptyList(), emptyList(), images.toList())
        val mirror = mutableListOf<InkCommit>()
        c.onCommit = { mirror.add(it) }
        return c to mirror
    }

    @Test fun `拖动结束 - 同 id 平移,镜像 ImageRemoved 再 ImageAdded,撤销恢复原位`() {
        val (c, mirror) = controllerWithImages(img("a", x = 0f))
        c.beginImageDrag("a")
        c.moveImageDrag(30f, 40f)
        assertTrue(c.endImageDrag())

        assertEquals(1, c.images().size)
        val moved = c.images().single()
        assertEquals("a", moved.id)                       // 同 id(src/引用不变)
        assertEquals(30f, moved.x, 0.001f)                // 0+30
        assertEquals(50f, moved.y, 0.001f)                // 10+40

        // 落盘镜像:先 removed 再 added(saveImages 整表重写语义下仅为顺序占位)
        assertEquals(2, mirror.size)
        assertTrue(mirror[0] is InkCommit.ImageRemoved && (mirror[0] as InkCommit.ImageRemoved).imageId == "a")
        assertTrue(mirror[1] is InkCommit.ImageAdded && (mirror[1] as InkCommit.ImageAdded).image.id == "a")

        // 撤销:原坐标、原层位恢复
        c.undo()
        val restored = c.images().single()
        assertEquals("a", restored.id)
        assertEquals(0f, restored.x, 0.001f)
        assertEquals(10f, restored.y, 0.001f)
        c.redo()
        assertEquals(30f, c.images().single().x, 0.001f)
    }

    @Test fun `拖动保持层位语义 - 撤销后回到原 index`() {
        val (c, _) = controllerWithImages(img("a", x = 0f), img("b", x = 500f), img("c", x = 1000f))
        c.beginImageDrag("b")                              // 中间层图片拖走再撤销
        c.moveImageDrag(10f, 0f)
        assertTrue(c.endImageDrag())
        assertEquals(listOf("a", "c", "b"), c.images().map { it.id })   // 移动后置顶(同卡拖动)
        c.undo()
        assertEquals(listOf("a", "b", "c"), c.images().map { it.id })   // 原层位恢复
    }

    @Test fun `未位移拖动不产生记录`() {
        val (c, mirror) = controllerWithImages(img("a", x = 0f))
        val stampBefore = c.renderStamp
        c.beginImageDrag("a")
        c.moveImageDrag(0.3f, 0f)                          // <1px
        assertFalse(c.endImageDrag())
        assertTrue(mirror.isEmpty())
        assertEquals(0f, c.images().single().x, 0.001f)
        assertTrue(c.renderStamp != stampBefore)           // 拖动结束仍推进缓存键(预览复位)
    }

    @Test fun `cancelImageDrag 放弃预览不落盘`() {
        val (c, mirror) = controllerWithImages(img("a", x = 0f))
        c.beginImageDrag("a")
        c.moveImageDrag(50f, 60f)
        assertEquals("a" to (50f to 60f), c.imageDragOffset())
        c.cancelImageDrag()
        assertNull(c.imageDragOffset())
        assertTrue(mirror.isEmpty())
        assertEquals(0f, c.images().single().x, 0.001f)
    }

    @Test fun `拖动状态机 - offset 与 renderStamp 推进`() {
        val (c, _) = controllerWithImages(img("a", x = 0f))
        assertNull(c.imageDragOffset())
        assertNull(c.draggingImageId())
        val stampIdle = c.renderStamp
        c.beginImageDrag("a")
        assertEquals("a", c.draggingImageId())
        assertTrue(c.renderStamp != stampIdle)             // 进入拖动 → 缓存摘除重画
        c.moveImageDrag(7f, 8f)
        assertEquals("a" to (7f to 8f), c.imageDragOffset())
        assertTrue(c.endImageDrag())
        assertNull(c.imageDragOffset())                    // 抬起复位
        assertNull(c.draggingImageId())
    }

    @Test fun `未 begin 的 move 与不存在的 id 均为 no-op`() {
        val (c, mirror) = controllerWithImages(img("a", x = 0f))
        c.moveImageDrag(100f, 100f)                        // 未 begin
        assertNull(c.imageDragOffset())
        assertFalse(c.endImageDrag())
        assertTrue(mirror.isEmpty())

        c.beginImageDrag("ghost")                          // 不存在的 id:不进入可拖动态
        c.moveImageDrag(20f, 20f)
        assertFalse(c.endImageDrag())
        assertTrue(mirror.isEmpty())
        assertEquals(1, c.images().size)
        assertEquals(0f, c.images().single().x, 0.001f)
    }

    @Test fun `deleteImageElement 推进渲染缓存键并可撤销原位恢复`() {
        val (c, mirror) = controllerWithImages(img("a", x = 0f), img("b", x = 500f))
        val stampBefore = c.renderStamp
        assertTrue(c.deleteImageElement("a"))
        assertTrue(c.renderStamp != stampBefore)           // 渲染失效
        assertEquals(listOf("b"), c.images().map { it.id })
        assertTrue(mirror.single() is InkCommit.ImageRemoved)
        assertFalse(c.deleteImageElement("a"))             // 已不存在
        c.undo()
        assertEquals(listOf("a", "b"), c.images().map { it.id })   // 原位恢复
    }
}
