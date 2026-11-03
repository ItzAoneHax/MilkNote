package com.milknote.app.ui.writer

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.milknote.app.ui.common.accentBrush
import com.milknote.app.ui.common.innerHighlight
import com.milknote.app.ui.common.paintedShadow
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.common.softShadow
import com.milknote.app.ui.theme.Mono
import com.milknote.app.ui.theme.MonoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内「最近图片」选择弹窗(DESIGN-V0 Milk 柔和圆角风):
 *
 * - 打开时按系统版本请求权限(API 33+ READ_MEDIA_IMAGES / 31–32 READ_EXTERNAL_STORAGE),
 *   拒绝后给说明 + 重试;若系统不再弹(永久拒绝)则引导去系统设置。
 * - MediaStore Images 按 DATE_ADDED DESC 取最近 60 张,IO 线程查询。
 * - LazyVerticalGrid 展示缩略图 + 拍摄日期;缩略图两段式采样解码 + LruCache,防大图 OOM。
 * - onSelect 只回传原始 MediaStore Uri;复制/插入由调用方在主线程完成(本弹窗不落盘)。
 *
 * 集成(WriterScreen 等):
 * ```
 * var showRecentImages by remember { mutableStateOf(false) }
 * if (showRecentImages) {
 *     RecentImagesDialog(
 *         onDismiss = { showRecentImages = false },
 *         onSelect = { uri -> insertImageFromGallery(uri) }, // 主线程自行复制+插入
 *     )
 * }
 * ```
 * 选中一张后弹窗自动关闭(先 onSelect 再 onDismiss),失败重试成本为再次打开。
 */
