package com.example.cardvault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.cardvault.model.BankCard
import com.example.cardvault.model.CardBrand
import com.example.cardvault.model.CardType
import com.example.cardvault.model.detectBrand
import com.example.cardvault.model.luhnValid
import com.example.cardvault.model.numberLengthHint
import com.example.cardvault.ui.theme.PageBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditScreen(
    initial: BankCard?,
    onBack: () -> Unit,
    onSave: (BankCard) -> Unit,
    /** 卡库里现有的全部卡片，用来拦截重复卡号 */
    existingCards: List<BankCard> = emptyList()
) {
    var bankName by remember { mutableStateOf(initial?.bankName.orEmpty()) }
    var nickname by remember { mutableStateOf(initial?.nickname.orEmpty()) }
    var cardNumber by remember { mutableStateOf(initial?.cardNumber.orEmpty()) }
    var holderName by remember { mutableStateOf(initial?.holderName.orEmpty()) }
    var expiry by remember { mutableStateOf(initial?.expiry.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var type by remember { mutableStateOf(initial?.type ?: CardType.DEBIT) }
    // 卡组织：null = 跟随自动识别；用户手选后固定为所选值
    var brandChoice by remember { mutableStateOf(initial?.brandOverride) }
    var brandMenuOpen by remember { mutableStateOf(false) }
    var creditLimit by remember { mutableStateOf(initial?.creditLimit.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }

    val digits = cardNumber.filter { it.isDigit() }
    val brand = detectBrand(digits)
    val luhnOk = luhnValid(digits)
    val lenHint = numberLengthHint(digits)
    val bankMissing = bankName.isBlank()
    val numberMissing = digits.isBlank()
    // 卡号重复：与库里其他卡（不含自己）的卡号撞号
    val numberDuplicate = digits.isNotEmpty() &&
        existingCards.any { it.cardNumber == digits && it.id != initial?.id }

    fun doSave() {
        if (bankMissing || numberMissing || numberDuplicate) {
            showErrors = true
            return
        }
        onSave(
            (initial ?: BankCard()).copy(
                bankName = bankName.trim(),
                nickname = nickname.trim(),
                cardNumber = digits,
                holderName = holderName.trim(),
                expiry = expiry.trim(),
                type = type,
                brandOverride = brandChoice,
                // 额度只在信用卡下有意义；切回储蓄卡时清空，避免残留数据
                creditLimit = if (type == CardType.CREDIT) creditLimit.filter { it.isDigit() } else "",
                phone = phone.trim(),
                note = note.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (initial == null) "添加卡片" else "编辑卡片",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { doSave() }) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PageBg
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                // Scaffold 给的 padding 包含 statusBar 和可能的 bottomBar inset，
                // 这里 weight(1f) 让滚动容器填满中间可用区域，避免 fillMaxSize 把 TopBar 顶出屏幕
                .padding(padding)
                .fillMaxSize()
                // edge-to-edge（API 35+ 强制）下 adjustResize 不再自动压缩窗口，
                // 键盘以 ime inset 派发，必须显式消费，否则有效期/手机号/备注被键盘遮住
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it },
                label = { Text("银行名称 *") },
                placeholder = { Text("例如：招商银行") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = showErrors && bankMissing,
                supportingText = { if (showErrors && bankMissing) Text("必填") }
            )

            Spacer(modifier = Modifier.size(8.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("卡片备注") },
                placeholder = { Text("例如：工资卡") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.size(8.dp))

            OutlinedTextField(
                value = cardNumber,
                onValueChange = { cardNumber = it.filter { c -> c.isDigit() }.take(19) },
                label = { Text("卡号 *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = numberDuplicate || (showErrors && numberMissing),
                supportingText = {
                    when {
                        // 重复提示实时出现：输完最后一位立刻标红，不用等点保存
                        numberDuplicate -> Text("该卡号已添加过，不能重复添加")
                        showErrors && numberMissing -> Text("必填")
                        digits.isBlank() -> Unit
                        !luhnOk && digits.length >= 12 -> Text("卡号校验没通过，可能打错了一位")
                        lenHint != null && luhnOk -> Text(lenHint)
                        brand.label.isNotBlank() -> Text("识别为 ${brand.label}")
                        else -> Unit
                    }
                }
            )

            Spacer(modifier = Modifier.size(8.dp))

            // 卡组织下拉框：默认"自动识别"（实时跟随卡号 BIN），手选后固定为所选值
            Text(
                text = "卡组织",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(4.dp))
            Box {
                OutlinedButton(onClick = { brandMenuOpen = true }) {
                    Text(
                        brandChoice?.label
                            ?: if (brand.label.isBlank()) "自动识别" else "自动识别（${brand.label}）"
                    )
                }
                DropdownMenu(
                    expanded = brandMenuOpen,
                    onDismissRequest = { brandMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (brand.label.isBlank()) "自动识别"
                                else "自动识别（${brand.label}）",
                                color = if (brandChoice == null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                        onClick = {
                            brandChoice = null
                            brandMenuOpen = false
                        }
                    )
                    CardBrand.entries.filter { it != CardBrand.UNKNOWN }.forEach { b ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    b.label,
                                    color = if (brandChoice == b) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            onClick = {
                                brandChoice = b
                                brandMenuOpen = false
                            }
                        )
                    }
                }
            }
            if (brandChoice != null) {
                Text(
                    text = "已手动选择，改卡号后不会再自动更新；选回\"自动识别\"恢复跟随",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            Text(
                text = "卡片类型",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CardType.entries.forEach { t ->
                    if (type == t) {
                        Button(onClick = { type = t }) { Text(t.label) }
                    } else {
                        OutlinedButton(onClick = { type = t }) { Text(t.label) }
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            OutlinedTextField(
                value = holderName,
                onValueChange = { holderName = it },
                label = { Text("持卡人姓名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.size(8.dp))

            OutlinedTextField(
                value = expiry,
                onValueChange = { raw ->
                    // 内部永远存纯数字（最多 4 位：MMYY），显示格式 MM/YY 由 VisualTransformation 处理。
                    // 这样 Compose 自己维护 selection，不会被字符串截断/补字符打乱光标位置。
                    expiry = raw.filter { it.isDigit() }.take(4)
                },
                label = { Text("有效期（信用卡填，格式 MM/YY）") },
                placeholder = { Text("0928") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = ExpiryTransformation
            )

            if (type == CardType.CREDIT) {
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = creditLimit,
                    onValueChange = { creditLimit = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text("信用额度（元，选填）") },
                    placeholder = { Text("例如 50000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
                label = { Text("银行预留手机号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(modifier = Modifier.size(8.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.size(16.dp))

            Text(
                text = "提示：背面那三位安全码（CVV）请不要录入，也别存在任何地方。付款时直接看实体卡。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(20.dp))

            // 底部再来一个保存按钮，表单再长也保证能够到
            Button(
                onClick = { doSave() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存卡片")
            }

            Spacer(modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * 有效期字段的格式转换：用户输入纯数字（MMYY），显示成 MM/YY。
 *
 * 为什么这样设计：之前在 onValueChange 里手动插入斜杠，每次 value 字符串被改动，
 * Compose 都把光标重置到改动位置（不是末尾），结果输入 09/3 后再输入 1 会变成 09/13。
 *
 * 用 VisualTransformation 把"显示文本"和"真实值"分开：
 *   真实值（OutlinedTextField.value）永远是 "0928" 这种纯数字，
 *   显示文本（filter 输出）是 "09/28"。
 * OffsetMapping 负责两者之间的字符位置映射，Compose 用它维护 selection，逻辑天然正确。
 */
private val ExpiryTransformation = VisualTransformation { text ->
    val digits = text.text.filter { it.isDigit() }.take(4)
    val out = if (digits.length > 2) {
        "${digits.substring(0, 2)}/${digits.substring(2)}"
    } else digits
    androidx.compose.ui.text.input.TransformedText(
        text = androidx.compose.ui.text.AnnotatedString(out),
        offsetMapping = object : OffsetMapping {
            // 原始(纯数字)位置 -> 显示(含斜杠)位置
            override fun originalToTransformed(offset: Int): Int =
                if (offset > 2) offset + 1 else offset
            // 显示位置 -> 原始位置
            override fun transformedToOriginal(offset: Int): Int =
                if (offset > 2) offset - 1 else offset
        }
    )
}
