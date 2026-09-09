package com.example.cardvault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardvault.model.BankCard
import com.example.cardvault.model.CardType
import com.example.cardvault.model.sortedForHome
import com.example.cardvault.ui.theme.AccentBlue
import com.example.cardvault.ui.theme.PageBg

/** 顶栏的三种形态：常驻布局，用透明度交叉淡化切换 */
private enum class TopBarMode { HOME, SEARCH, SELECT }

/**
 * 顶栏形态槽位：内容永远参与组合和测量（保证槽位宽度恒定、位置不跳），
 * 只对透明度做动画。激活的形态 zIndex 置顶，负责接收点击；其余形态 alpha=0
 * 且压在下层，事件全部落在激活层，不会误触。
 */
@Composable
private fun TopBarCrossfadeSlot(
    active: TopBarMode,
    mine: TopBarMode,
    content: @Composable () -> Unit
) {
    val isSelected = active == mine
    val alpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(200),
        label = "topBarAlpha-$mine"
    )
    Box(
        modifier = Modifier
            .alpha(alpha)
            .zIndex(if (isSelected) 1f else 0f)
    ) {
        content()
    }
}

/**
 * "筛选"图标：三条横线从上到下依次变短（即 Material Icons 的 filter_list）。
 * 该图标在 material-icons-extended 里，core 没收录；为它单独引一个图标库不值当，
 * 这里按官方 path 数据手绘同款矢量，零依赖。
 */
private val FilterIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "filter_list",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // 顶部长条：全宽
            moveTo(3f, 6f)
            lineTo(21f, 6f)
            lineTo(21f, 8f)
            lineTo(3f, 8f)
            close()
            // 中条：半宽
            moveTo(6f, 11f)
            lineTo(18f, 11f)
            lineTo(18f, 13f)
            lineTo(6f, 13f)
            close()
            // 底部短条
            moveTo(10f, 16f)
            lineTo(14f, 16f)
            lineTo(14f, 18f)
            lineTo(10f, 18f)
            close()
        }
    }.build()
}

