package com.milknote.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 物理动效系统(Apple WWDC「Designing Fluid Interfaces」+ Emil Kowalski):
 * - 点击弹簧零过冲(临界阻尼 1.0),只有手势释放继承速度时才允许微回弹;
 * - 时长不对称:退出快于进入,按压瞬时,浮层/抽屉按层级给足时间。
 *
 * 语义时长:press 100 / color 150 / enter 200 / exit 130 /
 * drawerEnter 260 / drawerExit 190 / reduced 60。
 */
object MilkMotion {
    const val press = 100
    const val color = 150
    const val enter = 200
    const val exit = 130
    const val drawerEnter = 260
    const val drawerExit = 190
    const val reduced = 60

    /** Emil 强 ease-out:UI 进入/退出/按压的唯一默认曲线 */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** 屏内平滑移动/形变 */
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** Apple / Vaul iOS 抽屉与侧栏曲线 */
    val EaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** 兼容别名:标准缓动 = 强 ease-out */
    val StandardEasing = EaseOut

    /** 兼容别名:入场减速 = 强 ease-out */
    val DecelEasing = EaseOut

    /** 颜色/选中态过渡 spec(150ms 强 ease-out) */
    fun <T> colorSpec(): FiniteAnimationSpec<T> = tween(color, easing = EaseOut)

    /**
     * 点击弹簧:临界阻尼 1.0、零过冲。
     * 分段滑块、开关滑移、点击展开等所有「无手势施力」的控件必须用它。
     */
    fun <T> uiSpring(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 480f)

    /**
     * 手势动量弹簧:仅用于拖放/甩动释放、需要继承释放速度的场合。
     * 物理上「扔出去」才有回弹,点击不该有。
     */
    fun <T> momentumSpring(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = 420f)

    /** 兼容旧调用点:快速弹簧改为零过冲(临界阻尼) */
    fun <T> snappySpring(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 540f)

    /** 兼容旧调用点:柔和弹簧改为零过冲(临界阻尼) */
    fun <T> smoothSpring(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 420f)

    /**
     * Apple 动量投影:按指数衰减预测甩动停点(不是 v²/2a)。
     * @return 从松手点继续滑行的像素距离。
     */
    fun project(velocityPxPerSec: Float, decelerationRate: Float = 0.998f): Float =
        (velocityPxPerSec / 1000f) * decelerationRate / (1f - decelerationRate)

    /**
     * 软边界橡皮筋:越界越多、位移越少,永不硬停。
     * dimensionPx <= 0 时返回 0。
     */
    fun rubberband(overshootPx: Float, dimensionPx: Float, constant: Float = 0.55f): Float {
        if (dimensionPx <= 0f) return 0f
        return (overshootPx * dimensionPx * constant) / (dimensionPx + constant * abs(overshootPx))
    }

    /** 居中弹窗入场:淡化 + 0.96→1 微缩放;减弱动效仅短淡化 */
    fun panelEnter(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(MilkMotion.reduced))
        else fadeIn(tween(enter, easing = EaseOut)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(enter, easing = EaseOut))

    fun panelExit(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(MilkMotion.reduced))
        else fadeOut(tween(exit, easing = EaseOut))

    /** 页面级切换:仅交叉淡化(纸张不做整体位移/缩放,DESIGN 审查 §3) */
    fun pageEnter(reduced: Boolean): EnterTransition =
        fadeIn(tween(if (reduced) MilkMotion.reduced else enter, easing = EaseOut))

    fun pageExit(reduced: Boolean): ExitTransition =
        fadeOut(tween(if (reduced) MilkMotion.reduced else exit, easing = EaseOut))
}

/**
 * 居中弹窗入场(Dialog 内容每次打开都是新组合,故首次组合即播放):
 * 透明度 0→1 + 0.96→1 微缩放(TransformOrigin.Center);减弱动效时直接呈现。
 */
@Composable
fun Modifier.milkPanelEnter(): Modifier {
    val reduced = LocalMilkMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(MilkMotion.enter, easing = MilkMotion.EaseOut))
    }
    return this.graphicsLayer {
        alpha = progress.value
        transformOrigin = TransformOrigin.Center
        val s = 0.96f + 0.04f * progress.value
        scaleX = s
        scaleY = s
    }
}

/**
 * 锚定弹出层(Popover)入场:从触发角 [origin] 以 0.94→1 长出 + 淡化 + 上移 4dp→0,
 * 160ms 强 ease-out;减弱动效时直接呈现。
 */
@Composable
fun Modifier.milkPopoverEnter(origin: TransformOrigin = TransformOrigin(1f, 0f)): Modifier {
    val reduced = LocalMilkMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(160, easing = MilkMotion.EaseOut))
    }
    return this.graphicsLayer {
        alpha = progress.value
        transformOrigin = origin
        val s = 0.94f + 0.06f * progress.value
        scaleX = s
        scaleY = s
        translationY = -4.dp.toPx() * (1f - progress.value)
    }
}
