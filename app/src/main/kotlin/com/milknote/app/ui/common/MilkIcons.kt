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
import androidx.compose.ui.graphics.vector.addPathNodes
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
 * MilkNote 统一线性矢量图标库（基于 Lucide / Solar / Tabler 标准 24×24 线性几何）：
 * 统一 1.7f 等宽圆角描边（StrokeCap.Round / StrokeJoin.Round），
 * 对齐「技能与工具」页的轻盈线性风格（技能与工具页内部图标保持独立）。
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

    private fun ImageVector.Builder.svgStroke(
        pathData: String,
        width: Float = 1.7f,
    ) {
        addPath(
            pathData = addPathNodes(pathData),
            fill = null,
            stroke = ink,
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
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

    /** 钢笔（Lucide pen-tool）：经典钢笔尖 + 气孔 + 笔握环 */
    val FountainPen: ImageVector by lazy {
        icon("FountainPen") {
            svgStroke("m18 13l-.654-4.736a4 4 0 0 0-2.756-3.266l-9.163-2.9a2 2 0 0 0-2.017.493l-.82.819a2 2 0 0 0-.492 2.017l2.9 9.163a4 4 0 0 0 3.266 2.756L13 18")
            svgStroke("M18.648 12.352a1.205 1.205 0 0 1 1.704 0l1.296 1.296a1.205 1.205 0 0 1 0 1.704l-6.296 6.296a1.205 1.205 0 0 1-1.704 0l-1.296-1.296a1.205 1.205 0 0 1 0-1.704zM3 3l6.586 6.586")
            stroke { circle(11f, 11f, 2f) }
        }
    }

    /** 钢笔笔尖着色层（用于双层色笔图标） */
    val FountainPenNibAccent: ImageVector by lazy {
        icon("FountainPenNibAccent") {
            svgStroke("M3 3l6.586 6.586", width = 1.9f)
            stroke(width = 1.9f) { circle(11f, 11f, 2f) }
        }
    }

    /** 圆珠笔（Tabler ballpen）：圆润笔杆 + 笔夹 + 圆珠笔尖 */
    val BallpointPen: ImageVector by lazy {
        icon("BallpointPen") {
            svgStroke("m14 6l7 7l-4 4")
            svgStroke("M5.828 18.172a2.83 2.83 0 0 0 4 0L20.414 7.586a2 2 0 0 0 0-2.829l-1.171-1.171a2 2 0 0 0-2.829 0L5.828 14.172a2.83 2.83 0 0 0 0 4M4 20l1.768-1.768")
        }
    }

    /** 圆珠笔笔头着色层 */
    val BallpointPenTipAccent: ImageVector by lazy {
        icon("BallpointPenTipAccent") {
            svgStroke("M4 20l1.768-1.768", width = 2.0f)
            svgStroke("m14 6l7 7l-4 4", width = 1.85f)
        }
    }

    /** 荧光笔（Solar highlighter-linear）：流线型斜切马克笔身 + 领口 + 荧光基线 */
    val Highlighter: ImageVector by lazy {
        icon("Highlighter") {
            svgStroke(
                "M11.9435 6.15807L13.3931 4.70847C14.4264 3.67517 14.9431 3.15857 15.5851 3.15857C16.2272 3.15857 16.7437 3.67509 17.777 4.70823L17.7772 4.70847L18.3429 5.27417L18.3432 5.2744C19.3763 6.30765 19.8928 6.82422 19.8928 7.46627C19.8928 8.10827 19.3762 8.62497 18.3429 9.65827L14.3123 13.6888C13.8124 14.1888 13.5624 14.4388 13.2085 14.6882C12.8546 14.9375 12.5351 15.0889 11.8961 15.3916L10.7063 15.9552C10.3229 16.1368 10.1312 16.2276 9.91883 16.3772C9.70653 16.5268 9.55653 16.6768 9.25653 16.9768L9.04443 17.189C8.61112 17.6223 8.39443 17.839 8.08213 17.9052C7.76983 17.9715 7.48382 17.8615 6.91182 17.6415L4.74233 16.8071C4.17032 16.5871 3.88443 16.4771 3.84133 16.2742C3.79823 16.0712 4.01493 15.8545 4.44823 15.4212L6.07463 13.7949C6.37463 13.4949 6.52463 13.3449 6.67423 13.1326C6.82383 12.9202 6.91462 12.7285 7.09623 12.3451L7.65983 11.1553C7.96252 10.5163 8.11392 10.1968 8.36322 9.84287C8.61263 9.48897 8.86263 9.23897 9.36263 8.73907L11.9435 6.15807Z",
            )
            svgStroke("M9.57422 8.52686L14.524 13.4767")
            svgStroke("M6.67773 13.1262L9.92423 16.3727")
            svgStroke("M10.1641 20.8414H20.1641")
        }
    }

    /** 荧光笔宽头与底线着色层 */
    val HighlighterTipAccent: ImageVector by lazy {
        icon("HighlighterTipAccent") {
            svgStroke(
                "M6.67773 13.1262L6.07463 13.7949L4.44823 15.4212C4.01493 15.8545 3.79823 16.0712 3.84133 16.2742C3.88443 16.4771 4.17032 16.5871 4.74233 16.8071L6.91182 17.6415C7.48382 17.8615 7.76983 17.9715 8.08213 17.9052C8.39443 17.839 8.61112 17.6223 9.04443 17.189L9.25653 16.9768L9.92423 16.3727Z",
                width = 1.85f,
            )
            svgStroke("M10.1641 20.8414H20.1641", width = 2.0f)
        }
    }

    /** 橡皮（Lucide eraser）：圆角斜切橡皮块 + 护套分界 + 底部擦除基线 */
    val Eraser: ImageVector by lazy {
        icon("Eraser") {
            svgStroke(
                "M21 21H8a2 2 0 0 1-1.42-.587l-3.994-3.999a2 2 0 0 1 0-2.828l10-10a2 2 0 0 1 2.829 0l5.999 6a2 2 0 0 1 0 2.828L12.834 21m-7.752-9.91l8.828 8.828",
            )
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

    /** 激光笔：斜向笔杆 + 锥形发射头 + 发光焦点 + 五向射线 */
    val LaserPointer: ImageVector by lazy {
        icon("LaserPointer") {
            stroke {
                moveTo(3.9f, 17.9f)
                lineTo(11.1f, 10.7f)
                lineTo(14.2f, 9.8f)
                lineTo(13.3f, 12.9f)
                lineTo(6.1f, 20.1f)
                quadTo(4.8f, 21f, 3.8f, 20f)
                quadTo(2.9f, 19f, 3.9f, 17.9f)
                close()
                moveTo(17.1f, 2.8f); lineTo(17.1f, 4.2f)
                moveTo(21f, 6.7f); lineTo(19.6f, 6.7f)
                moveTo(19.9f, 3.9f); lineTo(18.9f, 4.9f)
                moveTo(14.3f, 3.9f); lineTo(15.3f, 4.9f)
                moveTo(19.9f, 9.5f); lineTo(18.9f, 8.5f)
            }
            dot(17.1f, 6.7f, 1.55f)
        }
    }

    /** 激光笔焦点着色层 */
    val LaserTipAccent: ImageVector by lazy {
        icon("LaserTipAccent") {
            dot(17.1f, 6.7f, 1.85f)
            stroke(width = 1.8f) {
                moveTo(17.1f, 2.8f); lineTo(17.1f, 4.2f)
                moveTo(21f, 6.7f); lineTo(19.6f, 6.7f)
                moveTo(19.9f, 3.9f); lineTo(18.9f, 4.9f)
                moveTo(14.3f, 3.9f); lineTo(15.3f, 4.9f)
                moveTo(19.9f, 9.5f); lineTo(18.9f, 8.5f)
            }
        }
    }

    /** 阅读/抓手（Lucide hand）：自然张开手掌 */
    val HandPan: ImageVector by lazy {
        icon("HandPan") {
            svgStroke("M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2m0 4V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2m0 4.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8")
            svgStroke("M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15")
        }
    }

    /** 套索（Lucide lasso）：经典套索圈 + 绳结与引线 */
    val Lasso: ImageVector by lazy {
        icon("Lasso") {
            svgStroke("M3.704 14.467a10 8 0 1 1 3.115 2.375M7 22a5 5 0 0 1-2-3.994")
            stroke { circle(5f, 16f, 2f) }
        }
    }

    /** 矩形套索：四段虚框角 */
    val LassoRect: ImageVector by lazy {
        icon("LassoRect") {
            stroke {
                moveTo(4f, 8f); lineTo(4f, 5.5f); quadTo(4f, 4f, 5.5f, 4f); lineTo(8f, 4f)
                moveTo(16f, 4f); lineTo(18.5f, 4f); quadTo(20f, 4f, 20f, 5.5f); lineTo(20f, 8f)
                moveTo(20f, 16f); lineTo(20f, 18.5f); quadTo(20f, 20f, 18.5f, 20f); lineTo(16f, 20f)
                moveTo(8f, 20f); lineTo(5.5f, 20f); quadTo(4f, 20f, 4f, 18.5f); lineTo(4f, 16f)
                moveTo(11f, 4f); lineTo(13f, 4f)
                moveTo(11f, 20f); lineTo(13f, 20f)
                moveTo(4f, 11f); lineTo(4f, 13f)
                moveTo(20f, 11f); lineTo(20f, 13f)
            }
        }
    }

    /** 图案笔（Lucide wand-sparkles）：魔棒 + 闪烁图案星点 */
    val PatternBrush: ImageVector by lazy {
        icon("PatternBrush") {
            svgStroke("m21.64 3.64l-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72M14 7l3 3M5 6v4m14 4v4M10 2v2M7 8H3m18 8h-4M11 3H9")
        }
    }

    /** 图案笔星芒着色层 */
    val PatternBrushAccent: ImageVector by lazy {
        icon("PatternBrushAccent") {
            svgStroke("M5 6v4m14 4v4M10 2v2M7 8H3m18 8h-4M11 3H9", width = 1.8f)
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
                moveTo(9.5f, 8.5f); lineTo(15.5f, 14.5f)
                moveTo(6.5f, 11.5f); lineTo(12.5f, 17.5f)
            }
        }
    }

    /** 摘取工具：框选卡片摘出示意 */
    val SnipExtract: ImageVector by lazy {
        icon("SnipExtract") {
            svgStroke("M3 7V5a2 2 0 0 1 2-2h2m10 0h2a2 2 0 0 1 2 2v2m0 10v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2")
            stroke { roundRect(8f, 8f, 16f, 16f, 1.5f) }
        }
    }

    /** 框选提问：四角取景框 + 居中四芒星 */
    val SnipSpark: ImageVector by lazy {
        icon("SnipSpark") {
            svgStroke("M3 7V5a2 2 0 0 1 2-2h2m10 0h2a2 2 0 0 1 2 2v2m0 10v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2")
            stroke { sparkle(12f, 12f, 4.2f) }
        }
    }

    /** 文本框：带边框的 T 字 */
    val TextBox: ImageVector by lazy {
        icon("TextBox") {
            stroke {
                roundRect(3.5f, 3.5f, 20.5f, 20.5f, 2.5f)
                moveTo(8f, 8.5f); lineTo(16f, 8.5f)
                moveTo(12f, 8.5f); lineTo(12f, 16.5f)
                moveTo(10f, 16.5f); lineTo(14f, 16.5f)
            }
        }
    }

    /** 纯文字 T（Lucide type） */
    val TextCursor: ImageVector by lazy {
        icon("TextCursor") {
            svgStroke("M12 4v16M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2M9 20h6")
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

    /** 贴纸（Lucide sticker 结构，极简折角方形） */
    val Sticker: ImageVector by lazy {
        icon("Sticker") {
            svgStroke("M21 9a2.4 2.4 0 0 0-.706-1.706l-3.588-3.588A2.4 2.4 0 0 0 15 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2z")
            svgStroke("M15 3v5a1 1 0 0 0 1 1h5")
        }
    }

    /** 媒体播放（套索媒体筛选） */
    val MediaPlay: ImageVector by lazy {
        icon("MediaPlay") {
            stroke {
                roundRect(3.5f, 5f, 20.5f, 19f, 2.5f)
                moveTo(10f, 9f); lineTo(15.5f, 12f); lineTo(10f, 15f); close()
            }
        }
    }

    // ==================== 图形子工具 ====================

    val ShapeLine: ImageVector by lazy {
        icon("ShapeLine") {
            stroke {
                moveTo(6.2f, 17.8f); lineTo(17.8f, 6.2f)
                circle(5f, 19f, 1.4f)
                circle(19f, 5f, 1.4f)
            }
        }
    }

    val ShapeDashedLine: ImageVector by lazy {
        icon("ShapeDashedLine") {
            stroke {
                moveTo(4.5f, 19.5f); lineTo(7.8f, 16.2f)
                moveTo(10.3f, 13.7f); lineTo(13.7f, 10.3f)
                moveTo(16.2f, 7.8f); lineTo(19.5f, 4.5f)
            }
        }
    }

    val ShapeArrow: ImageVector by lazy {
        icon("ShapeArrow") {
            stroke {
                moveTo(5f, 19f); lineTo(19f, 5f)
                moveTo(12f, 5f); lineTo(19f, 5f); lineTo(19f, 12f)
            }
        }
    }

    val ShapeRect: ImageVector by lazy {
        icon("ShapeRect") {
            stroke { roundRect(4.25f, 5.5f, 19.75f, 18.5f, 2f) }
        }
    }

    val ShapeOval: ImageVector by lazy {
        icon("ShapeOval") {
            stroke { circle(12f, 12f, 7.5f) }
        }
    }

    val ShapeTriangle: ImageVector by lazy {
        icon("ShapeTriangle") {
            stroke {
                moveTo(12f, 4.5f); lineTo(20f, 18.5f); lineTo(4f, 18.5f); close()
            }
        }
    }

    val ShapeStar: ImageVector by lazy {
        icon("ShapeStar") {
            svgStroke("M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.12 2.12 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.12 2.12 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.12 2.12 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.12 2.12 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.12 2.12 0 0 0 1.597-1.16z")
        }
    }

    val ShapeHexagon: ImageVector by lazy {
        icon("ShapeHexagon") {
            stroke {
                moveTo(12f, 3.5f); lineTo(19.4f, 7.75f); lineTo(19.4f, 16.25f)
                lineTo(12f, 20.5f); lineTo(4.6f, 16.25f); lineTo(4.6f, 7.75f); close()
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
            stroke(width = 2.0f) {
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

    // ==================== 基础导航与控制（Lucide） ====================

    val ArrowLeft: ImageVector by lazy {
        icon("ArrowLeft") {
            svgStroke("m12 19l-7-7l7-7m7 7H5")
        }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") {
            svgStroke("m15 18l-6-6l6-6")
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            svgStroke("m9 18l6-6l-6-6")
        }
    }

    val ChevronUp: ImageVector by lazy {
        icon("ChevronUp") {
            svgStroke("m18 15l-6-6l-6 6")
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            svgStroke("m6 9l6 6l6-6")
        }
    }

    val UnfoldMore: ImageVector by lazy {
        icon("UnfoldMore") {
            svgStroke("m7 15l5 5l5-5M7 9l5-5l5 5")
        }
    }

    val Add: ImageVector by lazy {
        icon("Add") {
            svgStroke("M5 12h14m-7-7v14")
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            svgStroke("M18 6L6 18M6 6l12 12")
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            svgStroke("M20 6L9 17l-5-5")
        }
    }

    val CheckCircle: ImageVector by lazy {
        icon("CheckCircle") {
            stroke { circle(12f, 12f, 10f) }
            svgStroke("m16 9l-5.5 5.5L8 12")
        }
    }

    val ErrorCircle: ImageVector by lazy {
        icon("ErrorCircle") {
            stroke { circle(12f, 12f, 10f) }
            svgStroke("M12 8v4m0 4h.01")
        }
    }

    val WarningTriangle: ImageVector by lazy {
        icon("WarningTriangle") {
            svgStroke("m21.73 18l-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3M12 9v4m0 4h.01")
        }
    }

    val InfoCircle: ImageVector by lazy {
        icon("InfoCircle") {
            stroke { circle(12f, 12f, 10f) }
            svgStroke("M12 16v-4m0-4h.01")
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            svgStroke("m21 21l-4.34-4.34")
            stroke { circle(11f, 11f, 8f) }
        }
    }

    val SearchOff: ImageVector by lazy {
        icon("SearchOff") {
            svgStroke("m13.5 8.5l-5 5m0-5l5 5")
            stroke { circle(11f, 11f, 8f) }
            svgStroke("m21 21l-4.3-4.3")
        }
    }

    val MoreDots: ImageVector by lazy {
        icon("MoreDots") {
            dot(5f, 12f, 1.3f)
            dot(12f, 12f, 1.3f)
            dot(19f, 12f, 1.3f)
        }
    }

    val GripDots: ImageVector by lazy {
        icon("GripDots") {
            dot(9f, 6.5f, 1.2f); dot(15f, 6.5f, 1.2f)
            dot(9f, 12f, 1.2f); dot(15f, 12f, 1.2f)
            dot(9f, 17.5f, 1.2f); dot(15f, 17.5f, 1.2f)
        }
    }

    /** 展开笔匣：笔匣胶囊与内部笔锋 */
    val PenDock: ImageVector by lazy {
        icon("PenDock") {
            stroke {
                roundRect(3f, 7f, 21f, 17f, 5f)
                moveTo(8f, 12f); lineTo(12f, 12f)
                moveTo(15f, 10.5f); lineTo(17f, 12f); lineTo(15f, 13.5f)
            }
        }
    }

    /** 侧栏（Lucide panel-left） */
    val Sidebar: ImageVector by lazy {
        icon("Sidebar") {
            stroke { roundRect(3f, 3f, 21f, 21f, 2f) }
            svgStroke("M9 3v18")
        }
    }

    /** 双开分屏（Lucide columns-2） */
    val SplitView: ImageVector by lazy {
        icon("SplitView") {
            stroke { roundRect(3f, 3f, 21f, 21f, 2f) }
            svgStroke("M12 3v18")
        }
    }

    /** 专注模式（Lucide scan 四角扩展框） */
    val FocusExpand: ImageVector by lazy {
        icon("FocusExpand") {
            svgStroke("M3 7V5a2 2 0 0 1 2-2h2m10 0h2a2 2 0 0 1 2 2v2m0 10v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2")
        }
    }

    val Undo: ImageVector by lazy {
        icon("Undo") {
            svgStroke("M9 14L4 9l5-5")
            svgStroke("M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11")
        }
    }

    val Redo: ImageVector by lazy {
        icon("Redo") {
            svgStroke("m15 14l5-5l-5-5")
            svgStroke("M20 9H9.5A5.5 5.5 0 0 0 4 14.5A5.5 5.5 0 0 0 9.5 20H13")
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            svgStroke("M3 12a9 9 0 0 1 9-9a9.75 9.75 0 0 1 6.74 2.74L21 8")
            svgStroke("M21 3v5h-5m5 4a9 9 0 0 1-9 9a9.75 9.75 0 0 1-6.74-2.74L3 16")
            svgStroke("M8 16H3v5")
        }
    }

    val Reset: ImageVector by lazy {
        icon("Reset") {
            svgStroke("M3 12a9 9 0 1 0 9-9a9.75 9.75 0 0 0-6.74 2.74L3 8")
            svgStroke("M3 3v5h5")
        }
    }

    val Sliders: ImageVector by lazy {
        icon("Sliders") {
            svgStroke("M10 5H3m9 14H3M14 3v4m2 10v4m5-9h-9m9 7h-5m5-14h-7m-6 5v4m0-2H3")
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            svgStroke("M9.671 4.136a2.34 2.34 0 0 1 4.659 0a2.34 2.34 0 0 0 3.319 1.915a2.34 2.34 0 0 1 2.33 4.033a2.34 2.34 0 0 0 0 3.831a2.34 2.34 0 0 1-2.33 4.033a2.34 2.34 0 0 0-3.319 1.915a2.34 2.34 0 0 1-4.659 0a2.34 2.34 0 0 0-3.32-1.915a2.34 2.34 0 0 1-2.33-4.033a2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915")
            stroke { circle(12f, 12f, 3f) }
        }
    }

    /** 笔诊断（Lucide activity） */
    val PulseDiag: ImageVector by lazy {
        icon("PulseDiag") {
            svgStroke("M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2")
        }
    }

    /** 清空画布（Lucide brush-cleaning） */
    val ClearSweep: ImageVector by lazy {
        icon("ClearSweep") {
            svgStroke("m16 22l-1-4m4-4a1 1 0 0 0 1-1v-1a2 2 0 0 0-2-2h-3a1 1 0 0 1-1-1V4a2 2 0 0 0-4 0v5a1 1 0 0 1-1 1H6a2 2 0 0 0-2 2v1a1 1 0 0 0 1 1")
            svgStroke("M19 14H5l-1.973 6.767A1 1 0 0 0 4 22h16a1 1 0 0 0 .973-1.233zM8 22l1-4")
        }
    }

    // ==================== 书架、笔记本、文件夹与卡片（Lucide） ====================

    val Book: ImageVector by lazy {
        icon("Book") {
            svgStroke("M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H19a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H6.5a1 1 0 0 1 0-5H20")
        }
    }

    val Star: ImageVector by lazy {
        icon("Star") {
            svgStroke("M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.12 2.12 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.12 2.12 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.12 2.12 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.12 2.12 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.12 2.12 0 0 0 1.597-1.16z")
        }
    }

    val Cards: ImageVector by lazy {
        icon("Cards") {
            stroke {
                roundRect(3.5f, 5.5f, 16.5f, 19.5f, 2.2f)
                moveTo(7.5f, 3.5f); lineTo(18f, 3.5f)
                quadTo(20.5f, 3.5f, 20.5f, 6f); lineTo(20.5f, 16.5f)
                moveTo(7f, 10.5f); lineTo(13f, 10.5f)
                moveTo(7f, 14f); lineTo(11f, 14f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            svgStroke("M10 11v6m4-6v6m5-11v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
        }
    }

    val RestoreTrash: ImageVector by lazy {
        icon("RestoreTrash") {
            stroke { roundRect(2f, 3f, 22f, 8f, 1f) }
            svgStroke("M4 8v11a2 2 0 0 0 2 2h2M20 8v11a2 2 0 0 1-2 2h-2m-7-6l3-3l3 3m-3-3v9")
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            svgStroke("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")
        }
    }

    val FolderOpen: ImageVector by lazy {
        icon("FolderOpen") {
            svgStroke("m6 14l1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2")
        }
    }

    val FolderOff: ImageVector by lazy {
        icon("FolderOff") {
            svgStroke("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2ZM9.5 10.5l5 5m0-5l-5 5")
        }
    }

    val GridView: ImageVector by lazy {
        icon("GridView") {
            stroke {
                roundRect(3f, 3f, 10f, 10f, 1.2f)
                roundRect(14f, 3f, 21f, 10f, 1.2f)
                roundRect(14f, 14f, 21f, 21f, 1.2f)
                roundRect(3f, 14f, 10f, 21f, 1.2f)
            }
        }
    }

    val ListBullet: ImageVector by lazy {
        icon("ListBullet") {
            svgStroke("M3 5h.01M3 12h.01M3 19h.01M8 5h13M8 12h13M8 19h13")
        }
    }

    val Sort: ImageVector by lazy {
        icon("Sort") {
            svgStroke("m21 16l-4 4l-4-4m4 4V4M3 8l4-4l4 4M7 4v16")
        }
    }

    val ImportDoc: ImageVector by lazy {
        icon("ImportDoc") {
            svgStroke("M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z")
            svgStroke("M14 2v5a1 1 0 0 0 1 1h5m-8 4v6m3-3l-3-3l-3 3")
        }
    }

    val Export: ImageVector by lazy {
        icon("Export") {
            svgStroke("M12 3v12m5-7l-5-5l-5 5m14 7v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4")
        }
    }

    val PageAdd: ImageVector by lazy {
        icon("PageAdd") {
            svgStroke("M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z")
            svgStroke("M14 2v5a1 1 0 0 0 1 1h5M9 15h6m-3 3v-6")
        }
    }

    val Article: ImageVector by lazy {
        icon("Article") {
            svgStroke("M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z")
            svgStroke("M14 2v5a1 1 0 0 0 1 1h5M10 9H8m8 4H8m8 4H8")
        }
    }

    val Copy: ImageVector by lazy {
        icon("Copy") {
            stroke { roundRect(8f, 8f, 22f, 22f, 2f) }
            svgStroke("M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
        }
    }

    val Edit: ImageVector by lazy {
        icon("Edit") {
            svgStroke("M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7")
            svgStroke("M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z")
        }
    }

    val Pin: ImageVector by lazy {
        icon("Pin") {
            svgStroke("M12 17v5M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4a1 1 0 0 1 1 1z")
        }
    }

    val Bookmark: ImageVector by lazy {
        icon("Bookmark") {
            svgStroke("M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z")
        }
    }

    val NoteInsert: ImageVector by lazy {
        icon("NoteInsert") {
            svgStroke("M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z")
            svgStroke("M14 2v5a1 1 0 0 0 1 1h5M9 15h6m-3 3v-6")
        }
    }

    val ExpandOpen: ImageVector by lazy {
        icon("ExpandOpen") {
            svgStroke("M15 3h6v6m0-6l-7 7M3 21l7-7m-1 7H3v-6")
        }
    }

    // ==================== 媒体、相机、音频与画布选中（Lucide） ====================

    val Image: ImageVector by lazy {
        icon("Image") {
            stroke {
                roundRect(3f, 3f, 21f, 21f, 2f)
                circle(9f, 9f, 2f)
            }
            svgStroke("m21 15l-3.086-3.086a2 2 0 0 0-2.828 0L6 21")
        }
    }

    val PhotoLibrary: ImageVector by lazy {
        icon("PhotoLibrary") {
            svgStroke("m22 11l-1.296-1.296a2.4 2.4 0 0 0-3.408 0L11 16")
            svgStroke("M4 8a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2")
            dot(13f, 7f, 1.1f)
            stroke { roundRect(8f, 2f, 22f, 16f, 2f) }
        }
    }

    val BrokenImage: ImageVector by lazy {
        icon("BrokenImage") {
            svgStroke("m2 2l20 20M10.41 10.41a2 2 0 1 1-2.83-2.83m5.92 5.92L6 21m12-9l3 3")
            svgStroke("M3.59 3.59A2 2 0 0 0 3 5v14a2 2 0 0 0 2 2h14c.55 0 1.052-.22 1.41-.59M21 15V5a2 2 0 0 0-2-2H9")
        }
    }

    val Camera: ImageVector by lazy {
        icon("Camera") {
            svgStroke("M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z")
            stroke { circle(12f, 13f, 3f) }
        }
    }

    val Video: ImageVector by lazy {
        icon("Video") {
            svgStroke("m16 13l5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5")
            stroke { roundRect(2f, 6f, 16f, 18f, 2f) }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") {
            svgStroke("M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z")
        }
    }

    val Mic: ImageVector by lazy {
        icon("Mic") {
            svgStroke("M12 19v3m7-12v2a7 7 0 0 1-14 0v-2")
            stroke { roundRect(9f, 2f, 15f, 15f, 3f) }
        }
    }

    val MagnetSnap: ImageVector by lazy {
        icon("MagnetSnap") {
            svgStroke("m12 15l4 4M2.352 10.648a1.205 1.205 0 0 0 0 1.704l2.296 2.296a1.205 1.205 0 0 0 1.704 0l6.029-6.029a1 1 0 1 1 3 3l-6.029 6.029a1.205 1.205 0 0 0 0 1.704l2.296 2.296a1.205 1.205 0 0 0 1.704 0l6.365-6.367A1 1 0 0 0 8.716 4.282zM5 8l4 4")
        }
    }

    // ==================== AI 抽屉、会话、图片编辑与预览（Lucide） ====================

    val FunctionGraph: ImageVector by lazy {
        icon("FunctionGraph") {
            svgStroke("M3 3v18h18")
            svgStroke("M6 15c2.8 0 4.2-9 7-9s3.4 6 5.5 6")
        }
    }

    val Sparkle: ImageVector by lazy {
        icon("Sparkle") {
            svgStroke("M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594zM20 2v4m2-2h-4")
            stroke { circle(4f, 20f, 1.5f) }
        }
    }

    val History: ImageVector by lazy {
        icon("History") {
            svgStroke("M3 12a9 9 0 1 0 9-9a9.75 9.75 0 0 0-6.74 2.74L3 8")
            svgStroke("M3 3v5h5m4-1v5l4 2")
        }
    }

    val Clock: ImageVector by lazy {
        icon("Clock") {
            stroke { circle(12f, 12f, 10f) }
            svgStroke("M12 6v6l4 2")
        }
    }

    val AttachClip: ImageVector by lazy {
        icon("AttachClip") {
            svgStroke("m16 6l-8.414 8.586a2 2 0 0 0 2.829 2.829l8.414-8.586a4 4 0 1 0-5.657-5.657l-8.379 8.551a6 6 0 1 0 8.485 8.485l8.379-8.551")
        }
    }

    val Send: ImageVector by lazy {
        icon("Send") {
            svgStroke("M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11zm7.318-19.539l-10.94 10.939")
        }
    }

    val StopSquare: ImageVector by lazy {
        icon("StopSquare") {
            stroke { roundRect(5f, 5f, 19f, 19f, 2f) }
        }
    }

    val RotateLeft: ImageVector by lazy {
        icon("RotateLeft") {
            svgStroke("M3 12a9 9 0 1 0 9-9a9.75 9.75 0 0 0-6.74 2.74L3 8")
            svgStroke("M3 3v5h5")
        }
    }

    val RotateRight: ImageVector by lazy {
        icon("RotateRight") {
            svgStroke("M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8")
            svgStroke("M21 3v5h-5")
        }
    }

    val FlipHorizontal: ImageVector by lazy {
        icon("FlipHorizontal") {
            svgStroke("m3 7l5 5l-5 5zm18 0l-5 5l5 5zm-9 13v2m0-8v2m0-8v2m0-8v2")
        }
    }

    val FlipVertical: ImageVector by lazy {
        icon("FlipVertical") {
            svgStroke("m17 3l-5 5l-5-5zm0 18l-5-5l-5 5zM4 12H2m8 0H8m8 0h-2m8 0h-2")
        }
    }

    val Brightness: ImageVector by lazy {
        icon("Brightness") {
            stroke { circle(12f, 12f, 4f) }
            svgStroke("M12 2v2m0 16v2M4.93 4.93l1.41 1.41m11.32 11.32l1.41 1.41M2 12h2m16 0h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41")
        }
    }

    val DocumentEnhance: ImageVector by lazy {
        icon("DocumentEnhance") {
            svgStroke("M3 7V5a2 2 0 0 1 2-2h2m10 0h2a2 2 0 0 1 2 2v2m0 10v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2m4-9h8m-8 4h10M7 16h6")
        }
    }

    val CheckBoxChecked: ImageVector by lazy {
        icon("CheckBoxChecked") {
            stroke { roundRect(3.5f, 3.5f, 20.5f, 20.5f, 2.5f) }
            svgStroke("m8 12l3 3l5-5.5")
        }
    }

    val CheckBoxUnchecked: ImageVector by lazy {
        icon("CheckBoxUnchecked") {
            stroke { roundRect(3.5f, 3.5f, 20.5f, 20.5f, 2.5f) }
        }
    }

    // ==================== 设置侧栏与导出格式（Lucide / Tabler） ====================

    val Palette: ImageVector by lazy {
        icon("Palette") {
            svgStroke("M12 22a1 1 0 0 1 0-20a10 9 0 0 1 10 9a5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z")
            dot(13.5f, 6.5f, 1.0f)
            dot(17.5f, 10.5f, 1.0f)
            dot(6.5f, 12.5f, 1.0f)
            dot(8.5f, 7.5f, 1.0f)
        }
    }

    /** 触控笔（Lucide pen-line）：书写笔身与纸面基线 */
    val Stylus: ImageVector by lazy {
        icon("Stylus") {
            svgStroke("M13 21h8m.174-14.188a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z")
        }
    }

    val Keyboard: ImageVector by lazy {
        icon("Keyboard") {
            svgStroke("M10 8h.01M12 12h.01M14 8h.01M16 12h.01M18 8h.01M6 8h.01M7 16h10m-9-4h.01")
            stroke { roundRect(2f, 4f, 22f, 20f, 2f) }
        }
    }

    /** 设置侧栏「技能与工具」图标（Lucide blocks） */
    val Blocks: ImageVector by lazy {
        icon("Blocks") {
            svgStroke("M10 22V7a1 1 0 0 0-1-1H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5a1 1 0 0 0-1-1H2")
            stroke { roundRect(14f, 2f, 22f, 10f, 1f) }
        }
    }

    /** 存储与更新（Lucide hard-drive） */
    val StorageUpdate: ImageVector by lazy {
        icon("StorageUpdate") {
            svgStroke("M10 16h.01m-7.798-4.423a2 2 0 0 0-.212.896V18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5.527a2 2 0 0 0-.212-.896L18.55 5.11A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11zm19.734.436H2.054M6 16h.01")
        }
    }

    /** PDF 文件（Tabler file-type-pdf） */
    val FilePdf: ImageVector by lazy {
        icon("FilePdf") {
            svgStroke("M14 3v4a1 1 0 0 0 1 1h4")
            svgStroke("M5 12V5a2 2 0 0 1 2-2h7l5 5v4M5 18h1.5a1.5 1.5 0 0 0 0-3H5v6m12-3h2m1-3h-3v6m-6-6v6h1a2 2 0 0 0 2-2v-2a2 2 0 0 0-2-2z", width = 1.6f)
        }
    }

    /** ZIP 归档（Lucide file-archive） */
    val FileZip: ImageVector by lazy {
        icon("FileZip") {
            svgStroke("M13.659 22H18a2 2 0 0 0 2-2V8a2.4 2.4 0 0 0-.706-1.706l-3.588-3.588A2.4 2.4 0 0 0 14 2H6a2 2 0 0 0-2 2v11.5")
            svgStroke("M14 2v5a1 1 0 0 0 1 1h5M8 12v-1m0 7v-2m0-9V6")
            stroke { circle(8f, 20f, 2f) }
        }
    }
}
