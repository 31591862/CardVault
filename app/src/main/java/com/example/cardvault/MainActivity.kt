package com.example.cardvault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.cardvault.data.BackupCrypto
import com.example.cardvault.data.BackupFormatException
import com.example.cardvault.data.VaultRepository
import com.example.cardvault.model.BankCard
import com.example.cardvault.ui.BackupProgressDialog
import com.example.cardvault.ui.CardDetailScreen
import com.example.cardvault.ui.CardEditScreen
import com.example.cardvault.ui.CardListScreen
import com.example.cardvault.ui.SettingsScreen
import com.example.cardvault.ui.theme.CardVaultTheme

class MainActivity : FragmentActivity() {

    /** 是否通过身份验证。放在 Activity 里，方便 onStop 时直接改。 */
    private val unlocked = mutableStateOf(false)

    /**
     * 正在等待系统选择器（SAF 文件选择）返回结果。
     * 跳去文件选择器会触发本 Activity 的 onStop，但这不算"离开 App"：
     * 如果此时上锁，回来后锁屏页会把设置页连同 ActivityResult 回调一起移出组合树，
     * 系统投递"用户选好的文件位置"时无处接收 → 导出静默失败 → 文件停留 0 字节。
     */
    internal var expectingExternalResult = false

    /**
     * 按设置页开关应用/解除防截屏标志。
     * FLAG_SECURE 是窗口标志，运行时可随时加减；设置页切开关时直接调用，即时生效。
     */
    internal fun applySecureFlag(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 防截屏 / 防录屏 / 最近任务空白缩略图（FLAG_SECURE）
        // 设置页可开关（screenshot_protection）。默认值：release 开、debug 关——
        // debug 默认关是为了 adb 截图调试 UI 不被挡，release 必须默认保护
        applySecureFlag(
            getSharedPreferences("settings", MODE_PRIVATE)
                .getBoolean("screenshot_protection", !BuildConfig.DEBUG)
        )

        // debug 专属：adb am start --es goto <page> 直接跳到指定页，方便真机自动化调试
        // goto=saveTest 会先自动保存一张测试卡再回列表页，用于自动化验证保存链路
        val initialScreen = if (BuildConfig.DEBUG) {
            when (intent.getStringExtra("goto")) {
                "edit" -> Screen.Edit(null)
                "settings" -> Screen.Settings
                "saveTest" -> Screen.SaveTest
                "clearTest" -> Screen.ClearTest
                "exportTest" -> Screen.ExportTest
                "safTest" -> Screen.SafTest
                else -> Screen.List
            }
        } else Screen.List

        setContent {
            CardVaultTheme {
                AppHost(
                    unlockedState = unlocked,
                    onAuthenticate = { runBiometricPrompt() },
                    onExpectExternalResult = { expectingExternalResult = true },
                    onSetSecure = { applySecureFlag(it) },
                    initialScreen = initialScreen
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // "切后台自动上锁"可在设置页开关，默认关闭（用户选择便利优先）。
        // 注意：进程被系统回收重建后仍需重新验证，这是 Android 机制，不受本开关影响。
        val autoLock = getSharedPreferences("settings", MODE_PRIVATE)
            .getBoolean("auto_lock_on_background", false)
        if (BuildConfig.DEBUG) {
            Log.d("CardVault", "onStop: expectingExternalResult=$expectingExternalResult, autoLock=$autoLock")
        }
        // 去系统文件选择器选路径不算离开——此时上锁会吞掉导出回调（0 字节备份 bug）
        if (!expectingExternalResult && autoLock) {
            unlocked.value = false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 选择器结果已回来（无论用户选没选），恢复正常锁屏策略
        if (BuildConfig.DEBUG) {
            Log.d("CardVault", "onActivityResult: reset expectingExternalResult")
        }
        expectingExternalResult = false
    }

    private fun runBiometricPrompt() {
        val canAuth = BiometricManager.from(this)
            .canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // 设备没录指纹也没设锁屏密码，无法验证；此时手机本身没有任何防线
            Toast.makeText(this, "设备未设置指纹或锁屏密码，跳过验证", Toast.LENGTH_LONG).show()
            unlocked.value = true
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked.value = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(this@MainActivity, "验证失败：$errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    // 单次指纹不匹配，系统会自动让再试一次，这里不用处理
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁卡片保险箱")
            .setSubtitle("验证身份后查看卡号")
            // 用了 DEVICE_CREDENTIAL 就不能再设 setNegativeButtonText，否则会崩
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}

private sealed class Screen {
    data object List : Screen()
    data class Detail(val id: String) : Screen()
    data class Edit(val id: String?) : Screen()
    data object Settings : Screen()
    data object SaveTest : Screen()
    data object ClearTest : Screen()
    data object ExportTest : Screen()
    data object SafTest : Screen()
}

/**
 * screen 状态要能扛过进程被杀重建：否则选择器返回时如果设置页不在组合树里，
 * 待投递的选择结果无处接收，导出又会静默失败。
 */
private val screenSaver = Saver<Screen, String>(
    save = { when (it) {
        is Screen.Detail -> "d:${it.id}"
        is Screen.Edit -> "e:${it.id ?: ""}"
        Screen.Settings -> "s"
        else -> "l"
    } },
    restore = { when {
        it.startsWith("d:") -> Screen.Detail(it.substring(2))
        it.startsWith("e:") -> Screen.Edit(it.substring(2).takeIf(String::isNotBlank))
        it == "s" -> Screen.Settings
        else -> Screen.List
    } }
)

/** 备份任务类型：决定进度弹窗的标题 */
internal enum class BackupKind { EXPORT, IMPORT }

/**
 * 正在执行的备份任务进度。
 * 只有 step/total 这种"阶段进度"是真实的 —— PBKDF2 派生、AES 解密都是一次性操作，
 * 中途拿不到可量化的百分比，所以刻意不做假百分比，只告诉用户"现在在第几步、在干什么"。
 */
internal data class BackupJob(
    val kind: BackupKind,
    val stageText: String,
    val step: Int,
    val total: Int
)

@Composable
private fun AppHost(
    unlockedState: androidx.compose.runtime.MutableState<Boolean>,
    onAuthenticate: () -> Unit,
    onExpectExternalResult: () -> Unit,
    onSetSecure: (Boolean) -> Unit,
    initialScreen: Screen = Screen.List
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val unlocked by unlockedState

    val repo = remember { VaultRepository(context.applicationContext) }
    val cards = remember { mutableStateListOf<BankCard>() }
    var screen by rememberSaveable(stateSaver = screenSaver) { mutableStateOf(initialScreen) }
    // 导航方向：true = 往子页面进（新页从右侧滑入），false = 返回（新页从左侧滑入）
    var navForward by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    // 首页列表的滚动状态提升到这里持有：它不随页面切换/锁屏销毁重建，
    // 从二级页面返回时 LazyColumn 恢复原滚动位置（从哪来回哪去）。
    // 放在 AnimatedContent 外面，锁屏/解锁切换也不会丢。
    val listState = rememberLazyListState()
    // 导出/导入必须跑在后台线程：PBKDF2 21 万轮派生 + AES 加解密是纯 CPU 活，
    // 放在主线程会把 Compose 的渲染和动画一起冻住（表现为"点了像死机"）。
    val scope = rememberCoroutineScope()
    var backupJob by remember { mutableStateOf<BackupJob?>(null) }

    LaunchedEffect(unlocked) {
        if (unlocked) {
            if (!loaded) {
                loaded = true
                runCatching { repo.load() }
                    .onSuccess { cards.clear(); cards.addAll(it) }
                    .onFailure {
                        Toast.makeText(context, "卡库读取失败：${it.message}", Toast.LENGTH_LONG).show()
                    }
            }
        } else {
            loaded = false
            onAuthenticate()
        }
    }

    // 锁屏 ↔ 主页整层过渡：解锁时锁屏淡出、主页淡入上滑；上锁反向。
    // 之前是裸 if/return 整树替换，解锁进列表页才会生硬地一闪。
    AnimatedContent(
        targetState = unlocked,
        transitionSpec = {
            val enter = fadeIn(animationSpec = tween(260)) + slideInVertically(
                animationSpec = tween(300)
            ) { full -> if (targetState) full / 6 else -full / 8 }
            val exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(
                animationSpec = tween(280)
            ) { full -> if (targetState) -full / 8 else full / 6 }
            enter togetherWith exit
        },
        label = "lock_transition"
    ) { isUnlocked ->
        if (!isUnlocked) {
            LockScreen(onUnlock = onAuthenticate)
        } else {

        // 只在已解锁时拦截返回键，锁屏层不碰页面导航
        BackHandler(enabled = screen != Screen.List) {
            navForward = false
            screen = Screen.List
        }

    fun persist(list: List<BankCard>) {
        try {
            repo.save(list)
            cards.clear()
            cards.addAll(list.sortedByDescending { it.updatedAt })
        } catch (e: Exception) {
            Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun goTo(target: Screen) {
        navForward = true
        screen = target
    }

    fun goBack() {
        navForward = false
        screen = Screen.List
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            // 进子页面：新页从右边滑入、旧页往左滑出；返回时方向反过来
            val enter = fadeIn(animationSpec = tween(180)) + slideInHorizontally(
                animationSpec = tween(240),
                initialOffsetX = { full -> if (navForward) full / 4 else -full / 4 }
            )
            val exit = fadeOut(animationSpec = tween(180)) + slideOutHorizontally(
                animationSpec = tween(240),
                targetOffsetX = { full -> if (navForward) -full / 4 else full / 4 }
            )
            enter togetherWith exit
        },
        label = "page_transition"
    ) { target ->
        when (target) {
            Screen.List -> CardListScreen(
                cards = cards,
                listState = listState,
                onAdd = { goTo(Screen.Edit(null)) },
                onOpen = { goTo(Screen.Detail(it.id)) },
                onSettings = { goTo(Screen.Settings) },
                onRemoveSelected = { ids ->
                    persist(cards.filter { it.id !in ids })
                    Toast.makeText(context, "已删除 ${ids.size} 张卡", Toast.LENGTH_SHORT).show()
                }
            )

            is Screen.Detail -> {
                val card = cards.firstOrNull { it.id == target.id }
                if (card == null) {
                    LaunchedEffect(Unit) { goBack() }
                } else {
                    CardDetailScreen(
                        card = card,
                        onBack = { goBack() },
                        onEdit = { goTo(Screen.Edit(card.id)) },
                        onDelete = {
                            persist(cards.filter { it.id != card.id })
                            goBack()
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            is Screen.Edit -> {
                val existing = target.id?.let { id -> cards.firstOrNull { it.id == id } }
                CardEditScreen(
                    initial = existing,
                    existingCards = cards,
                    onBack = { goBack() },
                    onSave = { saved ->
                        val next = if (cards.any { it.id == saved.id }) {
                            cards.map { if (it.id == saved.id) saved else it }
                        } else {
                            cards + saved
                        }
                        persist(next)
                        goBack()
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Screen.Settings -> SettingsScreen(
                cardCount = cards.size,
                onBack = { goBack() },
                onLeaveForPicker = onExpectExternalResult,
                onScreenshotProtectionChanged = onSetSecure,
                onExport = { uri, password ->
                    if (activity != null) {
                        scope.launch {
                            try {
                                backupJob = BackupJob(BackupKind.EXPORT, "正在整理卡片数据…", 1, 3)
                                val json = withContext(Dispatchers.Default) { repo.toExportJson(cards) }

                                backupJob = BackupJob(BackupKind.EXPORT, "正在加密备份数据…", 2, 3)
                                val payload = withContext(Dispatchers.Default) {
                                    BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), password)
                                }

                                backupJob = BackupJob(BackupKind.EXPORT, "正在写入文件…", 3, 3)
                                withContext(Dispatchers.IO) {
                                    // "wt" = write + truncate：部分厂商 DocumentsProvider 覆盖写已有文件时
                                    // 用默认 "w" 模式会出现只清空不写入（文件 0 字节）的怪癖
                                    activity.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                                        out.write(payload)
                                        out.flush()
                                    } ?: throw IllegalStateException("无法打开目标文件写入")
                                    // 写完读回来校验：文件系统说谎时不能谎报"已导出"
                                    val written = activity.contentResolver.openInputStream(uri)?.use { it.readBytes().size }
                                        ?: throw IllegalStateException("无法读回备份文件校验")
                                    if (written != payload.size) {
                                        throw IllegalStateException("写入不完整（期望 ${payload.size}B，实际 ${written}B）")
                                    }
                                }
                                Toast.makeText(context, "已导出 ${cards.size} 张卡", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                backupJob = null
                            }
                        }
                    }
                },
                onImport = { uri, password ->
                    if (activity != null) {
                        scope.launch {
                            try {
                                backupJob = BackupJob(BackupKind.IMPORT, "正在读取备份文件…", 1, 4)
                                val bytes = withContext(Dispatchers.IO) {
                                    activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        ?: throw IllegalStateException("无法读取备份文件")
                                }

                                backupJob = BackupJob(BackupKind.IMPORT, "正在校验密码并解密…", 2, 4)
                                val plain = withContext(Dispatchers.Default) {
                                    BackupCrypto.decrypt(bytes, password).toString(Charsets.UTF_8)
                                }

                                backupJob = BackupJob(BackupKind.IMPORT, "正在合并卡片…", 3, 4)
                                // 先在主线程快照当前卡库，后台线程只做纯计算
                                val snapshot = cards.toList()
                                val merged = withContext(Dispatchers.Default) {
                                    mergeCards(snapshot, repo.fromExportJson(plain))
                                }

                                backupJob = BackupJob(BackupKind.IMPORT, "正在写入卡库…", 4, 4)
                                withContext(Dispatchers.IO) { repo.save(merged) }
                                // 状态更新回主线程
                                cards.clear()
                                cards.addAll(merged.sortedByDescending { it.updatedAt })
                                goBack()
                                Toast.makeText(context, "导入完成，共 ${merged.size} 张卡", Toast.LENGTH_LONG).show()
                            } catch (e: BackupFormatException) {
                                Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                backupJob = null
                            }
                        }
                    }
                }
            )

            Screen.SaveTest -> {
                // debug 专属自动化用例：保存一张测试卡，验证 Keystore 加密写入链路，完事回列表页
                LaunchedEffect(Unit) {
                    val now = System.currentTimeMillis()
                    val testCard = BankCard(
                        id = "debug-test-card",
                        bankName = "自动化测试银行",
                        nickname = "IV验证卡",
                        cardNumber = "6225768888888888",
                        holderName = "测试用户",
                        expiry = "0928",
                        type = com.example.cardvault.model.CardType.CREDIT,
                        createdAt = now,
                        updatedAt = now
                    )
                    val next = if (cards.any { it.id == testCard.id }) {
                        cards.map { if (it.id == testCard.id) testCard else it }
                    } else {
                        cards + testCard
                    }
                    persist(next)
                    Toast.makeText(context, "SaveTest 已执行，卡库共 ${next.size} 张", Toast.LENGTH_LONG).show()
                    screen = Screen.List
                }
            }

            Screen.ClearTest -> {
                // debug 专属：删除 SaveTest 写入的测试卡，恢复干净状态
                LaunchedEffect(Unit) {
                    val next = cards.filter { it.id != "debug-test-card" }
                    persist(next)
                    Toast.makeText(context, "测试卡已清除，剩 ${next.size} 张", Toast.LENGTH_LONG).show()
                    screen = Screen.List
                }
            }

            Screen.SafTest -> {
                // debug 专属：程序化发起真实 SAF 文件选择器（与正式导出同一条链路）。
                // 进入本页即设置"等待外部结果"标记 → 跳选择器不上锁；
                // 用户在选择器里点保存后回来，回调直接执行并读回校验。
                val safLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    if (uri == null) {
                        Toast.makeText(context, "SAFTest: 已取消", Toast.LENGTH_LONG).show()
                    } else {
                        try {
                            val json = repo.toExportJson(cards)
                            val payload = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), "test123456")
                            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                                out.write(payload)
                                out.flush()
                            } ?: throw IllegalStateException("openOutputStream 返回 null")
                            val readBack = context.contentResolver.openInputStream(uri)?.use { it.readBytes().size } ?: -1
                            val verdict = if (readBack == payload.size) "OK" else "不完整!"
                            Toast.makeText(
                                context,
                                "SAFTest: 加密 ${payload.size}B / 读回 ${readBack}B $verdict",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "SAFTest 失败：${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    navForward = false
                    screen = Screen.List
                }
                LaunchedEffect(Unit) {
                    onExpectExternalResult()
                    safLauncher.launch("cvsaf-test.cvbak")
                }
            }

            Screen.ExportTest -> {
                // debug 专属：重现导出链路。写入走 ContentResolver（与 SAF 导出同路径），
                // 写完立即读回大小，排查"备份文件 0 字节"问题。
                LaunchedEffect(Unit) {
                    try {
                        val now = System.currentTimeMillis()
                        val card = BankCard(
                            id = "debug-test-card",
                            bankName = "自动化测试银行",
                            nickname = "导出验证卡",
                            cardNumber = "6225768888888888",
                            holderName = "测试用户",
                            expiry = "0928",
                            type = com.example.cardvault.model.CardType.CREDIT,
                            createdAt = now,
                            updatedAt = now
                        )
                        val list = if (cards.any { it.id == card.id }) {
                            cards.map { if (it.id == card.id) card else it }
                        } else cards + card
                        repo.save(list)
                        cards.clear()
                        cards.addAll(list.sortedByDescending { it.updatedAt })

                        val json = repo.toExportJson(list)
                        val payload = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), "test123456")

                        // 与用户导出相同的写入通道：ContentResolver + openOutputStream
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "cvtest-$now.cvbak")
                            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                        }
                        val uri = context.contentResolver.insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                        ) ?: throw IllegalStateException("MediaStore insert 返回 null")
                        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            out.write(payload)
                            out.flush()
                        } ?: throw IllegalStateException("openOutputStream 返回 null")
                        val readBack = context.contentResolver.openInputStream(uri)?.use { it.readBytes().size } ?: -1
                        val verdict = if (readBack == payload.size) "OK" else "不完整!"
                        Toast.makeText(
                            context,
                            "ExportTest: 加密 ${payload.size}B / 读回 ${readBack}B $verdict",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "ExportTest 失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                    screen = Screen.List
                }
            }
        }
        }
            // 备份任务进行中：整屏遮罩 + 阶段进度。
            // 期间禁止返回键/点外部关闭 —— 中途取消会留下写了一半的卡库
            backupJob?.let { job -> BackupProgressDialog(job) }
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = "卡片保险箱已锁定",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "验证指纹或锁屏密码后查看",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.size(24.dp))
            Button(onClick = onUnlock) { Text("解锁") }
        }
    }
}

/** 合并导入的卡片：同 id 取更新时间更晚的那条，新 id 直接加入 */
private fun mergeCards(existing: List<BankCard>, incoming: List<BankCard>): List<BankCard> {
    val map = existing.associateBy { it.id }.toMutableMap()
    incoming.forEach { c ->
        val old = map[c.id]
        if (old == null || c.updatedAt > old.updatedAt) map[c.id] = c
    }
    return map.values.sortedByDescending { it.updatedAt }
}
