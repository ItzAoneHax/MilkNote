package com.milknote.notes.storage

import com.milknote.ink.model.AnnotationCard
import com.milknote.ink.model.DrawingChange
import com.milknote.ink.model.ElementRef
import com.milknote.ink.model.ImageElement
import com.milknote.ink.model.InkOp
import com.milknote.ink.model.LayerState
import com.milknote.ink.model.MediaElement
import com.milknote.ink.model.PageInkState
import com.milknote.ink.model.Stroke
import com.milknote.ink.model.orderedElements
import com.milknote.ink.proto.Stroke as StrokePb
import com.milknote.ink.serialize.ProtoInk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/** 操作日志前缀/帧损坏(不可作为尾损恢复)。 */
class DrawingJournalCorruptException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 页面操作日志 `<pageId>.jrnl`(紧凑 framed binary):
 * ```
 * MAGIC "MNJ1"
 * 帧* = [varint payloadLen][uint32 BE crc32(payload)][payload]
 * payload = [动作码][可选 op]
 * ```
 * - Apply: 新增写完整内容(笔迹走 protobuf,元素走紧凑 JSON);Remove 只存 kind+id,
 *   回放时按现态找回内容与 index;Reorder 只存 after;Undo/Redo 只存动作码。
 * - 每帧是一个不可分割的 UI Batch;读取遇到损坏/截断的残尾即停止,追加前截断残尾。
 * - 基线仍是原 `.pb` + `.json` sidecar;日志只追加变更,不整页重写、不重复写基线。
 */
internal object DrawingJournal {

    const val FILE_SUFFIX = ".jrnl"

    /** 单帧上限(防止损坏长度导致巨量分配)。 */
    const val MAX_FRAME_BYTES = 32 * 1024 * 1024

    /** 静态导入防护上限;本地日志默认不限制(长历史页面可以合法超过该值)。 */
    const val MAX_FRAMES = 250_000

    /** 批量/元素嵌套深度上限(正常最多两层:Batch 内元素操作)。 */
    private const val MAX_OP_DEPTH = 64

    private val MAGIC = byteArrayOf('M'.code.toByte(), 'N'.code.toByte(), 'J'.code.toByte(), '1'.code.toByte())

    private const val CODE_APPLY = 1
    private const val CODE_UNDO = 2
    private const val CODE_REDO = 3

    private const val OP_ADD_STROKE = 1
    private const val OP_REMOVE = 2
    private const val OP_ADD_CARD = 3
    private const val OP_ADD_IMAGE = 4
    private const val OP_ADD_MEDIA = 5
    private const val OP_REORDER = 6
    private const val OP_BATCH = 7

    private const val KIND_STROKE = 1
    private const val KIND_CARD = 2
    private const val KIND_IMAGE = 3
    private const val KIND_MEDIA = 4

    private val json = Json { ignoreUnknownKeys = true }

    internal enum class Damage { NONE, TRUNCATED, INVALID }

    internal data class Frame(val code: Int, val op: DecodedOp?)

    internal data class ReadResult(
        val frames: List<Frame>,
        val validEnd: Long,
        val damage: Damage,
    )

    // ---- 读取 ----

