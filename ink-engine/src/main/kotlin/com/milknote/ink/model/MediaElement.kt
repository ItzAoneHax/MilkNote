package com.milknote.ink.model

import kotlinx.serialization.Serializable

/**
 * 页面音视频元素类型。
 *
 * 与 [ImageElement] 同一坐标系与落盘纪律:文件本体在笔记本目录内,
 * [MediaElement.src] 为相对 `xxx.milk` 目录的路径(如 `media/video-xxx.mp4`)。
 */
@Serializable
enum class MediaKind { AUDIO, VIDEO }

/**
 * 页面音视频元素(本地录制/导入;GRAPHICS-ENGINE 媒体层)。
 *
 * - x/y/width/height 为页坐标 px(与笔迹同一坐标系),旋转与层级规则同图片/批注卡。
 * - durationMs 为素材时长(毫秒);0 表示未知(旧数据/探测失败),UI 以占位显示。
 * - 所有可选字段都有默认值:早期 JSON(仅 id/src 等)可直接解码,无需迁移。
 * - 落盘走 `<pageId>.media.json` 整表原子重写(与 ann/img sidecar 同构)。
 */
@Serializable
data class MediaElement(
    val id: String,
    val kind: MediaKind = MediaKind.AUDIO,
    val src: String = "",               // 相对 notebookDir 的路径
    val x: Float = 0f,                  // 左上角 x(页坐标 px)
    val y: Float = 0f,                  // 左上角 y
    val width: Float = 0f,              // 显示宽(px)
    val height: Float = 0f,             // 显示高(px)
    val durationMs: Long = 0L,          // 时长(毫秒;0 = 未知)
    val createdAt: Long = 0L,
    val rotationDeg: Float = 0f,
    val zIndex: Long = 0L,
)
