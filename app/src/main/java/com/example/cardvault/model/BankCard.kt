package com.example.cardvault.model

/**
 * 一张银行卡的记录。
 *
 * 安全约定（不要破坏）：
 * 1. 这里没有任何 CVV / 安全码 / 查询密码 / 支付密码字段。卡组织和 PCI DSS 明文禁止存储 CVV，
 *    任何时候都不要把这类字段加进来。真要付款时请直接看实体卡。
 * 2. cardNumber 只在加密文件里落地，且仅在用户主动点按"显示"时才在界面上完整呈现。
 * 3. 导出备份走用户密码派生的独立密钥，不复用设备 Keystore 密钥（换机后无法解密）。
 */
data class BankCard(
    val id: String = java.util.UUID.randomUUID().toString(),
    val bankName: String = "",
    val nickname: String = "",
    val cardNumber: String = "",
    val holderName: String = "",
    /** 信用卡有效期，格式 MM/yy；借记卡留空 */
    val expiry: String = "",
    val type: CardType = CardType.DEBIT,
    /** 卡组织；null = 未手动选择，按卡号自动识别。识别不出来时展示层兜底"未识别" */
    val brandOverride: CardBrand? = null,
    /** 信用卡额度（元，纯数字字符串）；储蓄卡恒为空串 */
    val creditLimit: String = "",
    /** 银行预留手机号后四位以外的完整号码，可不填 */
    val phone: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class CardType(val label: String) {
    DEBIT("储蓄卡"),
    CREDIT("信用卡")
}

/**
 * 中文按拼音比较的排序器。用系统自带的 ICU（API 24 起内置），不引第三方拼音库。
 * 代价：多音字姓氏按常用音（如"解"按 jiě、"单"按 dān），生僻字各系统版本可能略有差异。
 */
private val zhCollator: java.text.Collator by lazy {
    java.text.Collator.getInstance(java.util.Locale.CHINA)
}

/** 排序用姓名：持卡人为空时用银行名兜底，两者都空则沉到最后 */
private fun BankCard.sortName(): String = holderName.trim().ifBlank { bankName.trim() }

/**
 * 首页排序规则（用户 2026-09-09 确认）：
 * 1) 主排序：持卡人姓名 A-Z，中文按拼音；没名字的沉到最后
 * 2) 次排序：同名时信用卡排在储蓄卡前面
 * 3) 兜底：更新时间新的在前
 *
 * 只在展示层排序，不改存储顺序，导入合并逻辑和数据文件都不受影响。
 */
fun List<BankCard>.sortedForHome(): List<BankCard> = sortedWith(
    compareBy<BankCard> { if (it.sortName().isEmpty()) 1 else 0 }
        .then(Comparator { a, b -> zhCollator.compare(a.sortName(), b.sortName()) })
        .thenByDescending { it.type == CardType.CREDIT }
        .thenByDescending { it.updatedAt }
)

/** 卡组织，按卡号 BIN 推断 */
enum class CardBrand(val label: String) {
    UNIONPAY("银联"),
    VISA("VISA"),
    MASTERCARD("Mastercard"),
    AMEX("American Express"),
    JCB("JCB"),
    DINERS("Diners Club"),
    UNKNOWN("")
}

fun detectBrand(rawNumber: String): CardBrand {
    val n = rawNumber.filter { it.isDigit() }
    if (n.isEmpty()) return CardBrand.UNKNOWN
    return when {
        n.startsWith("4") -> CardBrand.VISA
        n.startsWith("62") -> CardBrand.UNIONPAY
        n.length >= 2 && n.substring(0, 2) in listOf("34", "37") -> CardBrand.AMEX
        n.startsWith("35") -> CardBrand.JCB
        n.length >= 2 && n.substring(0, 2) in listOf("51", "52", "53", "54", "55") -> CardBrand.MASTERCARD
        n.length >= 4 && n.substring(0, 4).toIntOrNull() in 2221..2720 -> CardBrand.MASTERCARD
        n.length >= 2 && n.substring(0, 2) in listOf("30", "36", "38") -> CardBrand.DINERS
        else -> CardBrand.UNKNOWN
    }
}

/**
 * Luhn 校验。用来在录入时提示"卡号可能打错了"。
 * 注意：通过 Luhn 不等于卡号真实存在，只是排除了最常见的手误。
 */
fun luhnValid(rawNumber: String): Boolean {
    val n = rawNumber.filter { it.isDigit() }
    if (n.length < 12) return false
    var sum = 0
    var double = false
    for (i in n.length - 1 downTo 0) {
        var d = n[i] - '0'
        if (double) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
        double = !double
    }
    return sum % 10 == 0
}

/**
 * 默认掩码：只留最后 4 位。
 * 卡号短于 8 位时（比如还没输完）也只留末 4 位，其余打星。
 */
fun maskNumber(rawNumber: String): String {
    val n = rawNumber.filter { it.isDigit() }
    if (n.length <= 4) return n
    val tail = n.takeLast(4)
    val headGroups = (n.length - 4 + 3) / 4
    return List(headGroups) { "****" }.joinToString(" ") + " $tail"
}

/** 4 位一组的展示格式，例如 6225 8801 2345 6789 */
fun formatGroups(rawNumber: String): String =
    rawNumber.filter { it.isDigit() }.chunked(4).joinToString(" ")

/**
 * 有效期展示格式：输入存的是纯数字 MMYY（如 "0928"），展示成 "09/28"。
 * 兼容老数据里可能存的 "09/28" 带斜杠格式：一律取数字位重新格式化。
 */
fun formatExpiry(raw: String): String {
    val d = raw.filter { it.isDigit() }
    return if (d.length > 2) "${d.substring(0, 2)}/${d.substring(2)}" else raw
}

/** 金额展示：纯数字字符串 → "¥50,000"；空/无效返回 "—" */
fun formatCnyAmount(raw: String): String {
    val d = raw.filter { it.isDigit() }
    if (d.isEmpty()) return "—"
    val grouped = d.reversed().chunked(3).joinToString(",").reversed()
    return "¥$grouped"
}

/**
 * 各卡组织常见的卡号位数。只在位数不在集合里时才提示核对——
 * 现实中银联借记卡 16/17/18/19 位都有发行，不能一刀切。
 */
private val commonLengths: Map<CardBrand, Set<Int>> = mapOf(
    CardBrand.UNIONPAY to setOf(16, 17, 18, 19),
    CardBrand.VISA to setOf(13, 16, 19),
    CardBrand.MASTERCARD to setOf(16, 17, 18, 19),
    CardBrand.AMEX to setOf(15),
    CardBrand.JCB to setOf(16, 17, 18, 19),
    CardBrand.DINERS to setOf(14)
)

/** 卡号位数合理性：位数在常见范围内就不提示；卡组织没识别出来也不多嘴 */
fun numberLengthHint(rawNumber: String): String? {
    val len = rawNumber.filter { it.isDigit() }.length
    if (len == 0) return null
    val common = commonLengths[detectBrand(rawNumber)] ?: return null
    if (len in common) return null
    return "常见为 ${common.sorted().joinToString("、")} 位，当前 $len 位，请核对"
}
