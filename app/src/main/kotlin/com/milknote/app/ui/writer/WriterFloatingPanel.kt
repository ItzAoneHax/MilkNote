package com.milknote.app.ui.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.common.milkDialogSurface

/** 整页居中的工具编辑卡片：12% 轻暗化 + 共享柔和阴影，不再锚定工具按钮。 */
@Composable
internal fun WriterFloatingPanel(
    onDismiss: () -> Unit,
    width: Dp = 420.dp,
    maxHeight: Dp = 620.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().imePadding().testTag("writer-floating-panel-root")) {
        Box(
            Modifier
                .fillMaxSize()
                .background(com.milknote.app.ui.theme.MilkScrim)
                .clickable(onClick = onDismiss)
                .testTag("writer-floating-panel-scrim"),
        )
        Box(
            Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = width)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .milkDialogSurface()
                    .testTag("writer-floating-panel"),
                content = content,
            )
        }
        androidx.activity.compose.BackHandler(enabled = true, onBack = onDismiss)
    }
}