    /**
     * 读取并校验日志。[maxFrames] < 0 表示不限制(本地日志默认);
     * 归档导入显式传 [MAX_FRAMES] 作为静态防护。恰好达到上限后干净 EOF 是正常文件。
     */
    fun read(file: File, maxFrames: Int = -1): ReadResult {
        if (!file.exists() || file.length() == 0L) return ReadResult(emptyList(), 0L, Damage.NONE)
        FileInputStream(file).use { raw ->
            val input = CountingInput(raw)
            val magic = ByteArray(MAGIC.size)
            // 头部不稳定地写了一半:按尾损处理,允许追加时重写(基线仍然有效)。
            if (input.readFully(magic, 0, magic.size) < magic.size) {
                return ReadResult(emptyList(), 0L, Damage.TRUNCATED)
            }
            if (!magic.contentEquals(MAGIC)) {
                throw DrawingJournalCorruptException("页面日志损坏：文件头不匹配")
            }
            val frames = ArrayList<Frame>()
            while (true) {
                val frameStart = input.count
                val size = try {
                    readVarintOrNull(input) ?: break // 干净的帧边界 EOF:恰好达到上限也正常结束
                } catch (_: IOException) {
                    return ReadResult(frames, frameStart, Damage.TRUNCATED)
                }
                // 只有确实还存在第 limit+1 帧时才拒绝;上限只用于导入等静态防护。
                if (maxFrames >= 0 && frames.size >= maxFrames) {
                    return ReadResult(frames, frameStart, Damage.INVALID)
                }
                if (size <= 0L || size > MAX_FRAME_BYTES) {
                    return ReadResult(frames, frameStart, Damage.INVALID)
                }
                val crcBytes = ByteArray(4)
                if (input.readFully(crcBytes, 0, 4) < 4) {
                    return ReadResult(frames, frameStart, Damage.TRUNCATED)
                }
                val expectedCrc = ((crcBytes[0].toInt() and 0xFF) shl 24) or
                    ((crcBytes[1].toInt() and 0xFF) shl 16) or
                    ((crcBytes[2].toInt() and 0xFF) shl 8) or
                    (crcBytes[3].toInt() and 0xFF)
                val payload = ByteArray(size.toInt())
                if (input.readFully(payload, 0, payload.size) < payload.size) {
                    return ReadResult(frames, frameStart, Damage.TRUNCATED)
                }
                if (crc32(payload) != expectedCrc) {
                    return ReadResult(frames, frameStart, Damage.INVALID)
                }
                val frame = try {
                    decodeFrame(payload)
                } catch (e: Exception) {
                    throw DrawingJournalCorruptException("页面日志损坏：动作帧无法解析", e)
                }
                frames += frame
            }
            return ReadResult(frames, input.count, Damage.NONE)
        }
    }

    private fun decodeFrame(payload: ByteArray): Frame {
        if (payload.isEmpty()) throw DrawingJournalCorruptException("页面日志损坏：空动作帧")
        val code = payload[0].toInt()
        return when (code) {
            CODE_APPLY -> {
                val reader = Reader(payload, 1)
                val op = decodeOp(reader)
                reader.requireEnd()
                Frame(code, op)
            }
            CODE_UNDO, CODE_REDO -> {
                if (payload.size != 1) throw DrawingJournalCorruptException("页面日志损坏：动作码带多余字节")
                Frame(code, null)
            }
            else -> throw DrawingJournalCorruptException("页面日志损坏：未知动作码 $code")
        }
    }

    private fun decodeOp(reader: Reader, depth: Int = 0): DecodedOp {
        if (depth > MAX_OP_DEPTH) throw DrawingJournalCorruptException("页面日志损坏：操作嵌套过深")
        return when (val type = reader.u8()) {
        OP_ADD_STROKE -> DecodedOp.AddStroke(
            with(ProtoInk) { StrokePb.parseFrom(reader.blob()).toModel() },
        )
        OP_REMOVE -> DecodedOp.Remove(reader.kind(), reader.text())
        OP_ADD_CARD -> DecodedOp.AddCard(
            json.decodeFromString<AnnotationCard>(reader.blob().decodeToString()),
        )
        OP_ADD_IMAGE -> DecodedOp.AddImage(
            json.decodeFromString<ImageElement>(reader.blob().decodeToString()),
        )
        OP_ADD_MEDIA -> DecodedOp.AddMedia(
            json.decodeFromString<MediaElement>(reader.blob().decodeToString()),
        )
        OP_REORDER -> {
            val count = reader.count()
            val after = ArrayList<LayerState>(count)
            repeat(count) {
                val kind = reader.kind()
                val id = reader.text()
                after += LayerState(ElementRef(kind, id), reader.zigzag())
            }
            DecodedOp.Reorder(after)
        }
        OP_BATCH -> {
            val count = reader.count()
            val ops = ArrayList<DecodedOp>(count)
            repeat(count) { ops += decodeOp(reader, depth + 1) }
            DecodedOp.Batch(ops)
        }
        else -> throw DrawingJournalCorruptException("页面日志损坏：未知操作码 $type")
        }
    }

