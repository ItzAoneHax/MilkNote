package com.milknote.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 动效语义时长(视觉升级 2026-11):同一语义同一答案,不再各处散落魔法数。
 * - press 80ms 按压 / color 160ms 颜色与选中态 / enter 180ms 浮层与页面进入 / exit 140ms 退出
 * - reduced 60ms:系统「减弱动效」时空间动画退化为短淡化
 */
object MilkMotion {
    const val press = 80
    const val color = 160
    const val enter = 180
    const val exit = 140
    const val reduced = 60

    /** 浮层入场:淡化 + 0.985→1 微缩放;减弱动效仅短淡化(reduced = LocalMilkMotion.current) */
    fun panelEnter(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(MilkMotion.reduced))
        else fadeIn(tween(enter)) + scaleIn(initialScale = 0.985f, animationSpec = tween(enter))

    fun panelExit(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(MilkMotion.reduced)) else fadeOut(tween(exit))

    /** 页面级切换:仅交叉淡化(纸张不做整体位移/缩放,DESIGN 审查 §3) */
    fun pageEnter(reduced: Boolean): EnterTransition =
        fadeIn(tween(if (reduced) MilkMotion.reduced else enter))

    fun pageExit(reduced: Boolean): ExitTransition =
        fadeOut(tween(if (reduced) MilkMotion.reduced else exit))
}

/**
 * 浮层入场(Dialog 内容每次打开都是新组合,故首次组合即播放):
 * 透明度 0→1 + 0.985→1 微缩放;减弱动效时直接呈现。
 */
@Composable
fun Modifier.milkPanelEnter(): Modifier {
    val reduced = LocalMilkMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(MilkMotion.enter))
    }
    return this.graphicsLayer {
        alpha = progress.value
        val s = 0.985f + 0.015f * progress.value
        scaleX = s
        scaleY = s
    }
}
