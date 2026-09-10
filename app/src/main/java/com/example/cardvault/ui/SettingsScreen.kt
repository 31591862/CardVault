package com.example.cardvault.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardvault.BackupJob
import com.example.cardvault.BackupKind
import com.example.cardvault.model.BankCard
import com.example.cardvault.ui.theme.AccentBlue
import com.example.cardvault.ui.theme.IconChipBg
import com.example.cardvault.ui.theme.PageBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 行首图标：圆角方块浅蓝底 + 蓝色图标（AccentBlue / IconChipBg 来自 ui.theme） */

/**
 * 设置页：分组卡片式布局。
 * 每组 = 灰色小节标题 + 白色圆角卡片，卡片内每行 = 圆角色块图标 + 标题/副标题 + 右侧控件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    cardCount: Int,
    onBack: () -> Unit,
    onLeaveForPicker: () -> Unit,
    onScreenshotProtectionChanged: (Boolean) -> Unit,
    onExport: (Uri, String) -> Unit,
    onImport: (Uri, String) -> Unit
) {
    // 必须用 rememberSaveable：系统文件选择器是独立 Activity，打开时本 Activity 可能被系统
    // 回收重建。用 remember 的话密码状态会随重建丢失，选完文件回来回调里拿到 null，
    // 导出代码静默不执行——文件停留在选择器创建时的 0 字节状态，且没有任何报错。
    var pendingExportPassword by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImportUri by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pwd = pendingExportPassword
        pendingExportPassword = null
        if (uri != null && pwd != null) {
            onExport(uri, pwd)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri.toString()
    }

    Scaffold(
        containerColor = PageBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PageBg
                )
            )
        }
    ) { padding ->
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        var autoLock by remember { mutableStateOf(prefs.getBoolean("auto_lock_on_background", false)) }
        // 防截屏默认值必须和 MainActivity.onCreate 一致：release 开、debug 关
        var screenShield by remember {
            mutableStateOf(prefs.getBoolean("screenshot_protection", !com.example.cardvault.BuildConfig.DEBUG))
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            SectionLabel("安全")
            SectionCard {
                SettingSwitchRow(
                    icon = Icons.Filled.Lock,
                    title = "切后台自动上锁",
                    subtitle = "切到其他应用时立即锁定，回来需重新验证",
                    checked = autoLock,
                    onChecked = { on ->
                        autoLock = on
                        prefs.edit().putBoolean("auto_lock_on_background", on).apply()
                    }
                )
                SettingSwitchRow(
                    icon = ShieldIcon,
                    title = "防截屏保护",
                    subtitle = "开启后无法截屏录屏，最近任务缩略图也是空白",
                    checked = screenShield,
                    onChecked = { on ->
                        screenShield = on
                        prefs.edit().putBoolean("screenshot_protection", on).apply()
                        onScreenshotProtectionChanged(on)
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("数据管理")
            SectionCard {
                SettingActionRow(
                    icon = UploadIcon,
                    title = "导出加密备份",
                    subtitle = if (cardCount > 0) "已存 $cardCount 张卡 · 生成加密文件，换机导入" else "暂无卡片可导出",
                    enabled = cardCount > 0,
                    onClick = { pendingExportPassword = "" }
                )
                SettingActionRow(
                    icon = DownloadIcon,
                    title = "从备份导入",
                    subtitle = "与现有卡片合并，不会清空当前数据",
                    enabled = true,
                    onClick = {
                        onLeaveForPicker()
                        importLauncher.launch(arrayOf("*/*"))
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("关于安全")
            SectionCard {
                SecurityNote("卡号只存在这台手机 App 的私有目录里，用设备 Keystore 密钥加密，App 本身不联网。")
                SecurityNote("界面默认只显示卡号后四位，完整卡号要手动点开才会显示。")
                SecurityNote(
                    if (screenShield) "已开启防截屏，截图和最近任务缩略图都会是空白。"
                    else "防截屏已关闭：截图和最近任务会显示 App 内容，注意保管。"
                )
                SecurityNote("卡背面的三位安全码（CVV）和支付密码不提供录入，请不要用备注字段变相记录。")
                SecurityNote("导出密码无法找回。忘了密码，备份文件就永远打不开了。")
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("关于")
            SectionCard {
                SettingInfoRow(
                    icon = Icons.Filled.Info,
                    title = "卡片保险箱",
                    subtitle = "版本 ${com.example.cardvault.BuildConfig.VERSION_NAME}"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (pendingExportPassword != null) {
        PasswordDialog(
            title = "设置备份密码",
            description = "这个密码用来解开导出的备份文件，换手机导入时要输入同一个。请单独记下来。",
            confirmLabel = "导出",
            requireConfirm = true,
            onDismiss = { pendingExportPassword = null },
            onConfirm = { pwd ->
                pendingExportPassword = pwd
                // 跳去系统文件选择器前打标记：onStop 时不重新上锁，
                // 否则回来锁屏页会吞掉选择结果，导出静默失败（0 字节备份 bug）
                onLeaveForPicker()
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())
                exportLauncher.launch("cardvault-backup-$stamp.cvbak")
            }
        )
    }

    if (pendingImportUri != null) {
        PasswordDialog(
            title = "输入备份密码",
            description = "输入导出时设置的密码。导入会与现有卡片合并，不会清空当前数据。",
            confirmLabel = "导入",
            requireConfirm = false,
            onDismiss = { pendingImportUri = null },
            onConfirm = { pwd ->
                val uriStr = pendingImportUri
                pendingImportUri = null
                if (uriStr != null) onImport(android.net.Uri.parse(uriStr), pwd)
            }
        )
    }
}

// ---------- 分组卡片骨架 ----------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF8A9099),
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            content()
        }
    }
}