/**
 * 首页：卡片墙。这里永远只显示掩码卡号，完整卡号要点进详情再手动展开。
 *
 * 筛选和搜索都是纯 UI 状态（不写磁盘）：切后台、上锁、重开 App 都会回到"全部"。
 * 这样不会出现"下次打开发现卡片少了，却忘了是自己筛过"的情况。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(
    cards: List<BankCard>,
    listState: LazyListState,
    onAdd: () -> Unit,
    onOpen: (BankCard) -> Unit,
    onSettings: () -> Unit,
    /** 批量删除回调：参数为要删除的卡片 id 集合 */
    onRemoveSelected: (Collection<String>) -> Unit
) {
    // 筛选项：null = 全部；暂时只按卡片类型筛
    var filterType by remember { mutableStateOf<CardType?>(null) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    // 搜索
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // 批量选择：长按卡片进入；删除需二次确认
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    // 条件条是浮在列表之上的，列表顶部要精确让出它"实际"有多高。
    // 写死一个估值会随字号/按钮高度变化而失准（表现为第一张卡片被压住一角），
    // 所以这里量出真实像素高度再换算成 dp 用。
    var conditionBarHeightPx by remember { mutableStateOf(0) }
    var searchBarHeightPx by remember { mutableStateOf(0) }

    // 多选模式下返回键先退出多选，而不是退出 App
    BackHandler(enabled = selecting) {
        selecting = false
        selectedIds = emptySet()
    }
    // 搜索态下按返回键：先退出搜索，而不是直接退出 App
    BackHandler(enabled = searchActive) {
        searchActive = false
        query = ""
        keyboard?.hide()
    }

    // 排序（姓名 A-Z 拼音 → 信用卡优先 → 更新时间新的在前）后再做筛选/搜索，
    // 保证无论怎么筛，卡片之间的相对顺序都和未筛选时一致。
    //
    // 这里刻意不套 remember：首帧进 App 时 cards 还是空列表，若把结果缓存下来，
    // 等数据加载完（cards 变成 27 张）顶栏已更新、缓存却可能仍是首帧算出的空列表，
    // 表现就是"解锁后首页显示'没有符合条件的卡片'，进任意二级页面返回才恢复"。
    // 卡片量级只有几十条，每次重组重排一次的开销可以忽略，正确性优先。
    val visible = run {
        val ordered = cards.sortedForHome()
        val byType = filterType?.let { t -> ordered.filter { it.type == t } } ?: ordered
        val q = query.trim()
        if (q.isEmpty()) {
            byType
        } else {
            // 卡号比对时两边都只留数字，这样"6225 8801"和"62258801"都能命中
            val digits = q.filter { it.isDigit() }
            byType.filter { card ->
                card.bankName.contains(q, ignoreCase = true) ||
                    (digits.isNotEmpty() && card.cardNumber.filter { it.isDigit() }.contains(digits))
            }
        }
    }

    val hasCondition = filterType != null || query.isNotBlank()

    // 批量删除确认框放在最外层，不受筛选/搜索分支影响
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除 ${selectedIds.size} 张卡片？") },
            text = {
                Text("删除后卡片信息将从这台手机上永久移除，无法撤销。导出的备份文件不受影响。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onRemoveSelected(selectedIds)
                        selectedIds = emptySet()
                        selecting = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        containerColor = PageBg,
        topBar = {
            TopAppBar(
                title = {
                    val mode = when {
                        searchActive -> TopBarMode.SEARCH
                        selecting -> TopBarMode.SELECT
                        else -> TopBarMode.HOME
                    }
                    // 三种形态常驻布局、只用透明度交叉淡化，不用 AnimatedContent：
                    // 顶栏标题槽位宽度会随按钮区宽度瞬时变化，AnimatedContent 的容器尺寸动画
                    // 会把通栏搜索框裁出直边、槽位跳变还会让"我的卡片"左右弹跳。
                    // 常驻布局宽度恒定，alpha 只影响绘制不参与测量，两类问题都根治。
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TopBarCrossfadeSlot(mode, TopBarMode.HOME) {
                            Column {
                                Text(
                                    text = "我的卡片",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (cards.isNotEmpty()) {
                                    Text(
                                        text = if (hasCondition) {
                                            "筛选出 ${visible.size} / ${cards.size} 张"
                                        } else {
                                            "共 ${cards.size} 张"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasCondition) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                        TopBarCrossfadeSlot(mode, TopBarMode.SELECT) {
                            Text(
                                text = "已选 ${selectedIds.size} 张",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    val mode = when {
                        searchActive -> TopBarMode.SEARCH
                        selecting -> TopBarMode.SELECT
                        else -> TopBarMode.HOME
                    }
                    // 与标题区同理：常驻布局 + 透明度交叉淡化，右侧对齐。
                    // ⚠️ 这里绝不能 fillMaxWidth：TopAppBar 会先量按钮区再分剩余宽度给标题，
                    // 按钮区吃满整行会把标题槽位饿死（标题消失、通栏搜索框被挤成细长条、顶栏撑高）
                    Box(contentAlignment = Alignment.CenterEnd) {
                        TopBarCrossfadeSlot(mode, TopBarMode.SELECT) {
                            Row {
                                TextButton(
                                    onClick = {
                                        // 全选 ↔ 全不选：当前全部选中时点击即清空
                                        selectedIds = if (selectedIds.size == cards.size) {
                                            emptySet()
                                        } else {
                                            cards.map { it.id }.toSet()
                                        }
                                    }
                                ) {
                                    Text(if (selectedIds.size == cards.size) "全不选" else "全选", fontSize = 13.sp)
                                }
                                IconButton(
                                    onClick = { showDeleteConfirm = true },
                                    enabled = selectedIds.isNotEmpty()
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除所选",
                                        tint = if (selectedIds.isNotEmpty()) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        }
                                    )
                                }
                                IconButton(onClick = {
                                    selecting = false
                                    selectedIds = emptySet()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "退出选择")
                                }
                            }
                        }
                        TopBarCrossfadeSlot(mode, TopBarMode.SEARCH) {
                            TextButton(
                                onClick = {
                                    searchActive = false
                                    query = ""
                                    keyboard?.hide()
                                }
                            ) {
                                Text("取消")
                            }
                        }
                        TopBarCrossfadeSlot(mode, TopBarMode.HOME) {
                            Row {
                                Box {
                                    IconButton(onClick = { filterMenuExpanded = true }) {
                                        Icon(
                                            FilterIcon,
                                            contentDescription = "筛选",
                                            tint = if (filterType != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    FilterMenu(
                                        expanded = filterMenuExpanded,
                                        selected = when (filterType) {
                                            null -> 0
                                            CardType.DEBIT -> 1
                                            CardType.CREDIT -> 2
                                        },
                                        onSelect = { index ->
                                            filterType = when (index) {
                                                1 -> CardType.DEBIT
                                                2 -> CardType.CREDIT
                                                else -> null
                                            }
                                            filterMenuExpanded = false
                                        },
                                        onDismiss = { filterMenuExpanded = false }
                                    )
                                }
                                IconButton(onClick = { searchActive = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "搜索")
                                }
                                IconButton(onClick = onSettings) {
                                    Icon(Icons.Default.Settings, contentDescription = "设置")
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PageBg
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = AccentBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加卡片")
            }
        }
        // 注意：这里刻意用 Box + fillMaxSize，不用 Column + weight(1f)。
        // 解锁动画（AnimatedContent 的 SizeTransform）测量目标尺寸时会给出无界高度约束，
        // 那种约束下 Column 的 weight 不生效，LazyColumn 会被测成 0 高度从而一帧都不渲染
        // （表现为"解锁后首页空白，但顶栏张数正常，进任意页面返回才恢复"）。
        // 所以列表必须是 content 的直接子项自己撑满，条件条做成浮层。
        ) { padding ->
        // 搜索栏展开时，列表和条件条要让出的顶部空间（与展开动画同步 200ms）
        val searchSpace by animateDpAsState(
            targetValue = if (searchActive) {
                with(density) { searchBarHeightPx.toDp() + 6.dp }
            } else {
                0.dp
            },
            animationSpec = tween(200),
            label = "searchSpace"
        )
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                cards.isEmpty() -> EmptyGuide()
                visible.isEmpty() -> NoResult(
                    onReset = {
                        filterType = null
                        query = ""
                        searchActive = false
                    }
                )
                else -> LazyColumn(
                    // 滚动状态由 AppHost 提升持有：列表页被切走再回来，state 对象不销毁，
                    // 滚到哪儿回哪儿。若靠 AnimatedContent 的保存机制恢复，恢复瞬间列表
                    // 可能还在空数据/0 高度状态，位置会被重置回顶部。
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 4.dp,
                        // 依次让出：搜索栏（展开时）+ 条件条的真实高度，各留 6dp 间隙
                        top = 4.dp + searchSpace + (
                            if (hasCondition && visible.isNotEmpty()) {
                                with(density) { conditionBarHeightPx.toDp() } + 6.dp
                            } else {
                                0.dp
                            }
                            )
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(visible, key = { it.id }) { card ->
                        val isSelected = card.id in selectedIds
                        Box(modifier = Modifier.fillMaxWidth()) {
                            BankCardFace(
                                card = card,
                                revealed = false,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (selecting) {
                                        // 多选模式：点按切换选中；全不选时自动退出
                                        val next = if (isSelected) {
                                            selectedIds - card.id
                                        } else {
                                            selectedIds + card.id
                                        }
                                        selectedIds = next
                                        if (next.isEmpty()) selecting = false
                                    } else {
                                        onOpen(card)
                                    }
                                },
                                // 长按进入多选；搜索态下不响应长按
                                onLongClick = if (!searchActive && !selecting) {
                                    {
                                        selecting = true
                                        selectedIds = setOf(card.id)
                                    }
                                } else {
                                    null
                                }
                            )
                            if (selecting && !isSelected) {
                                // 多选模式下未选中的卡整体压白变淡——
                                // 选中态靠"明暗对比"表达，比任何描边颜色都醒目，也不引入外来色
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.62f))
                                )
                            }
                            if (isSelected) {
                                // 白色隔离圈 + 主色描边（画在卡面上层，不受压暗层影响）。
                                // 不用 border 修饰符：它的描边骑在边线上居中绘制，会溢出卡片边界，
                                // 圆角抗锯齿处会露出杂边。drawBehind + 内缩矩形保证描边 100% 落在卡内：
                                // 主色 0~3dp，白色 3~5dp，外沿与卡片边缘重合，外面什么都不画。
                                val selPrimary = MaterialTheme.colorScheme.primary
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .drawBehind {
                                            innerRoundRectStroke(Color.White, 5.dp.toPx(), 16.dp.toPx())
                                            innerRoundRectStroke(selPrimary, 3.dp.toPx(), 16.dp.toPx())
                                        }
                                )
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "已选中",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 14.dp)
                                        .size(28.dp)
                                )
                            }
                        }
                    }
                    // FAB 占的底部空间
                    item { Spacer(modifier = Modifier.size(72.dp)) }
                }
            }

            // 通栏不透明背板：高度 = searchSpace，与条件条的下移、列表的让位同一个动画值，
            // 保证搜索栏展开过程中，搜索栏与条件条之间的区域永远没有缝隙透出卡片。
            // （搜索框自己的 expandVertically 和条件条的 animateDpAsState 是两套动画，
            //   逐帧不可能完全对齐，缝隙就出现在动画进行中）
            if (searchActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(searchSpace)
                        .background(PageBg)
                )
            }

            // 搜索栏：展开/收起在顶栏正下方，浮在列表之上。
            // 绝对不放 TopAppBar 标题槽——槽位宽度由内容自然决定，通栏输入框在那种
            // 测量下会折行、超界、顶栏撑高（前几轮所有问题的总根源）
            AnimatedVisibility(
                visible = searchActive,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(200)) + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut(tween(180)) + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                // 通栏不透明底：白色胶囊四周的圆角空隙和 padding 区不能透出下面的卡片
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PageBg)
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .onSizeChanged { searchBarHeightPx = it.height }
                            .focusRequester(focusRequester),
                    placeholder = {
                        Text("搜银行名称或卡号", fontSize = 14.sp)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                }
            }

            // 筛选/搜索条件浮层：一眼看得出"我在筛选"，点 × 立即还原。
            // 搜索栏展开时整体下移，给搜索栏让位
            if (hasCondition && cards.isNotEmpty() && visible.isNotEmpty()) {
                ConditionBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = searchSpace)
                        .onSizeChanged { conditionBarHeightPx = it.height },
                    filterType = filterType,
                    resultCount = visible.size,
                    onClearFilter = { filterType = null },
                    onReset = {
                        filterType = null
                        query = ""
                        searchActive = false
                    }
                )
            }
        }
    }
}

/** 筛选下拉菜单：全部 / 储蓄卡 / 信用卡 */
@Composable
private fun FilterMenu(
    expanded: Boolean,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val labels = listOf("全部类型", "储蓄卡", "信用卡")
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        labels.forEachIndexed { index, label ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        color = if (index == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Unspecified
                        }
                    )
                },
                onClick = { onSelect(index) },
                trailingIcon = if (index == selected) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}