    // ---- 追加 ----

    /**
     * 追加一条变更并返回新的有效文件尾。
     * [validEndHint] 来自本实例上次读取/追加的 [`ReadResult.validEnd`];
     * 与文件长度不一致时(崩溃残尾/外部改动)重新扫描并截断残尾。
     */
    fun append(file: File, change: DrawingChange, validEndHint: Long? = null): Long {
        val payload = encodeChange(change)
        if (payload.size > MAX_FRAME_BYTES) {
            throw DrawingJournalCorruptException("动作帧过大：${payload.size} 字节")
        }
        val frame = frameBytes(payload)
        file.parentFile?.mkdirs()

        var validEnd = 0L
        if (file.exists() && file.length() > 0L) {
            validEnd = when {
                validEndHint != null && validEndHint in MAGIC.size.toLong()..file.length() -> validEndHint
                else -> read(file).validEnd // 任意残尾/损坏帧之后都从最后一个有效帧尾继续
            }
        }
        RandomAccessFile(file, "rw").use { raf ->
            if (validEnd < MAGIC.size) {
                raf.setLength(0L)
                raf.write(MAGIC)
                validEnd = MAGIC.size.toLong()
            } else if (raf.length() > validEnd) {
                raf.setLength(validEnd) // 截断崩溃残尾
            }
            raf.seek(validEnd)
            raf.write(frame)
            return validEnd + frame.size
        }
    }

    private fun encodeChange(change: DrawingChange): ByteArray {
        val writer = Writer()
        when (change) {
            is DrawingChange.Apply -> {
                writer.u8(CODE_APPLY)
                encodeOp(writer, change.op)
            }
            DrawingChange.Undo -> writer.u8(CODE_UNDO)
            DrawingChange.Redo -> writer.u8(CODE_REDO)
        }
        return writer.toByteArray()
    }

    private fun encodeOp(writer: Writer, op: InkOp) {
        when (op) {
            is InkOp.Add -> {
                writer.u8(OP_ADD_STROKE)
                writer.blob(with(ProtoInk) { op.stroke.toProto().toByteArray() })
            }
            is InkOp.Remove -> {
                writer.u8(OP_REMOVE)
                writer.kind(ElementRef.Kind.STROKE)
                writer.text(op.stroke.id)
            }
            is InkOp.AddCard -> {
                writer.u8(OP_ADD_CARD)
                writer.blob(json.encodeToString(op.card).toByteArray(StandardCharsets.UTF_8))
            }
            is InkOp.RemoveCard -> {
                writer.u8(OP_REMOVE)
                writer.kind(ElementRef.Kind.CARD)
                writer.text(op.card.id)
            }
            is InkOp.AddImage -> {
                writer.u8(OP_ADD_IMAGE)
                writer.blob(json.encodeToString(op.image).toByteArray(StandardCharsets.UTF_8))
            }
            is InkOp.RemoveImage -> {
                writer.u8(OP_REMOVE)
                writer.kind(ElementRef.Kind.IMAGE)
                writer.text(op.image.id)
            }
            is InkOp.AddMedia -> {
                writer.u8(OP_ADD_MEDIA)
                writer.blob(json.encodeToString(op.media).toByteArray(StandardCharsets.UTF_8))
            }
            is InkOp.RemoveMedia -> {
                writer.u8(OP_REMOVE)
                writer.kind(ElementRef.Kind.MEDIA)
                writer.text(op.media.id)
            }
            is InkOp.Reorder -> {
                writer.u8(OP_REORDER)
                writer.varint(op.after.size.toLong())
                op.after.forEach {
                    writer.kind(it.ref.kind)
                    writer.text(it.ref.id)
                    writer.zigzag(it.zIndex)
                }
            }
            is InkOp.Batch -> {
                writer.u8(OP_BATCH)
                writer.varint(op.ops.size.toLong())
                op.ops.forEach { encodeOp(writer, it) }
            }
        }
    }

