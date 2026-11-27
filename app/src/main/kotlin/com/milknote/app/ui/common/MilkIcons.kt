package com.milknote.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkRadius

/**
 * 统一线性图标徽章（与「技能与工具」行内 30dp 圆角浅染容器一致）：
 * 用于列表入口、选择行与浮层标题，保持全应用一致的容器质感与图标尺度。
 */
@Composable
fun MilkIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    boxSize: Dp = 30.dp,
    iconSize: Dp = 17.dp,
    cornerRadius: Dp = MilkRadius.sm,
    active: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalMilkDark.current
    val bg = containerColor ?: if (active) {
        cs.primary.copy(alpha = if (dark) 0.18f else 0.10f)
    } else {
        cs.surfaceVariant.copy(alpha = if (dark) 0.55f else 0.75f)
    }
    Box(
        modifier
            .size(boxSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = if (active) tint else cs.onSurfaceVariant.copy(alpha = 0.45f),
        )
    }
}

/**
 * MilkNote 统一线性矢量图标库：
 * 24×24 视口、1.7f 等宽圆角描边（StrokeCap.Round / StrokeJoin.Round），
 * 对齐「技能与工具」页的轻盈线性风格，替换语义失真、厚重或拟物位图图标。
 */
object MilkIcons {
    private val ink = SolidColor(Color.Black)