/** 条件常驻条：蓝色 chip 显示当前筛选类型，右侧显示结果数和"清除" */
@Composable
private fun ConditionBar(
    modifier: Modifier = Modifier,
    filterType: CardType?,
    resultCount: Int,
    onClearFilter: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 浮在列表之上，必须不透明，否则会透出卡片；颜色跟页面底色保持一致
            .background(PageBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (filterType != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = onClearFilter
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filterType.label,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "清除筛选",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "$resultCount 张",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(10.dp))
        TextButton(onClick = onReset) {
            Text("清除", fontSize = 12.sp)
        }
    }
}

/** 一张卡都没录入时的引导 */
@Composable
private fun EmptyGuide(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "还没有录入卡片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "点右下角的加号，添加第一张卡。\n卡号只存在这台手机的加密文件里，不会联网。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

/** 筛完/搜完一张都不剩 */
@Composable
private fun NoResult(
    modifier: Modifier = Modifier,
    onReset: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "没有符合条件的卡片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "换一个关键词，或者清除当前的筛选条件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.size(16.dp))
            TextButton(onClick = onReset) {
                Text("清除条件")
            }
        }
    }
}

/**
 * 画一条完全落在绘制区域边界内的圆角描边。
 * Modifier.border 的描边骑在边线上居中绘制，会有一半溢出边界，在圆角抗锯齿处露出杂边；
 * 这里把矩形整体内缩 width/2，描边外沿正好贴着边界，边界外不产生任何像素。
 */
private fun DrawScope.innerRoundRectStroke(color: Color, widthPx: Float, outerRadiusPx: Float) {
    val half = widthPx / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - widthPx, size.height - widthPx),
        cornerRadius = CornerRadius((outerRadiusPx - half).coerceAtLeast(0f)),
        style = Stroke(width = widthPx)
    )
}