    // ---- 回放 ----

    /** 在已恢复基线的 [state] 上按序回放;内容与两栈由 PageInkState 自身维护。 */
    fun replay(state: PageInkState, frames: List<Frame>): PageInkState {
        frames.forEach { frame ->
            when (frame.code) {
                CODE_UNDO -> state.undo()
                CODE_REDO -> state.redo()
                CODE_APPLY -> materialize(frame.op ?: return@forEach, state)?.let(state::apply)
            }
        }
        return state
    }

    /**
     * Remove 用回放现态找回内容与 index;Reorder 的 before 用现态重建。
     * 目标已不存在时丢弃该子操作(日志与基线不一致时的保守降级)。
     */
    private fun materialize(op: DecodedOp, state: PageInkState): InkOp? = when (op) {
        is DecodedOp.AddStroke -> InkOp.Add(op.stroke)
        is DecodedOp.Remove -> when (op.kind) {
            ElementRef.Kind.STROKE -> {
                val index = state.strokes.indexOfFirst { it.id == op.id }
                index.takeIf { it >= 0 }?.let { InkOp.Remove(state.strokes[it], it) }
            }
            ElementRef.Kind.CARD -> {
                val index = state.cards.indexOfFirst { it.id == op.id }
                index.takeIf { it >= 0 }?.let { InkOp.RemoveCard(state.cards[it], it) }
            }
            ElementRef.Kind.IMAGE -> {
                val index = state.images.indexOfFirst { it.id == op.id }
                index.takeIf { it >= 0 }?.let { InkOp.RemoveImage(state.images[it], it) }
            }
            ElementRef.Kind.MEDIA -> {
                val index = state.media.indexOfFirst { it.id == op.id }
                index.takeIf { it >= 0 }?.let { InkOp.RemoveMedia(state.media[it], it) }
            }
        }
        is DecodedOp.AddCard -> InkOp.AddCard(op.card)
        is DecodedOp.AddImage -> InkOp.AddImage(op.image)
        is DecodedOp.AddMedia -> InkOp.AddMedia(op.media)
        is DecodedOp.Reorder -> InkOp.Reorder(
            state.orderedElements().map { LayerState(it.ref, it.zIndex) },
            op.after,
        )
        is DecodedOp.Batch -> {
            val ops = op.ops.mapNotNull { materialize(it, state) }
            if (ops.isEmpty()) null else InkOp.Batch(ops)
        }
    }

    // ---- 编解码内部类型 ----

    internal sealed interface DecodedOp {
        data class AddStroke(val stroke: Stroke) : DecodedOp
        data class Remove(val kind: ElementRef.Kind, val id: String) : DecodedOp
        data class AddCard(val card: AnnotationCard) : DecodedOp
        data class AddImage(val image: ImageElement) : DecodedOp
        data class AddMedia(val media: MediaElement) : DecodedOp
        data class Reorder(val after: List<LayerState>) : DecodedOp
        data class Batch(val ops: List<DecodedOp>) : DecodedOp
    }

    private class Writer {
        private val out = ByteArrayOutputStream(64)

        fun u8(value: Int) = out.write(value)

        fun varint(value: Long) {
            var v = value
            while (v and 0x7FL.inv() != 0L) {
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            out.write(v.toInt())
        }

        fun zigzag(value: Long) = varint((value shl 1) xor (value shr 63))

        fun text(value: String) = blob(value.toByteArray(StandardCharsets.UTF_8))

        fun blob(value: ByteArray) {
            varint(value.size.toLong())
            out.write(value)
        }

        fun kind(value: ElementRef.Kind) = u8(kindCode(value))

        fun toByteArray(): ByteArray = out.toByteArray()
    }

