package com.milknote.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.LocalMilkMotion
import com.milknote.app.ui.theme.MilkMotion
import com.milknote.app.ui.theme.MilkText
import com.milknote.app.ui.theme.PaperBorder
import com.milknote.app.ui.theme.PaperInk
import com.milknote.app.ui.theme.PaperMeta
import com.milknote.app.ui.theme.RedPen
import com.milknote.notes.model.PAPER_KINDS

/** 实体书本封面形状:左侧书脊窄圆角、右侧开本圆角 */
val BookCoverShape = RoundedCornerShape(
    topStart = 4.dp,
    bottomStart = 4.dp,
    topEnd = 12.dp,
    bottomEnd = 12.dp,
)

/**
 * 共享书本封面:左侧书脊 + 2dp 压痕,真实缩略图或诚实空封面(纸色 + 纸纹 + 衬线标题),
 * 可选红丝带星标、PDF 折角与标识,以及右下角覆盖层 slot(书架「…」操作钮)。
 *
 * 纸纹标签固定在左下角,避免与右下角覆盖层重叠。
 */
@Composable
fun MilkBookCover(
    name: String,
    cover: Color,
    paperColor: Color,
    paper: String,
    modifier: Modifier = Modifier,
    thumbnail: ImageBitmap? = null,
    pinned: Boolean = false,
    isPdf: Boolean = false,
    shadowElevation: Dp = 6.dp,
    pressed: Boolean = false,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val dark = LocalMilkDark.current
    val reduced = LocalMilkMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.97f else 1f,
        animationSpec = tween(MilkMotion.press, easing = MilkMotion.EaseOut),
        label = "milkBookCoverPress",
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .softShadow(shadowElevation, BookCoverShape)
            .clip(BookCoverShape)
            .background(paperColor)
            .border(1.dp, if (dark) Color.White.copy(alpha = 0.10f) else PaperBorder, BookCoverShape),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopStart,
                modifier = Modifier.fillMaxSize().padding(start = 11.dp),
            )
        } else {
            NotebookPaperPattern(
                kind = paper,
                modifier = Modifier.fillMaxSize().padding(start = 11.dp),
            )
            Text(
                text = name,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PaperInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 20.dp),
            )
            Text(
                text = PAPER_KINDS.firstOrNull { it.first == paper }?.second.orEmpty(),
                style = MilkText.monoMicro,
                color = PaperMeta,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 10.dp),
            )
        }

        // 书脊 + 2dp 压痕暗线
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(11.dp)
                .background(cover)
                .drawBehind {
                    val crease = 2.dp.toPx()
                    drawRect(
                        color = Color.Black.copy(alpha = 0.14f),
                        topLeft = Offset(size.width - crease, 0f),
                        size = Size(crease, size.height),
                    )
                },
        )

        // 星标置顶:朱砂红丝带书签
        if (pinned) {
            Canvas(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 22.dp)
                    .width(12.dp)
                    .height(22.dp),
            ) {
                val ribbon = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width * 0.5f, size.height * 0.74f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(ribbon, RedPen)
            }
        }

        // PDF:右上折角 + 右下淡印标识
        if (isPdf) {
            Canvas(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp),
            ) {
                val fold = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(fold, Color(0xFFE5E7EB))
                val corner = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(corner, Color(0xFFD1D5DB))
            }
            Text(
                "PDF",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = RedPen.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 42.dp, bottom = 10.dp),
            )
        }

        if (overlay != null) {
            Box(Modifier.align(Alignment.BottomEnd)) {
                overlay()
            }
        }
    }
}
