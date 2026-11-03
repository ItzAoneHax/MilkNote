package com.milknote.ink.serialize

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.milknote.ink.model.InkPoint
import com.milknote.ink.model.InkTool
import com.milknote.ink.model.Stroke as ModelStroke
import com.milknote.ink.proto.PageSnapshot as PageSnapshotPb
import com.milknote.ink.proto.Point as PointPb
import com.milknote.ink.proto.Stroke as StrokePb
import com.milknote.ink.proto.StrokeRecord as StrokeRecordPb
import java.io.InputStream
import java.io.OutputStream

/**
 * 笔迹 ↔ protobuf(DESIGN-V0 §6.1)。
 * 页面文件 = StrokeRecord 流:每条 [varint 长度][消息体];追加写、崩溃安全(尾部半条丢弃)。
 */
object ProtoInk {

    // ---- 模型 ↔ proto ----

    fun ModelStroke.toProto(): StrokePb {
        val b = StrokePb.newBuilder()
            .setId(id)
            .setTool(tool.id)
            .setColor(color.toHex())
            .setBaseWidth(baseWidthMm)
            .setZIndex(zIndex)
        points.forEach {
            b.addPts(
                PointPb.newBuilder()
                    .setX(it.x).setY(it.y).setP(it.pressure)
                    .setTx(it.tiltX).setTy(it.tiltY)
                    .setT(it.timeMs)
            )
        }
        return b.build()
    }

    fun StrokePb.toModel(): ModelStroke = ModelStroke(
        id = if (id.isNullOrEmpty()) java.util.UUID.randomUUID().toString() else id,
        tool = InkTool.of(tool),
        color = parseColorOrNull(color) ?: 0xFF23221EL,
        baseWidthMm = baseWidth,
        points = ptsList.map { InkPoint(it.x, it.y, it.p, it.tx, it.ty, it.t) },
        zIndex = zIndex,
    )

    fun Long.toHex(): String = "#%08X".format(this)

    /** "#RRGGBB" / "#AARRGGBB" → ARGB Long;纯 JVM,无 android 依赖 */
    fun parseColorOrNull(hex: String?): Long? {
        if (hex.isNullOrBlank() || hex[0] != '#') return null
        val body = hex.substring(1)
        return when (body.length) {
            6 -> ("FF" + body).toLongOrNull(16)
            8 -> body.toLongOrNull(16)
            else -> null
        }
    }

    // ---- 追加流 ----

    fun recordOf(stroke: ModelStroke): StrokeRecordPb =
        StrokeRecordPb.newBuilder().setStroke(stroke.toProto()).build()

    fun tombstoneOf(strokeId: String): StrokeRecordPb =
        StrokeRecordPb.newBuilder().setTombstone(strokeId).build()

    fun writeRecord(out: OutputStream, record: StrokeRecordPb) {
        val cos = CodedOutputStream.newInstance(out, 16)
        cos.writeRawVarint32(record.serializedSize)
        cos.writeRawBytes(record.toByteArray())
        cos.flush()
    }

    /**
     * 读取整页:顺序应用 stroke / tombstone。
     * 全程用同一个 CodedInputStream(它内部有预读缓冲,逐条新建会吞掉下一条的字节)。
     * 尾部半条(崩溃残留)静默丢弃。
     */
    fun readPage(input: InputStream): List<ModelStroke> {
        val strokes = LinkedHashMap<String, ModelStroke>()
        input.use { ins ->
            val cis = CodedInputStream.newInstance(ins)
            cis.setSizeLimit(MAX_RECORD_BYTES)
            while (true) {
                val record = try {
                    if (cis.isAtEnd) break
                    val size = cis.readRawVarint32()
                    if (size <= 0 || size > MAX_RECORD_BYTES) break
                    val body = cis.readRawBytes(size)
                    StrokeRecordPb.parseFrom(body)
                } catch (_: Exception) {
                    break // 尾部损坏:崩溃残留
                }
                when (record.kindCase) {
                    StrokeRecordPb.KindCase.STROKE ->
                        record.stroke.toModel().let { s -> strokes[s.id] = s }
                    StrokeRecordPb.KindCase.TOMBSTONE ->
                        strokes.remove(record.tombstone)
                    else -> {}
                }
            }
        }
        return strokes.values.toList()
    }

    fun snapshot(strokes: List<ModelStroke>, pageId: String, paper: String): PageSnapshotPb {
        val b = PageSnapshotPb.newBuilder().setPageId(pageId).setPaper(paper)
        strokes.forEach { b.addStrokes(it.toProto()) }
        return b.build()
    }

    const val MAX_RECORD_BYTES = 4 * 1024 * 1024
}