    private class Reader(private val data: ByteArray, private var pos: Int = 0) {
        fun u8(): Int {
            if (pos >= data.size) throw DrawingJournalCorruptException("页面日志损坏：动作帧被截断")
            return data[pos++].toInt() and 0xFF
        }

        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (shift <= 63) {
                val b = u8()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            throw DrawingJournalCorruptException("页面日志损坏：varint 过长")
        }

        fun count(): Int {
            val value = varint()
            // 每个元素至少占 1 字节;用剩余字节数兜底,避免伪造 count 触发巨量分配。
            if (value < 0L || value > Int.MAX_VALUE || value > (data.size - pos).toLong()) {
                throw DrawingJournalCorruptException("页面日志损坏：元素数量非法")
            }
            return value.toInt()
        }

        fun zigzag(): Long {
            val value = varint()
            return (value ushr 1) xor -(value and 1L)
        }

        fun blob(): ByteArray {
            val size = varint()
            if (size < 0L || size > MAX_FRAME_BYTES || pos + size > data.size) {
                throw DrawingJournalCorruptException("页面日志损坏：负载长度非法")
            }
            val result = data.copyOfRange(pos, pos + size.toInt())
            pos += size.toInt()
            return result
        }

        fun text(): String = blob().decodeToString()

        fun kind(): ElementRef.Kind = when (val code = u8()) {
            KIND_STROKE -> ElementRef.Kind.STROKE
            KIND_CARD -> ElementRef.Kind.CARD
            KIND_IMAGE -> ElementRef.Kind.IMAGE
            KIND_MEDIA -> ElementRef.Kind.MEDIA
            else -> throw DrawingJournalCorruptException("页面日志损坏：未知元素类型 $code")
        }

        fun requireEnd() {
            if (pos != data.size) throw DrawingJournalCorruptException("页面日志损坏：动作帧有多余字节")
        }
    }

    private fun kindCode(kind: ElementRef.Kind): Int = when (kind) {
        ElementRef.Kind.STROKE -> KIND_STROKE
        ElementRef.Kind.CARD -> KIND_CARD
        ElementRef.Kind.IMAGE -> KIND_IMAGE
        ElementRef.Kind.MEDIA -> KIND_MEDIA
    }

    // ---- 二进制与校验 ----

    private fun frameBytes(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 8)
        writeVarint(out, payload.size.toLong())
        val crc = crc32(payload)
        out.write((crc ushr 24) and 0xFF)
        out.write((crc ushr 16) and 0xFF)
        out.write((crc ushr 8) and 0xFF)
        out.write(crc and 0xFF)
        out.write(payload)
        return out.toByteArray()
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun crc32(bytes: ByteArray): Int = CRC32().apply { update(bytes) }.value.toInt()

    private fun readVarintOrNull(input: InputStream): Long? {
        var shift = 0
        var result = 0L
        var readAny = false
        while (shift <= 63) {
            val b = input.read()
            if (b < 0) {
                if (!readAny) return null
                throw java.io.EOFException("truncated varint")
            }
            readAny = true
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw java.io.IOException("varint too long")
    }

    /** 统计已读字节,用于计算有效帧尾。 */
    private class CountingInput(private val input: InputStream) : InputStream() {
        var count: Long = 0L
            private set

        override fun read(): Int {
            val b = input.read()
            if (b >= 0) count++
            return b
        }

        fun readFully(buffer: ByteArray, offset: Int, length: Int): Int {
            var total = 0
            while (total < length) {
                val read = input.read(buffer, offset + total, length - total)
                if (read < 0) break
                total += read
            }
            count += total
            return total
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = input.read(b, off, len)
            if (read > 0) count += read
            return read
        }
    }
}