    private fun icon(
        name: String,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

    private fun ImageVector.Builder.stroke(
        width: Float = 1.7f,
        block: PathBuilder.() -> Unit,
    ) {
        path(
            fill = null,
            stroke = ink,
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
            pathBuilder = block,
        )
    }

    private fun ImageVector.Builder.dot(
        cx: Float,
        cy: Float,
        r: Float = 1.25f,
    ) {
        path(
            fill = ink,
            stroke = null,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(cx - r, cy)
            arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx + r, cy)
            arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx - r, cy)
            close()
        }
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx - r, cy)
        close()
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        lineTo(r - rad, t)
        quadTo(r, t, r, t + rad)
        lineTo(r, b - rad)
        quadTo(r, b, r - rad, b)
        lineTo(l + rad, b)
        quadTo(l, b, l, b - rad)
        lineTo(l, t + rad)
        quadTo(l, t, l + rad, t)
        close()
    }

    private fun PathBuilder.sparkle(cx: Float, cy: Float, r: Float) {
        val inner = r * 0.24f
        moveTo(cx, cy - r)
        quadTo(cx + inner, cy - inner, cx + r, cy)
        quadTo(cx + inner, cy + inner, cx, cy + r)
        quadTo(cx - inner, cy + inner, cx - r, cy)
        quadTo(cx - inner, cy - inner, cx, cy - r)
        close()
    }

    // ==================== 书写工具与笔设置 ====================

    /** 钢笔：经典对称笔尖 + 中缝 + 气孔 + 笔握环 */
    val FountainPen: ImageVector by lazy {
        icon("FountainPen") {
            stroke {
                moveTo(12f, 2.75f)
                lineTo(18f, 11f)
                lineTo(15.2f, 17.5f)
                lineTo(8.8f, 17.5f)
                lineTo(6f, 11f)
                close()
                moveTo(12f, 2.75f)
                lineTo(12f, 11.2f)
                moveTo(8f, 17.5f)
                lineTo(8f, 20.5f)
                quadTo(8f, 21.25f, 8.75f, 21.25f)
                lineTo(15.25f, 21.25f)
                quadTo(16f, 21.25f, 16f, 20.5f)
                lineTo(16f, 17.5f)
            }
            dot(12f, 12.6f, 1.1f)
        }
    }

    /** 钢笔笔尖着色层（用于双层色笔图标） */
    val FountainPenNibAccent: ImageVector by lazy {
        icon("FountainPenNibAccent") {
            stroke(width = 1.9f) {
                moveTo(12f, 3.2f)
                lineTo(17.5f, 11f)
                lineTo(15.2f, 17f)
                lineTo(8.8f, 17f)
                lineTo(6.5f, 11f)
                close()
            }
        }
    }

    /** 圆珠笔：斜向笔杆 + 锥形笔尖 + 笔夹 */
    val BallpointPen: ImageVector by lazy {
        icon("BallpointPen") {
            stroke {
                moveTo(16.2f, 3.6f)
                lineTo(20.4f, 7.8f)
                lineTo(9.4f, 18.8f)
                lineTo(4f, 20f)
                lineTo(5.2f, 14.6f)
                close()
                moveTo(13.8f, 6f)
                lineTo(18f, 10.2f)
                moveTo(6.2f, 13.6f)
                lineTo(10.4f, 17.8f)
            }
        }
    }

    /** 圆珠笔笔头着色层 */
    val BallpointPenTipAccent: ImageVector by lazy {
        icon("BallpointPenTipAccent") {
            stroke(width = 2.0f) {
                moveTo(5.2f, 14.6f)
                lineTo(4f, 20f)
                lineTo(9.4f, 18.8f)
            }
        }
    }

    /** 荧光笔：斜切宽头马克笔 + 底部荧光基线 */
    val Highlighter: ImageVector by lazy {
        icon("Highlighter") {
            stroke {
                moveTo(14.5f, 3.5f)
                lineTo(20f, 9f)
                lineTo(11f, 18f)
                lineTo(5.5f, 12.5f)
                close()
                moveTo(5.5f, 12.5f)
                lineTo(3.8f, 16.2f)
                lineTo(7.3f, 19.7f)
                lineTo(11f, 18f)
                moveTo(4f, 20.5f)
                lineTo(17.5f, 20.5f)
            }
        }
    }

    /** 荧光笔宽头与底线着色层 */
    val HighlighterTipAccent: ImageVector by lazy {
        icon("HighlighterTipAccent") {
            stroke(width = 2.0f) {
                moveTo(5.5f, 12.5f)
                lineTo(3.8f, 16.2f)
                lineTo(7.3f, 19.7f)
                lineTo(11f, 18f)
                moveTo(4f, 20.5f)
                lineTo(17.5f, 20.5f)
            }
        }
    }

    /** 橡皮：斜向橡皮块 + 护套分界 + 擦除基线 */
    val Eraser: ImageVector by lazy {
        icon("Eraser") {
            stroke {
                moveTo(7.2f, 19.5f)
                lineTo(19.2f, 7.5f)
                quadTo(20.6f, 6.1f, 19.2f, 4.7f)
                lineTo(17.3f, 2.8f)
                quadTo(15.9f, 1.4f, 14.5f, 2.8f)
                lineTo(3.5f, 13.8f)
                quadTo(2.1f, 15.2f, 3.5f, 16.6f)
                lineTo(6.4f, 19.5f)
                close()
                moveTo(9.8f, 7.5f)
                lineTo(15.8f, 13.5f)
                moveTo(6.4f, 19.5f)
                lineTo(20.5f, 19.5f)
            }
        }
    }

    /** 图形工具：叠合圆与圆角方 */
    val Shapes: ImageVector by lazy {
        icon("Shapes") {
            stroke {
                circle(8.75f, 8.75f, 4.75f)
                roundRect(11.75f, 11.75f, 20.25f, 20.25f, 1.8f)
            }
        }
    }

    /** 激光笔：指向射线 + 发光焦点 + 三向光束 */
    val LaserPointer: ImageVector by lazy {
        icon("LaserPointer") {
            stroke {
                moveTo(4f, 20f)
                lineTo(13.2f, 10.8f)
                moveTo(16.5f, 3f)
                lineTo(16.5f, 4.8f)
                moveTo(21f, 7.5f)
                lineTo(19.2f, 7.5f)
                moveTo(19.8f, 4.2f)
                lineTo(18.5f, 5.5f)
            }
            dot(16.5f, 7.5f, 1.8f)
        }
    }

    /** 激光笔焦点着色层 */
    val LaserTipAccent: ImageVector by lazy {
        icon("LaserTipAccent") {
            dot(16.5f, 7.5f, 2.2f)
            stroke(width = 1.8f) {
                moveTo(16.5f, 3f)
                lineTo(16.5f, 4.8f)
                moveTo(21f, 7.5f)
                lineTo(19.2f, 7.5f)
                moveTo(19.8f, 4.2f)
                lineTo(18.5f, 5.5f)
            }
        }
    }

    /** 阅读/抓手：轻盈线性手掌 */
    val HandPan: ImageVector by lazy {
        icon("HandPan") {
            stroke {
                moveTo(18f, 11.5f)
                lineTo(18f, 6.8f)
                arcTo(1.3f, 1.3f, 0f, false, false, 15.4f, 6.8f)
                lineTo(15.4f, 10.5f)
                lineTo(15.4f, 4.8f)
                arcTo(1.3f, 1.3f, 0f, false, false, 12.8f, 4.8f)
                lineTo(12.8f, 10.5f)
                lineTo(12.8f, 5.8f)
                arcTo(1.3f, 1.3f, 0f, false, false, 10.2f, 5.8f)
                lineTo(10.2f, 12.5f)
                lineTo(7.8f, 10.4f)
                arcTo(1.3f, 1.3f, 0f, false, false, 5.8f, 12.1f)
                lineTo(9.2f, 17.5f)
                quadTo(10.8f, 20.5f, 14f, 20.5f)
                quadTo(18f, 20.5f, 18f, 15.8f)
                close()
            }
        }
    }

    /** 套索：环形套索圈 + 绳结与引线 */
    val Lasso: ImageVector by lazy {
        icon("Lasso") {
            stroke {
                moveTo(3.5f, 10.5f)
                arcTo(8.5f, 6f, 0f, true, true, 20.5f, 10.5f)
                arcTo(8.5f, 6f, 0f, true, true, 3.5f, 10.5f)
                close()
                circle(14.5f, 16.8f, 1.7f)
                moveTo(15.7f, 18.1f)
                quadTo(17.5f, 20f, 19.8f, 20.5f)
            }
        }
    }

    /** 矩形套索：四段虚框角 */
    val LassoRect: ImageVector by lazy {
        icon("LassoRect") {
            stroke {
                moveTo(4f, 8f)
                lineTo(4f, 5.5f)
                quadTo(4f, 4f, 5.5f, 4f)
                lineTo(8f, 4f)
                moveTo(16f, 4f)
                lineTo(18.5f, 4f)
                quadTo(20f, 4f, 20f, 5.5f)
                lineTo(20f, 8f)
                moveTo(20f, 16f)
                lineTo(20f, 18.5f)
                quadTo(20f, 20f, 18.5f, 20f)
                lineTo(16f, 20f)
                moveTo(8f, 20f)
                lineTo(5.5f, 20f)
                quadTo(4f, 20f, 4f, 18.5f)
                lineTo(4f, 16f)
                moveTo(11f, 4f)
                lineTo(13f, 4f)
                moveTo(11f, 20f)
                lineTo(13f, 20f)
                moveTo(4f, 11f)
                lineTo(4f, 13f)
                moveTo(20f, 11f)
                lineTo(20f, 13f)
            }
        }
    }

    /** 图案笔：斜向画笔 + 双星芒装饰 */
    val PatternBrush: ImageVector by lazy {
        icon("PatternBrush") {
            stroke {
                moveTo(4.5f, 19.5f)
                lineTo(14.2f, 9.8f)
                sparkle(17.2f, 6.2f, 3.2f)
                moveTo(6.5f, 6f)
                lineTo(6.5f, 8.8f)
                moveTo(5.1f, 7.4f)
                lineTo(7.9f, 7.4f)
                moveTo(17.5f, 16f)
                lineTo(17.5f, 18.8f)
                moveTo(16.1f, 17.4f)
                lineTo(18.9f, 17.4f)
            }
        }
    }

    /** 图案笔星芒着色层 */
    val PatternBrushAccent: ImageVector by lazy {
        icon("PatternBrushAccent") {
            stroke(width = 1.8f) {
                sparkle(17.2f, 6.2f, 3.2f)
                moveTo(6.5f, 6f)
                lineTo(6.5f, 8.8f)
                moveTo(5.1f, 7.4f)
                lineTo(7.9f, 7.4f)
            }
        }
    }

    /** 胶带：斜贴纸胶带条 + 斜纹 */
    val WashiTape: ImageVector by lazy {
        icon("WashiTape") {
            stroke {
                moveTo(3.5f, 14.5f)
                lineTo(14.5f, 3.5f)
                lineTo(20.5f, 9.5f)
                lineTo(9.5f, 20.5f)
                close()
                moveTo(9.5f, 8.5f)
                lineTo(15.5f, 14.5f)
                moveTo(6.5f, 11.5f)
                lineTo(12.5f, 17.5f)
            }
        }
    }

    /** 摘取工具：框选卡片摘出示意 */
    val SnipExtract: ImageVector by lazy {
        icon("SnipExtract") {
            stroke {
                moveTo(7f, 4f)
                lineTo(5.5f, 4f)
                quadTo(4f, 4f, 4f, 5.5f)
                lineTo(4f, 7f)
                moveTo(17f, 4f)
                lineTo(18.5f, 4f)
                quadTo(20f, 4f, 20f, 5.5f)
                lineTo(20f, 7f)
                moveTo(7f, 20f)
                lineTo(5.5f, 20f)
                quadTo(4f, 20f, 4f, 18.5f)
                lineTo(4f, 17f)
                moveTo(17f, 20f)
                lineTo(18.5f, 20f)
                quadTo(20f, 20f, 20f, 18.5f)
                lineTo(20f, 17f)
                roundRect(8f, 8f, 16f, 16f, 1.5f)
            }
        }
    }

    /** 框选提问：四角取景框 + 居中四芒星 */
    val SnipSpark: ImageVector by lazy {
        icon("SnipSpark") {
            stroke {
                moveTo(7.5f, 3.5f)
                lineTo(5f, 3.5f)
                quadTo(3.5f, 3.5f, 3.5f, 5f)
                lineTo(3.5f, 7.5f)
                moveTo(16.5f, 3.5f)
                lineTo(19f, 3.5f)
                quadTo(20.5f, 3.5f, 20.5f, 5f)
                lineTo(20.5f, 7.5f)
                moveTo(7.5f, 20.5f)
                lineTo(5f, 20.5f)
                quadTo(3.5f, 20.5f, 3.5f, 19f)
                lineTo(3.5f, 16.5f)
                moveTo(16.5f, 20.5f)
                lineTo(19f, 20.5f)
                quadTo(20.5f, 20.5f, 20.5f, 19f)
                lineTo(20.5f, 16.5f)
                sparkle(12f, 12f, 4.3f)
            }
        }
    }

    /** 文本框：带角标框的 T 字 */
    val TextBox: ImageVector by lazy {
        icon("TextBox") {
            stroke {
                roundRect(3.75f, 3.75f, 20.25f, 20.25f, 2.5f)
                moveTo(8f, 8.5f)
                lineTo(16f, 8.5f)
                moveTo(12f, 8.5f)
                lineTo(12f, 16.5f)
                moveTo(10f, 16.5f)
                lineTo(14f, 16.5f)
            }
        }
    }

    /** 纯文字 T */
    val TextCursor: ImageVector by lazy {
        icon("TextCursor") {
            stroke {
                moveTo(5.5f, 6f)
                lineTo(18.5f, 6f)
                moveTo(12f, 6f)
                lineTo(12f, 18.5f)
                moveTo(9.2f, 18.5f)
                lineTo(14.8f, 18.5f)
            }
        }
    }

    /** 笔迹波纹（套索笔迹筛选） */
    val InkStroke: ImageVector by lazy {
        icon("InkStroke") {
            stroke {
                moveTo(3.5f, 15.5f)
                curveTo(6.2f, 8.5f, 9.5f, 8.5f, 12f, 12.5f)
                curveTo(14.5f, 16.5f, 17.8f, 16.5f, 20.5f, 8.5f)
            }
        }
    }

    /** 贴纸：折角方形 */
    val Sticker: ImageVector by lazy {
        icon("Sticker") {
            stroke {
                moveTo(5.5f, 4f)
                lineTo(18.5f, 4f)
                quadTo(20f, 4f, 20f, 5.5f)
                lineTo(20f, 14f)
                lineTo(14f, 20f)
                lineTo(5.5f, 20f)
                quadTo(4f, 20f, 4f, 18.5f)
                lineTo(4f, 5.5f)
                quadTo(4f, 4f, 5.5f, 4f)
                close()
                moveTo(14f, 20f)
                lineTo(14f, 15.5f)
                quadTo(14f, 14f, 15.5f, 14f)
                lineTo(20f, 14f)
            }
        }
    }

    /** 媒体播放（套索媒体筛选） */
    val MediaPlay: ImageVector by lazy {
        icon("MediaPlay") {
            stroke {
                roundRect(3.5f, 5f, 20.5f, 19f, 2.5f)
                moveTo(10f, 9f)
                lineTo(15.5f, 12f)
                lineTo(10f, 15f)
                close()
            }
        }
    }

    // ==================== 图形子工具 ====================

    val ShapeLine: ImageVector by lazy {
        icon("ShapeLine") {
            stroke {
                moveTo(6.2f, 17.8f)
                lineTo(17.8f, 6.2f)
                circle(5f, 19f, 1.4f)
                circle(19f, 5f, 1.4f)
            }
        }
    }

    val ShapeDashedLine: ImageVector by lazy {
        icon("ShapeDashedLine") {
            stroke {
                moveTo(4.5f, 19.5f)
                lineTo(7.8f, 16.2f)
                moveTo(10.3f, 13.7f)
                lineTo(13.7f, 10.3f)
                moveTo(16.2f, 7.8f)
                lineTo(19.5f, 4.5f)
            }
        }
    }

    val ShapeArrow: ImageVector by lazy {
        icon("ShapeArrow") {
            stroke {
                moveTo(5f, 19f)
                lineTo(19f, 5f)
                moveTo(12f, 5f)
                lineTo(19f, 5f)
                lineTo(19f, 12f)
            }
        }
    }

    val ShapeRect: ImageVector by lazy {
        icon("ShapeRect") {
            stroke {
                roundRect(4.25f, 5.5f, 19.75f, 18.5f, 2f)
            }
        }
    }

    val ShapeOval: ImageVector by lazy {
        icon("ShapeOval") {
            stroke {
                circle(12f, 12f, 7.5f)
            }
        }
    }

    val ShapeTriangle: ImageVector by lazy {
        icon("ShapeTriangle") {
            stroke {
                moveTo(12f, 4.5f)
                lineTo(20f, 18.5f)
                lineTo(4f, 18.5f)
                close()
            }
        }
    }

    val ShapeStar: ImageVector by lazy {
        icon("ShapeStar") {
            stroke {
                moveTo(12f, 3.5f)
                lineTo(14.6f, 8.9f)
                lineTo(20.5f, 9.7f)
                lineTo(16.2f, 13.8f)
                lineTo(17.3f, 19.7f)
                lineTo(12f, 16.9f)
                lineTo(6.7f, 19.7f)
                lineTo(7.8f, 13.8f)
                lineTo(3.5f, 9.7f)
                lineTo(9.4f, 8.9f)
                close()
            }
        }
    }

    val ShapeHexagon: ImageVector by lazy {
        icon("ShapeHexagon") {
            stroke {
                moveTo(12f, 3.5f)
                lineTo(19.4f, 7.75f)
                lineTo(19.4f, 16.25f)
                lineTo(12f, 20.5f)
                lineTo(4.6f, 16.25f)
                lineTo(4.6f, 7.75f)
                close()
            }
        }
    }

    // ==================== 文本格式与对齐 ====================

    val AlignLeft: ImageVector by lazy {
        icon("AlignLeft") {
            stroke {
                moveTo(4f, 6.5f); lineTo(20f, 6.5f)
                moveTo(4f, 12f); lineTo(14.5f, 12f)
                moveTo(4f, 17.5f); lineTo(17.5f, 17.5f)
            }
        }
    }

    val AlignCenter: ImageVector by lazy {
        icon("AlignCenter") {
            stroke {
                moveTo(4f, 6.5f); lineTo(20f, 6.5f)
                moveTo(7f, 12f); lineTo(17f, 12f)
                moveTo(5.5f, 17.5f); lineTo(18.5f, 17.5f)
            }
        }
    }

    val AlignRight: ImageVector by lazy {
        icon("AlignRight") {
            stroke {
                moveTo(4f, 6.5f); lineTo(20f, 6.5f)
                moveTo(9.5f, 12f); lineTo(20f, 12f)
                moveTo(6.5f, 17.5f); lineTo(20f, 17.5f)
            }
        }
    }

    val FormatBold: ImageVector by lazy {
        icon("FormatBold") {
            stroke(width = 2.1f) {
                moveTo(7f, 5.5f)
                lineTo(13.2f, 5.5f)
                arcTo(3.2f, 3.2f, 0f, false, true, 13.2f, 11.9f)
                lineTo(7f, 11.9f)
                close()
                moveTo(7f, 11.9f)
                lineTo(14f, 11.9f)
                arcTo(3.3f, 3.3f, 0f, false, true, 14f, 18.5f)
                lineTo(7f, 18.5f)
                close()
            }
        }
    }

    val FormatItalic: ImageVector by lazy {
        icon("FormatItalic") {
            stroke {
                moveTo(10.5f, 5.5f); lineTo(16.5f, 5.5f)
                moveTo(7.5f, 18.5f); lineTo(13.5f, 18.5f)
                moveTo(13.5f, 5.5f); lineTo(10.5f, 18.5f)
            }
        }
    }

    val FormatUnderline: ImageVector by lazy {
        icon("FormatUnderline") {
            stroke {
                moveTo(7.5f, 5.5f)
                lineTo(7.5f, 11.5f)
                arcTo(4.5f, 4.5f, 0f, false, false, 16.5f, 11.5f)
                lineTo(16.5f, 5.5f)
                moveTo(5.5f, 19.5f)
                lineTo(18.5f, 19.5f)
            }
        }
    }

    val FormatStrikethrough: ImageVector by lazy {
        icon("FormatStrikethrough") {
            stroke {
                moveTo(4.5f, 12f); lineTo(19.5f, 12f)
                moveTo(15.8f, 7.8f)
                curveTo(15.2f, 6.2f, 13.8f, 5.5f, 12f, 5.5f)
                curveTo(9.6f, 5.5f, 8.2f, 6.8f, 8.2f, 8.5f)
                moveTo(8.2f, 15.8f)
                curveTo(8.7f, 17.5f, 10.2f, 18.5f, 12.2f, 18.5f)
                curveTo(14.5f, 18.5f, 16f, 17.2f, 16f, 15.2f)
            }
        }
    }

    // ==================== 基础导航与控制 ====================

    val ArrowLeft: ImageVector by lazy {
        icon("ArrowLeft") {
            stroke {
                moveTo(19f, 12f); lineTo(5f, 12f)
                moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
            }
        }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") {
            stroke { moveTo(14.5f, 6.5f); lineTo(9f, 12f); lineTo(14.5f, 17.5f) }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            stroke { moveTo(9.5f, 6.5f); lineTo(15f, 12f); lineTo(9.5f, 17.5f) }
        }
    }

    val ChevronUp: ImageVector by lazy {
        icon("ChevronUp") {
            stroke { moveTo(6.5f, 14.5f); lineTo(12f, 9f); lineTo(17.5f, 14.5f) }
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            stroke { moveTo(6.5f, 9.5f); lineTo(12f, 15f); lineTo(17.5f, 9.5f) }
        }
    }

    val UnfoldMore: ImageVector by lazy {
        icon("UnfoldMore") {
            stroke {
                moveTo(8.5f, 9f); lineTo(12f, 5.5f); lineTo(15.5f, 9f)
                moveTo(8.5f, 15f); lineTo(12f, 18.5f); lineTo(15.5f, 15f)
            }
        }
    }

    val Add: ImageVector by lazy {
        icon("Add") {
            stroke { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            stroke { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            stroke { moveTo(5f, 12.5f); lineTo(9.8f, 17.2f); lineTo(19.2f, 7.2f) }
        }
    }

    val CheckCircle: ImageVector by lazy {
        icon("CheckCircle") {
            stroke {
                circle(12f, 12f, 8.75f)
                moveTo(8.3f, 12.3f); lineTo(10.8f, 14.8f); lineTo(15.8f, 9.5f)
            }
        }
    }

    val ErrorCircle: ImageVector by lazy {
        icon("ErrorCircle") {
            stroke {
                circle(12f, 12f, 8.75f)
                moveTo(12f, 8.2f); lineTo(12f, 12.6f)
            }
            dot(12f, 15.8f, 1.0f)
        }
    }

    val WarningTriangle: ImageVector by lazy {
        icon("WarningTriangle") {
            stroke {
                moveTo(12f, 3.8f)
                lineTo(20.6f, 18.8f)
                quadTo(21f, 19.6f, 20.1f, 19.6f)
                lineTo(3.9f, 19.6f)
                quadTo(3f, 19.6f, 3.4f, 18.8f)
                close()
                moveTo(12f, 9.5f); lineTo(12f, 13.5f)
            }
            dot(12f, 16.4f, 1.0f)
        }
    }

    val InfoCircle: ImageVector by lazy {
        icon("InfoCircle") {
            stroke {
                circle(12f, 12f, 8.75f)
                moveTo(12f, 11.4f); lineTo(12f, 16f)
            }
            dot(12f, 8.2f, 1.0f)
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            stroke {
                circle(10.8f, 10.8f, 6.25f)
                moveTo(15.5f, 15.5f); lineTo(20f, 20f)
            }
        }
    }

    val SearchOff: ImageVector by lazy {
        icon("SearchOff") {
            stroke {
                circle(10.8f, 10.8f, 6.25f)
                moveTo(15.5f, 15.5f); lineTo(20f, 20f)
                moveTo(4f, 4f); lineTo(20f, 20f)
            }
        }
    }

    val MoreDots: ImageVector by lazy {
        icon("MoreDots") {
            dot(5.5f, 12f, 1.35f)
            dot(12f, 12f, 1.35f)
            dot(18.5f, 12f, 1.35f)
        }
    }

    val GripDots: ImageVector by lazy {
        icon("GripDots") {
            dot(9.2f, 7f, 1.25f); dot(14.8f, 7f, 1.25f)
            dot(9.2f, 12f, 1.25f); dot(14.8f, 12f, 1.25f)
            dot(9.2f, 17f, 1.25f); dot(14.8f, 17f, 1.25f)
        }
    }

    /** 展开笔匣：笔匣胶囊与内部笔锋，替代拖拽点 */
    val PenDock: ImageVector by lazy {
        icon("PenDock") {
            stroke {
                roundRect(3f, 7f, 21f, 17f, 5f)
                moveTo(8f, 12f); lineTo(12f, 12f)
                moveTo(15f, 10.5f); lineTo(17f, 12f); lineTo(15f, 13.5f)
            }
        }
    }

    val Sidebar: ImageVector by lazy {
        icon("Sidebar") {
            stroke {
                roundRect(3.25f, 4.25f, 20.75f, 19.75f, 2.5f)
                moveTo(9f, 4.25f); lineTo(9f, 19.75f)
            }
        }
    }

    val SplitView: ImageVector by lazy {
        icon("SplitView") {
            stroke {
                roundRect(3.25f, 4.25f, 20.75f, 19.75f, 2.5f)
                moveTo(12f, 4.25f); lineTo(12f, 19.75f)
            }
        }
    }

    val FocusExpand: ImageVector by lazy {
        icon("FocusExpand") {
            stroke {
                moveTo(8.5f, 4f); lineTo(4f, 4f); lineTo(4f, 8.5f)
                moveTo(15.5f, 4f); lineTo(20f, 4f); lineTo(20f, 8.5f)
                moveTo(4f, 15.5f); lineTo(4f, 20f); lineTo(8.5f, 20f)
                moveTo(20f, 15.5f); lineTo(20f, 20f); lineTo(15.5f, 20f)
            }
        }
    }

    val Undo: ImageVector by lazy {
        icon("Undo") {
            stroke {
                moveTo(8.5f, 6.5f); lineTo(4f, 11f); lineTo(8.5f, 15.5f)
                moveTo(4.5f, 11f); lineTo(14.5f, 11f)
                arcTo(4.75f, 4.75f, 0f, true, true, 14.5f, 20.5f)
                lineTo(12f, 20.5f)
            }
        }
    }

    val Redo: ImageVector by lazy {
        icon("Redo") {
            stroke {
                moveTo(15.5f, 6.5f); lineTo(20f, 11f); lineTo(15.5f, 15.5f)
                moveTo(19.5f, 11f); lineTo(9.5f, 11f)
                arcTo(4.75f, 4.75f, 0f, true, false, 9.5f, 20.5f)
                lineTo(12f, 20.5f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            stroke {
                moveTo(19.5f, 10.5f)
                arcTo(7.5f, 7.5f, 0f, true, false, 18.2f, 16.5f)
                moveTo(19.5f, 5.5f); lineTo(19.5f, 10.5f); lineTo(14.5f, 10.5f)
            }
        }
    }

    val Reset: ImageVector by lazy {
        icon("Reset") {
            stroke {
                moveTo(4.5f, 10.5f)
                arcTo(7.5f, 7.5f, 0f, true, true, 5.8f, 16.5f)
                moveTo(4.5f, 5.5f); lineTo(4.5f, 10.5f); lineTo(9.5f, 10.5f)
            }
        }
    }

    val Sliders: ImageVector by lazy {
        icon("Sliders") {
            stroke {
                moveTo(4f, 7.5f); lineTo(7.5f, 7.5f)
                moveTo(11.5f, 7.5f); lineTo(20f, 7.5f)
                circle(9.5f, 7.5f, 2f)
                moveTo(4f, 16.5f); lineTo(12.5f, 16.5f)
                moveTo(16.5f, 16.5f); lineTo(20f, 16.5f)
                circle(14.5f, 16.5f, 2f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            stroke {
                circle(12f, 12f, 3.2f)
                moveTo(12f, 3f); lineTo(12f, 5.2f)
                moveTo(12f, 18.8f); lineTo(12f, 21f)
                moveTo(3f, 12f); lineTo(5.2f, 12f)
                moveTo(18.8f, 12f); lineTo(21f, 12f)
                moveTo(5.6f, 5.6f); lineTo(7.2f, 7.2f)
                moveTo(16.8f, 16.8f); lineTo(18.4f, 18.4f)
                moveTo(18.4f, 5.6f); lineTo(16.8f, 7.2f)
                moveTo(7.2f, 16.8f); lineTo(5.6f, 18.4f)
                circle(12f, 12f, 6.8f)
            }
        }
    }

    /** 笔诊断：波形脉冲 */
    val PulseDiag: ImageVector by lazy {
        icon("PulseDiag") {
            stroke {
                roundRect(3.25f, 4.25f, 20.75f, 19.75f, 2.8f)
                moveTo(6f, 12.5f)
                lineTo(8.8f, 12.5f)
                lineTo(10.6f, 8.2f)
                lineTo(13.4f, 16.2f)
                lineTo(15.2f, 12.5f)
                lineTo(18f, 12.5f)
            }
        }
    }

    /** 清空画布：扫除刷 */
    val ClearSweep: ImageVector by lazy {
        icon("ClearSweep") {
            stroke {
                moveTo(4f, 19.5f); lineTo(20f, 19.5f)
                moveTo(14.5f, 4f); lineTo(11f, 10.5f)
                moveTo(7f, 11.5f); lineTo(15f, 11.5f)
                quadTo(16.5f, 11.5f, 16.8f, 13f)
                lineTo(17.5f, 17f)
                lineTo(5.5f, 17f)
                lineTo(6.2f, 13f)
                quadTo(6.4f, 11.5f, 7f, 11.5f)
                close()
                moveTo(9.5f, 14.5f); lineTo(9.5f, 17f)
                moveTo(13f, 14.5f); lineTo(13f, 17f)
            }
        }
    }

    // ==================== 书架、笔记本、文件夹与卡片 ====================

    val Book: ImageVector by lazy {
        icon("Book") {
            stroke {
                moveTo(4.5f, 5.2f)
                quadTo(4.5f, 3.5f, 6.5f, 3.5f)
                lineTo(19f, 3.5f)
                lineTo(19f, 19.5f)
                lineTo(6.5f, 19.5f)
                quadTo(4.5f, 19.5f, 4.5f, 17.8f)
                close()
                moveTo(4.5f, 17.5f)
                quadTo(4.5f, 16f, 6.5f, 16f)
                lineTo(19f, 16f)
                moveTo(8.5f, 7.5f); lineTo(15.5f, 7.5f)
                moveTo(8.5f, 11f); lineTo(13.5f, 11f)
            }
        }
    }

    val Star: ImageVector by lazy {
        icon("Star") {
            stroke {
                moveTo(12f, 3.4f)
                lineTo(14.6f, 8.8f)
                lineTo(20.5f, 9.6f)
                lineTo(16.2f, 13.7f)
                lineTo(17.2f, 19.6f)
                lineTo(12f, 16.8f)
                lineTo(6.8f, 19.6f)
                lineTo(7.8f, 13.7f)
                lineTo(3.5f, 9.6f)
                lineTo(9.4f, 8.8f)
                close()
            }
        }
    }

    val Cards: ImageVector by lazy {
        icon("Cards") {
            stroke {
                roundRect(3.5f, 5.5f, 16.5f, 19.5f, 2.2f)
                moveTo(7.5f, 3.5f)
                lineTo(18f, 3.5f)
                quadTo(20.5f, 3.5f, 20.5f, 6f)
                lineTo(20.5f, 16.5f)
                moveTo(7f, 10.5f); lineTo(13f, 10.5f)
                moveTo(7f, 14f); lineTo(11f, 14f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            stroke {
                moveTo(4.5f, 6.8f); lineTo(19.5f, 6.8f)
                moveTo(9f, 6.8f); lineTo(9f, 4.5f)
                quadTo(9f, 3.6f, 10f, 3.6f)
                lineTo(14f, 3.6f)
                quadTo(15f, 3.6f, 15f, 4.5f)
                lineTo(15f, 6.8f)
                moveTo(6.2f, 6.8f)
                lineTo(7.1f, 18.8f)
                quadTo(7.2f, 20.2f, 8.6f, 20.2f)
                lineTo(15.4f, 20.2f)
                quadTo(16.8f, 20.2f, 16.9f, 18.8f)
                lineTo(17.8f, 6.8f)
                moveTo(10.2f, 10.8f); lineTo(10.2f, 16.2f)
                moveTo(13.8f, 10.8f); lineTo(13.8f, 16.2f)
            }
        }
    }

    val RestoreTrash: ImageVector by lazy {
        icon("RestoreTrash") {
            stroke {
                moveTo(4.5f, 6.8f); lineTo(19.5f, 6.8f)
                moveTo(9f, 6.8f); lineTo(9f, 4.5f)
                quadTo(9f, 3.6f, 10f, 3.6f)
                lineTo(14f, 3.6f)
                quadTo(15f, 3.6f, 15f, 4.5f)
                lineTo(15f, 6.8f)
                moveTo(6.2f, 6.8f)
                lineTo(7.1f, 18.8f)
                quadTo(7.2f, 20.2f, 8.6f, 20.2f)
                lineTo(15.4f, 20.2f)
                quadTo(16.8f, 20.2f, 16.9f, 18.8f)
                lineTo(17.8f, 6.8f)
                moveTo(12f, 16.2f); lineTo(12f, 10.4f)
                moveTo(9.5f, 12.6f); lineTo(12f, 10.2f); lineTo(14.5f, 12.6f)
            }
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            stroke {
                moveTo(3.5f, 6.8f)
                quadTo(3.5f, 5f, 5.3f, 5f)
                lineTo(9.2f, 5f)
                lineTo(11.2f, 7.4f)
                lineTo(18.7f, 7.4f)
                quadTo(20.5f, 7.4f, 20.5f, 9.2f)
                lineTo(20.5f, 17.5f)
                quadTo(20.5f, 19.3f, 18.7f, 19.3f)
                lineTo(5.3f, 19.3f)
                quadTo(3.5f, 19.3f, 3.5f, 17.5f)
                close()
            }
        }
    }

    val FolderOpen: ImageVector by lazy {
        icon("FolderOpen") {
            stroke {
                moveTo(3.5f, 18f)
                lineTo(3.5f, 6.5f)
                quadTo(3.5f, 5f, 5f, 5f)
                lineTo(9f, 5f)
                lineTo(11f, 7.2f)
                lineTo(18f, 7.2f)
                quadTo(19.5f, 7.2f, 19.5f, 8.7f)
                lineTo(19.5f, 10.5f)
                moveTo(3.5f, 18.2f)
                lineTo(5.8f, 11.2f)
                quadTo(6.1f, 10.4f, 7f, 10.4f)
                lineTo(20f, 10.4f)
                quadTo(21f, 10.4f, 20.7f, 11.4f)
                lineTo(18.6f, 18.2f)
                quadTo(18.3f, 19f, 17.4f, 19f)
                lineTo(4.5f, 19f)
                quadTo(3.5f, 19f, 3.5f, 18.2f)
                close()
            }
        }
    }

    val FolderOff: ImageVector by lazy {
        icon("FolderOff") {
            stroke {
                moveTo(3.5f, 6.8f)
                quadTo(3.5f, 5f, 5.3f, 5f)
                lineTo(9.2f, 5f)
                lineTo(11.2f, 7.4f)
                lineTo(18.7f, 7.4f)
                quadTo(20.5f, 7.4f, 20.5f, 9.2f)
                lineTo(20.5f, 16.5f)
                moveTo(16.8f, 19.3f)
                lineTo(5.3f, 19.3f)
                quadTo(3.5f, 19.3f, 3.5f, 17.5f)
                lineTo(3.5f, 9f)
                moveTo(4f, 4f); lineTo(20f, 20f)
            }
        }
    }

    val GridView: ImageVector by lazy {
        icon("GridView") {
            stroke {
                roundRect(4f, 4f, 10.2f, 10.2f, 1.8f)
                roundRect(13.8f, 4f, 20f, 10.2f, 1.8f)
                roundRect(4f, 13.8f, 10.2f, 20f, 1.8f)
                roundRect(13.8f, 13.8f, 20f, 20f, 1.8f)
            }
        }
    }

    val ListBullet: ImageVector by lazy {
        icon("ListBullet") {
            stroke {
                moveTo(8.5f, 6.5f); lineTo(20f, 6.5f)
                moveTo(8.5f, 12f); lineTo(20f, 12f)
                moveTo(8.5f, 17.5f); lineTo(20f, 17.5f)
            }
            dot(4.8f, 6.5f, 1.1f)
            dot(4.8f, 12f, 1.1f)
            dot(4.8f, 17.5f, 1.1f)
        }
    }

    val Sort: ImageVector by lazy {
        icon("Sort") {
            stroke {
                moveTo(4f, 6.5f); lineTo(14.5f, 6.5f)
                moveTo(4f, 12f); lineTo(11.5f, 12f)
                moveTo(4f, 17.5f); lineTo(8.5f, 17.5f)
                moveTo(17.5f, 6.5f); lineTo(17.5f, 18f)
                moveTo(14.8f, 15.2f); lineTo(17.5f, 18f); lineTo(20.2f, 15.2f)
            }
        }
    }

    val ImportDoc: ImageVector by lazy {
        icon("ImportDoc") {
            stroke {
                moveTo(14f, 3.5f)
                lineTo(6.5f, 3.5f)
                quadTo(5f, 3.5f, 5f, 5f)
                lineTo(5f, 19f)
                quadTo(5f, 20.5f, 6.5f, 20.5f)
                lineTo(17.5f, 20.5f)
                quadTo(19f, 20.5f, 19f, 19f)
                lineTo(19f, 8.5f)
                close()
                moveTo(14f, 3.5f); lineTo(14f, 8.5f); lineTo(19f, 8.5f)
                moveTo(12f, 11.5f); lineTo(12f, 17f)
                moveTo(9.5f, 14.5f); lineTo(12f, 17f); lineTo(14.5f, 14.5f)
            }
        }
    }

    val Export: ImageVector by lazy {
        icon("Export") {
            stroke {
                moveTo(12f, 15f); lineTo(12f, 4f)
                moveTo(8.2f, 7.8f); lineTo(12f, 4f); lineTo(15.8f, 7.8f)
                moveTo(4.5f, 14.5f)
                lineTo(4.5f, 18.2f)
                quadTo(4.5f, 20f, 6.3f, 20f)
                lineTo(17.7f, 20f)
                quadTo(19.5f, 20f, 19.5f, 18.2f)
                lineTo(19.5f, 14.5f)
            }
        }
    }

    val PageAdd: ImageVector by lazy {
        icon("PageAdd") {
            stroke {
                moveTo(14f, 3.5f)
                lineTo(6.5f, 3.5f)
                quadTo(5f, 3.5f, 5f, 5f)
                lineTo(5f, 19f)
                quadTo(5f, 20.5f, 6.5f, 20.5f)
                lineTo(17.5f, 20.5f)
                quadTo(19f, 20.5f, 19f, 19f)
                lineTo(19f, 8.5f)
                close()
                moveTo(14f, 3.5f); lineTo(14f, 8.5f); lineTo(19f, 8.5f)
                moveTo(12f, 11.8f); lineTo(12f, 16.8f)
                moveTo(9.5f, 14.3f); lineTo(14.5f, 14.3f)
            }
        }
    }

    val Article: ImageVector by lazy {
        icon("Article") {
            stroke {
                roundRect(4.5f, 3.5f, 19.5f, 20.5f, 2f)
                moveTo(8f, 8.2f); lineTo(16f, 8.2f)
                moveTo(8f, 12f); lineTo(16f, 12f)
                moveTo(8f, 15.8f); lineTo(13f, 15.8f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        icon("Copy") {
            stroke {
                roundRect(8.5f, 8.5f, 19.5f, 20.5f, 2f)
                moveTo(6.5f, 15.5f)
                lineTo(5.8f, 15.5f)
                quadTo(4.5f, 15.5f, 4.5f, 14.2f)
                lineTo(4.5f, 5.2f)
                quadTo(4.5f, 3.5f, 6.2f, 3.5f)
                lineTo(14.5f, 3.5f)
                quadTo(15.5f, 3.5f, 15.5f, 4.8f)
                lineTo(15.5f, 6f)
            }
        }
    }

    val Edit: ImageVector by lazy {
        icon("Edit") {
            stroke {
                moveTo(15.5f, 4.5f)
                lineTo(19.5f, 8.5f)
                lineTo(9.2f, 18.8f)
                lineTo(4.5f, 19.5f)
                lineTo(5.2f, 14.8f)
                close()
                moveTo(13.2f, 6.8f); lineTo(17.2f, 10.8f)
            }
        }
    }

    val Pin: ImageVector by lazy {
        icon("Pin") {
            stroke {
                moveTo(9f, 4.5f); lineTo(15f, 4.5f)
                moveTo(10f, 4.5f); lineTo(9f, 11f)
                moveTo(14f, 4.5f); lineTo(15f, 11f)
                moveTo(6.5f, 13.5f)
                quadTo(6.5f, 11f, 9f, 11f)
                lineTo(15f, 11f)
                quadTo(17.5f, 11f, 17.5f, 13.5f)
                close()
                moveTo(12f, 13.5f); lineTo(12f, 20.5f)
            }
        }
    }

    val Bookmark: ImageVector by lazy {
        icon("Bookmark") {
            stroke {
                moveTo(6.5f, 4.5f)
                quadTo(6.5f, 3.5f, 7.5f, 3.5f)
                lineTo(16.5f, 3.5f)
                quadTo(17.5f, 3.5f, 17.5f, 4.5f)
                lineTo(17.5f, 20.5f)
                lineTo(12f, 16.5f)
                lineTo(6.5f, 20.5f)
                close()
            }
        }
    }

    val NoteInsert: ImageVector by lazy {
        icon("NoteInsert") {
            stroke {
                roundRect(4.5f, 3.5f, 19.5f, 20.5f, 2f)
                moveTo(12f, 9f); lineTo(12f, 15f)
                moveTo(9f, 12f); lineTo(15f, 12f)
            }
        }
    }

    val ExpandOpen: ImageVector by lazy {
        icon("ExpandOpen") {
            stroke {
                moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f)
                moveTo(20f, 4f); lineTo(13.5f, 10.5f)
                moveTo(10f, 20f); lineTo(4f, 20f); lineTo(4f, 14f)
                moveTo(4f, 20f); lineTo(10.5f, 13.5f)
            }
        }
    }

    // ==================== 媒体、相机、音频与画布选中 ====================

    val Image: ImageVector by lazy {
        icon("Image") {
            stroke {
                roundRect(3.5f, 4.5f, 20.5f, 19.5f, 2.5f)
                moveTo(4f, 16.8f); lineTo(9.2f, 11.6f); lineTo(13.5f, 15.8f)
                moveTo(12.2f, 14.5f); lineTo(15.2f, 11.8f); lineTo(20.2f, 16.5f)
            }
            dot(8.5f, 8.8f, 1.35f)
        }
    }

    val PhotoLibrary: ImageVector by lazy {
        icon("PhotoLibrary") {
            stroke {
                roundRect(6.5f, 3.5f, 20.5f, 17.5f, 2.2f)
                moveTo(7f, 14.2f); lineTo(11.2f, 10.2f); lineTo(14.5f, 13.4f)
                moveTo(13.8f, 12.6f); lineTo(16.2f, 10.5f); lineTo(20.2f, 14.2f)
                moveTo(3.5f, 8f)
                lineTo(3.5f, 18.5f)
                quadTo(3.5f, 20.5f, 5.5f, 20.5f)
                lineTo(16f, 20.5f)
            }
            dot(10.5f, 7.2f, 1.15f)
        }
    }

    val BrokenImage: ImageVector by lazy {
        icon("BrokenImage") {
            stroke {
                roundRect(3.5f, 4.5f, 20.5f, 19.5f, 2.5f)
                moveTo(4f, 13.5f); lineTo(8.5f, 11f); lineTo(12f, 14f); lineTo(15.5f, 10.5f); lineTo(20f, 13f)
                moveTo(4f, 4f); lineTo(20f, 20f)
            }
        }
    }

    val Camera: ImageVector by lazy {
        icon("Camera") {
            stroke {
                moveTo(4.5f, 7.5f)
                lineTo(7.5f, 7.5f)
                lineTo(9.2f, 5f)
                lineTo(14.8f, 5f)
                lineTo(16.5f, 7.5f)
                lineTo(19.5f, 7.5f)
                quadTo(21f, 7.5f, 21f, 9f)
                lineTo(21f, 18f)
                quadTo(21f, 19.5f, 19.5f, 19.5f)
                lineTo(4.5f, 19.5f)
                quadTo(3f, 19.5f, 3f, 18f)
                lineTo(3f, 9f)
                quadTo(3f, 7.5f, 4.5f, 7.5f)
                close()
                circle(12f, 13.2f, 3.4f)
            }
        }
    }

    val Video: ImageVector by lazy {
        icon("Video") {
            stroke {
                roundRect(3f, 6.5f, 15.5f, 17.5f, 2.2f)
                moveTo(15.5f, 10.5f)
                lineTo(21f, 7.5f)
                lineTo(21f, 16.5f)
                lineTo(15.5f, 13.5f)
                close()
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") {
            stroke {
                moveTo(7.5f, 5.2f)
                lineTo(18.5f, 12f)
                lineTo(7.5f, 18.8f)
                close()
            }
        }
    }

    val Mic: ImageVector by lazy {
        icon("Mic") {
            stroke {
                roundRect(9.2f, 3.2f, 14.8f, 13.5f, 2.8f)
                moveTo(5.5f, 10.8f)
                arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 10.8f)
                moveTo(12f, 17.3f); lineTo(12f, 20.8f)
                moveTo(8.5f, 20.8f); lineTo(15.5f, 20.8f)
            }
        }
    }

    val MagnetSnap: ImageVector by lazy {
        icon("MagnetSnap") {
            stroke {
                moveTo(6.5f, 4.5f)
                lineTo(6.5f, 12.5f)
                arcTo(5.5f, 5.5f, 0f, false, false, 17.5f, 12.5f)
                lineTo(17.5f, 4.5f)
                moveTo(6.5f, 8.2f); lineTo(9.5f, 8.2f)
                moveTo(14.5f, 8.2f); lineTo(17.5f, 8.2f)
                moveTo(9.5f, 4.5f); lineTo(9.5f, 12.5f)
                arcTo(2.5f, 2.5f, 0f, false, false, 14.5f, 12.5f)
                lineTo(14.5f, 4.5f)
                moveTo(6.5f, 4.5f); lineTo(9.5f, 4.5f)
                moveTo(14.5f, 4.5f); lineTo(17.5f, 4.5f)
            }
        }
    }

    // ==================== AI 抽屉、会话、图片编辑与预览 ====================

    val Sparkle: ImageVector by lazy {
        icon("Sparkle") {
            stroke {
                sparkle(11f, 12f, 7.5f)
                moveTo(18.5f, 4f); lineTo(18.5f, 7.6f)
                moveTo(16.7f, 5.8f); lineTo(20.3f, 5.8f)
            }
        }
    }

    val History: ImageVector by lazy {
        icon("History") {
            stroke {
                moveTo(4.5f, 12f)
                arcTo(7.5f, 7.5f, 0f, true, true, 6.8f, 17.4f)
                moveTo(4.5f, 7f); lineTo(4.5f, 12f); lineTo(9.5f, 12f)
                moveTo(12f, 8f); lineTo(12f, 12.2f); lineTo(15f, 14f)
            }
        }
    }

    val Clock: ImageVector by lazy {
        icon("Clock") {
            stroke {
                circle(12f, 12f, 8.5f)
                moveTo(12f, 7.5f); lineTo(12f, 12.2f); lineTo(15.2f, 14.2f)
            }
        }
    }

    val AttachClip: ImageVector by lazy {
        icon("AttachClip") {
            stroke {
                moveTo(19.5f, 11.2f)
                lineTo(11.3f, 19.4f)
                arcTo(4.4f, 4.4f, 0f, false, true, 5.1f, 13.2f)
                lineTo(13.8f, 4.5f)
                arcTo(2.9f, 2.9f, 0f, false, true, 17.9f, 8.6f)
                lineTo(9.4f, 17.1f)
                arcTo(1.4f, 1.4f, 0f, false, true, 7.4f, 15.1f)
                lineTo(15f, 7.5f)
            }
        }
    }

    val Send: ImageVector by lazy {
        icon("Send") {
            stroke {
                moveTo(20.5f, 3.5f)
                lineTo(14f, 20.5f)
                lineTo(10.8f, 13.2f)
                lineTo(3.5f, 10f)
                close()
                moveTo(20.5f, 3.5f); lineTo(10.8f, 13.2f)
            }
        }
    }

    val StopSquare: ImageVector by lazy {
        icon("StopSquare") {
            stroke {
                roundRect(6.2f, 6.2f, 17.8f, 17.8f, 2f)
            }
        }
    }

    val RotateLeft: ImageVector by lazy {
        icon("RotateLeft") {
            stroke {
                moveTo(5.5f, 10.5f)
                arcTo(7f, 7f, 0f, true, true, 7.2f, 16.5f)
                moveTo(5.5f, 5.5f); lineTo(5.5f, 10.5f); lineTo(10.5f, 10.5f)
            }
        }
    }

    val RotateRight: ImageVector by lazy {
        icon("RotateRight") {
            stroke {
                moveTo(18.5f, 10.5f)
                arcTo(7f, 7f, 0f, true, false, 16.8f, 16.5f)
                moveTo(18.5f, 5.5f); lineTo(18.5f, 10.5f); lineTo(13.5f, 10.5f)
            }
        }
    }

    val FlipHorizontal: ImageVector by lazy {
        icon("FlipHorizontal") {
            stroke {
                moveTo(12f, 4f); lineTo(12f, 20f)
                moveTo(8.5f, 7f); lineTo(4f, 12f); lineTo(8.5f, 17f); close()
                moveTo(15.5f, 7f); lineTo(20f, 12f); lineTo(15.5f, 17f); close()
            }
        }
    }

    val FlipVertical: ImageVector by lazy {
        icon("FlipVertical") {
            stroke {
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(7f, 8.5f); lineTo(12f, 4f); lineTo(17f, 8.5f); close()
                moveTo(7f, 15.5f); lineTo(12f, 20f); lineTo(17f, 15.5f); close()
            }
        }
    }

    val Brightness: ImageVector by lazy {
        icon("Brightness") {
            stroke {
                circle(12f, 12f, 4f)
                moveTo(12f, 3.2f); lineTo(12f, 5f)
                moveTo(12f, 19f); lineTo(12f, 20.8f)
                moveTo(3.2f, 12f); lineTo(5f, 12f)
                moveTo(19f, 12f); lineTo(20.8f, 12f)
                moveTo(5.8f, 5.8f); lineTo(7.1f, 7.1f)
                moveTo(16.9f, 16.9f); lineTo(18.2f, 18.2f)
                moveTo(18.2f, 5.8f); lineTo(16.9f, 7.1f)
                moveTo(7.1f, 16.9f); lineTo(5.8f, 18.2f)
            }
        }
    }

    val DocumentEnhance: ImageVector by lazy {
        icon("DocumentEnhance") {
            stroke {
                moveTo(7.5f, 4f); lineTo(4.8f, 4f)
                quadTo(4f, 4f, 4f, 4.8f); lineTo(4f, 7.5f)
                moveTo(16.5f, 4f); lineTo(19.2f, 4f)
                quadTo(20f, 4f, 20f, 4.8f); lineTo(20f, 7.5f)
                moveTo(7.5f, 20f); lineTo(4.8f, 20f)
                quadTo(4f, 20f, 4f, 19.2f); lineTo(4f, 16.5f)
                moveTo(16.5f, 20f); lineTo(19.2f, 20f)
                quadTo(20f, 20f, 20f, 19.2f); lineTo(20f, 16.5f)
                moveTo(7.5f, 10f); lineTo(16.5f, 10f)
                moveTo(7.5f, 14f); lineTo(13.5f, 14f)
            }
        }
    }

    val CheckBoxChecked: ImageVector by lazy {
        icon("CheckBoxChecked") {
            stroke {
                roundRect(4f, 4f, 20f, 20f, 3f)
                moveTo(8f, 12.2f); lineTo(10.8f, 15f); lineTo(16.2f, 9.2f)
            }
        }
    }

    val CheckBoxUnchecked: ImageVector by lazy {
        icon("CheckBoxUnchecked") {
            stroke {
                roundRect(4f, 4f, 20f, 20f, 3f)
            }
        }
    }

    // ==================== 设置侧栏与导出格式 ====================

    val Palette: ImageVector by lazy {
        icon("Palette") {
            stroke {
                moveTo(12f, 3.5f)
                arcTo(8.5f, 8.5f, 0f, true, false, 12f, 20.5f)
                quadTo(14.5f, 20.5f, 14.5f, 18.2f)
                quadTo(14.5f, 16.5f, 16.2f, 16.5f)
                lineTo(18f, 16.5f)
                arcTo(2.5f, 2.5f, 0f, false, false, 20.5f, 14f)
                arcTo(8.5f, 8.5f, 0f, false, false, 12f, 3.5f)
                close()
            }
            dot(8f, 10.5f, 1.1f)
            dot(10.8f, 7.2f, 1.1f)
            dot(15.2f, 8.5f, 1.1f)
        }
    }

    /** 触控笔：细长焦距触控笔 + 侧键 */
    val Stylus: ImageVector by lazy {
        icon("Stylus") {
            stroke {
                moveTo(17.2f, 3.8f)
                lineTo(20.2f, 6.8f)
                lineTo(9.2f, 17.8f)
                lineTo(4.2f, 19.8f)
                lineTo(6.2f, 14.8f)
                close()
                moveTo(11f, 10f); lineTo(13.5f, 7.5f)
            }
        }
    }

    val Keyboard: ImageVector by lazy {
        icon("Keyboard") {
            stroke {
                roundRect(2.75f, 5.5f, 21.25f, 18.5f, 2.5f)
                moveTo(6.5f, 9.5f); lineTo(7.5f, 9.5f)
                moveTo(11.5f, 9.5f); lineTo(12.5f, 9.5f)
                moveTo(16.5f, 9.5f); lineTo(17.5f, 9.5f)
                moveTo(6.5f, 12.5f); lineTo(7.5f, 12.5f)
                moveTo(11.5f, 12.5f); lineTo(12.5f, 12.5f)
                moveTo(16.5f, 12.5f); lineTo(17.5f, 12.5f)
                moveTo(8f, 15.5f); lineTo(16f, 15.5f)
            }
        }
    }

    /** 设置侧栏「技能与工具」图标：模块方阵 */
    val Blocks: ImageVector by lazy {
        icon("Blocks") {
            stroke {
                roundRect(4f, 4f, 10.5f, 10.5f, 1.8f)
                roundRect(13.5f, 4f, 20f, 10.5f, 1.8f)
                roundRect(4f, 13.5f, 10.5f, 20f, 1.8f)
                moveTo(16.75f, 13.8f); lineTo(16.75f, 19.7f)
                moveTo(13.8f, 16.75f); lineTo(19.7f, 16.75f)
            }
        }
    }

    /** 存储与更新：磁盘与同步更新 */
    val StorageUpdate: ImageVector by lazy {
        icon("StorageUpdate") {
            stroke {
                roundRect(4f, 4.5f, 20f, 11.2f, 2f)
                roundRect(4f, 13.5f, 20f, 19.5f, 2f)
                moveTo(7.5f, 7.8f); lineTo(11.5f, 7.8f)
                moveTo(7.5f, 16.5f); lineTo(11.5f, 16.5f)
            }
            dot(16.5f, 7.8f, 1.0f)
            dot(16.5f, 16.5f, 1.0f)
        }
    }

    val FilePdf: ImageVector by lazy {
        icon("FilePdf") {
            stroke {
                moveTo(14f, 3.5f); lineTo(6.5f, 3.5f)
                quadTo(5f, 3.5f, 5f, 5f); lineTo(5f, 19f)
                quadTo(5f, 20.5f, 6.5f, 20.5f); lineTo(17.5f, 20.5f)
                quadTo(19f, 20.5f, 19f, 19f); lineTo(19f, 8.5f); close()
                moveTo(14f, 3.5f); lineTo(14f, 8.5f); lineTo(19f, 8.5f)
                moveTo(8.5f, 12.2f); lineTo(15.5f, 12.2f)
                moveTo(8.5f, 15.8f); lineTo(13.5f, 15.8f)
            }
        }
    }

    val FileZip: ImageVector by lazy {
        icon("FileZip") {
            stroke {
                roundRect(4.5f, 4f, 19.5f, 20f, 2.5f)
                moveTo(12f, 4f); lineTo(12f, 14f)
                moveTo(10.5f, 7f); lineTo(12f, 7f)
                moveTo(12f, 9.5f); lineTo(13.5f, 9.5f)
                moveTo(10.5f, 12f); lineTo(12f, 12f)
                roundRect(10f, 14f, 14f, 17.5f, 1f)
            }
        }
    }
}