/** 行首图标：圆角方块浅蓝底 + 蓝色图标 */
@Composable
private fun IconChip(icon: ImageVector, tint: Color = AccentBlue) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(IconChipBg, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconChip(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1B1F27)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA0A9)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentBlue,
                checkedBorderColor = AccentBlue
            )
        )
    }
}

/** 右侧带箭头的可点击行（导出/导入） */
@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconChip(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1B1F27).copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA0A9).copy(alpha = alpha)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFB8BEC7).copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 纯展示行（版本号），不可点、无箭头 */
@Composable
private fun SettingInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconChip(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1B1F27)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA0A9)
            )
        }
    }
}

/** 安全须知行：小图标 + 说明文字 */
@Composable
private fun SecurityNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = Color(0xFFB8C6E8),
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7280),
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------- 手绘图标（core 图标库没有的，零依赖手绘同款） ----------

/** 盾牌：防截屏保护（core 没有 VisibilityOff，手绘盾牌更贴切） */
private val ShieldIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "shield",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            lineTo(20f, 5.5f)
            lineTo(20f, 11f)
            lineTo(17f, 17f)
            lineTo(12f, 21f)
            lineTo(7f, 17f)
            lineTo(4f, 11f)
            lineTo(4f, 5.5f)
            close()
        }
    }.build()
}

/** 上传：托盘 + 向上箭头（Material 风格 upload） */
private val UploadIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "upload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // 箭头杆
            moveTo(11f, 4f)
            lineTo(13f, 4f)
            lineTo(13f, 11f)
            lineTo(11f, 11f)
            close()
            // 箭头头
            moveTo(12f, 3f)
            lineTo(16.5f, 7.5f)
            lineTo(15.1f, 8.9f)
            lineTo(12f, 5.8f)
            lineTo(8.9f, 8.9f)
            lineTo(7.5f, 7.5f)
            close()
            // 托盘底边
            moveTo(4f, 18f)
            lineTo(20f, 18f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            // 托盘两侧
            moveTo(4f, 12f)
            lineTo(6f, 12f)
            lineTo(6f, 18f)
            lineTo(4f, 18f)
            close()
            moveTo(18f, 12f)
            lineTo(20f, 12f)
            lineTo(20f, 18f)
            lineTo(18f, 18f)
            close()
        }
    }.build()
}

/** 下载：托盘 + 向下箭头（Material 风格 download） */
private val DownloadIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "download",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // 箭头杆
            moveTo(11f, 5f)
            lineTo(13f, 5f)
            lineTo(13f, 12f)
            lineTo(11f, 12f)
            close()
            // 箭头头（朝下）
            moveTo(7.5f, 12.5f)
            lineTo(8.9f, 11.1f)
            lineTo(12f, 14.2f)
            lineTo(15.1f, 11.1f)
            lineTo(16.5f, 12.5f)
            lineTo(12f, 17f)
            close()
            // 托盘底边
            moveTo(4f, 18f)
            lineTo(20f, 18f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            // 托盘两侧
            moveTo(4f, 12f)
            lineTo(6f, 12f)
            lineTo(6f, 18f)
            lineTo(4f, 18f)
            close()
            moveTo(18f, 12f)
            lineTo(20f, 12f)
            lineTo(20f, 18f)
            lineTo(18f, 18f)
            close()
        }
    }.build()
}

@Composable
private fun PasswordDialog(
    title: String,
    description: String,
    confirmLabel: String,
    requireConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.size(12.dp))
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it; error = null },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (requireConfirm) {
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedTextField(
                        value = pwd2,
                        onValueChange = { pwd2 = it; error = null },
                        label = { Text("再输一次") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                if (error != null) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pwd.length < 6 -> error = "密码至少 6 位"
                    requireConfirm && pwd != pwd2 -> error = "两次输入不一致"
                    else -> onConfirm(pwd)
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 导出 / 导入备份的阶段进度弹窗。
 *
 * 只显示"第几步 + 当前在干什么"，不显示百分比：PBKDF2 派生和 AES 加解密都是一次性操作，
 * 中途拿不到可量化的进度，给个假百分比反而更让人焦虑（卡在 62% 不动）。
 * 全程不可取消（返回键、点外部都不关），避免中途退出留下写了一半的卡库。
 */
@Composable
internal fun BackupProgressDialog(job: BackupJob) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 56.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = AccentBlue,
                    strokeWidth = 3.5.dp
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = if (job.kind == BackupKind.EXPORT) "正在导出备份" else "正在导入备份",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = job.stageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "第 ${job.step} / ${job.total} 步",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