@Composable
internal fun RecentImagesDialog(
    onDismiss: () -> Unit,
    onSelect: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val permission = remember { RecentImages.requiredPermission() }

    var granted by remember { mutableStateOf(RecentImages.hasPermission(context)) }
    var deniedOnce by remember { mutableStateOf(false) }
    // null = 正在读取;非 null = 已就绪(可能为空列表)
    var items by remember { mutableStateOf<List<RecentImage>?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        granted = g
        if (!g) deniedOnce = true
    }

    // 打开即请求(未授权时);授权后读最近图片
    LaunchedEffect(Unit) { if (!granted) launcher.launch(permission) }
    LaunchedEffect(granted) {
        if (granted && items == null) items = RecentImages.loadRecent(context)
    }

    // 永久拒绝判定:本轮被拒过 + 系统不再显示 rationale → 只能去设置里手动开
    val permanentlyDenied = deniedOnce && !granted && !shouldShowRationale(context, permission)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 横竖屏都不溢出:宽 ≤ min(560dp, 屏宽 92%),高 ≤ 屏高 88%;网格占剩余空间、内部滚动
        val configuration = LocalConfiguration.current
        val maxWidth = minOf(560.dp, (configuration.screenWidthDp * 0.92f).dp)
        val maxHeight = (configuration.screenHeightDp * 0.88f).dp
        // 缩略图解码目标边长(比最小格 92dp 略大,留清晰度余量)
        val thumbTargetPx = with(LocalDensity.current) { 112.dp.roundToPx() }

        Column(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .softShadow(18.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .innerHighlight()
                .paintedShadow(RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            RecentImagesHeader(itemCount = items?.size, onDismiss = onDismiss)
            Spacer(Modifier.height(14.dp))

            when {
                !granted -> PermissionRationaleBody(
                    permanentlyDenied = permanentlyDenied,
                    onRetry = { launcher.launch(permission) },
                    onOpenSettings = { openAppSettings(context) },
                    onDismiss = onDismiss,
                )
                items == null -> LoadingBody()
                items!!.isEmpty() -> EmptyBody()
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 92.dp),
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 2.dp),
                ) {
                    items(items!!, key = { it.id }) { item ->
                        RecentImageCell(
                            item = item,
                            targetPx = thumbTargetPx,
                            onClick = {
                                onSelect(item.uri)
                                onDismiss()
                            },
                        )
                    }
                }
            }

            if (granted) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "点击图片即插入 · 插入时会先复制到笔记",
                    style = MonoMeta,
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

// ---------------- 标题行 ----------------

@Composable
private fun RecentImagesHeader(itemCount: Int?, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Collections,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text("最近图片", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        if (itemCount != null) {
            Text(
                "$itemCount 张",
                style = MonoMeta,
                fontFamily = Mono,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .softClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "×",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- 权限说明 / 拒绝态 ----------------

@Composable
private fun PermissionRationaleBody(
    permanentlyDenied: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(6.dp))
        Icon(
            Icons.Outlined.Collections,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (permanentlyDenied) "相册权限已被拒绝" else "需要访问相册",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (permanentlyDenied) {
                "请在系统设置 → 应用 → MilkNote → 权限中手动允许「照片和视频」,再回来重试。"
            } else {
                "为了挑选最近的图片插入笔记,MilkNote 需要读取设备上的照片。\n照片只在本地使用,不会被上传。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentBrush())
                    .softClickable(onClick = if (permanentlyDenied) onOpenSettings else onRetry)
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            ) {
                Text(
                    if (permanentlyDenied) "去系统设置" else "重新授权",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
            TextButton(onClick = onDismiss) { Text("取消") }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------------- 加载 / 空态 ----------------

@Composable
private fun LoadingBody() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "正在读取相册…",
            style = MonoMeta,
            fontFamily = Mono,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyBody() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Collections,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(10.dp))
        Text("相册里还没有图片", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "拍几张照片或截几张图,再回到这里挑选",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- 网格单元:缩略图 + 日期角标 ----------------

@Composable
private fun RecentImageCell(item: RecentImage, targetPx: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(item.id) { mutableStateOf(RecentImageThumbnails.peek(item.uri)) }
    LaunchedEffect(item.id) {
        if (bitmap == null) {
            RecentImageThumbnails.load(context, item.uri, targetPx)?.let { bitmap = it }
        }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp)) // Milk:缩略图 ~10(§5.1)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .softClickable(onClick = onClick),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // 解码占位:小呼吸圈,避免 60 格同时转圈太吵
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        // 底部日期角标(黑 scrim 渐变,保证浅色照片上可读)
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f),
                    ),
                )
                .padding(start = 7.dp, end = 7.dp, top = 16.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.dateLabel(),
                style = MonoMeta,
                fontFamily = Mono,
                fontSize = 10.sp,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

// ---------------- 数据 / MediaStore 读取 ----------------

/** 相册里最近的一张图片(只记 id 与 Uri;复制/插入由调用方完成) */
internal data class RecentImage(
    val id: Long,
    val uri: Uri,
    val dateAddedSec: Long,
) {
    private val dateFmt by lazy { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    fun dateLabel(): String = dateFmt.format(Date(dateAddedSec * 1000L))
}

/** 权限判定 + MediaStore 查询(最近 N 张,DATE_ADDED DESC,IO 线程) */
internal object RecentImages {
    const val DEFAULT_LIMIT = 60

    /** 33+ 细粒度媒体权限;31–32 旧版读存储(minSdk=31,更老版本不需要管) */
    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    suspend fun loadRecent(context: Context, limit: Int = DEFAULT_LIMIT): List<RecentImage> =
        withContext(Dispatchers.IO) { query(context, limit) }

    private fun query(context: Context, limit: Int): List<RecentImage> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
        )
        // minSdk 31,可用 bundle 参数化查询(排序 + 限量走 MediaStore 保证的列名)
        val bundle = Bundle().apply {
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val out = ArrayList<RecentImage>(limit)
        try {
            context.contentResolver
                .query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, bundle, null)
                ?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        out += RecentImage(
                            id = id,
                            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                            dateAddedSec = c.getLong(dateCol),
                        )
                    }
                }
        } catch (_: SecurityException) {
            // 弹窗打开后权限被系统收回等;返回已读到部分
        } catch (_: IllegalArgumentException) {
            // 个别 ROM 对 bundle 参数的兼容问题;此时视为空相册
        }
        return out
    }
}

/** 打开系统设置的应用详情页(永久拒绝后的唯一出路) */
private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun shouldShowRationale(context: Context, permission: String): Boolean =
    (context as? Activity)?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) } ?: false

// ---------------- 缩略图:采样解码 + LruCache(防 OOM) ----------------

internal object RecentImageThumbnails {
    /** 堆的 1/8,收敛在 8–32MB;按字节数计费 */
    private val cache = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceIn(8 * 1024, 32 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun peek(uri: Uri): Bitmap? = cache.get(uri.toString())

    /** IO 线程解码;命中缓存直接返回。失败返回 null(格子停留占位) */
    suspend fun load(context: Context, uri: Uri, targetPx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            cache.get(uri.toString()) ?: decodeSampled(context, uri, targetPx)?.also { cache.put(uri.toString(), it) }
        }

    /** 两段式解码:先只读边界算 inSampleSize,再真解码到约 [targetPx] 边长(采样防大图 OOM) */
    private fun decodeSampled(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565 // 缩略图不需要 alpha,再省一半内存
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }
}
