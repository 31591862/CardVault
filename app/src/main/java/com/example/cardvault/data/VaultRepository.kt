package com.example.cardvault.data

import android.content.Context
import com.example.cardvault.model.BankCard
import com.example.cardvault.model.CardBrand
import com.example.cardvault.model.CardType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 本地卡库。整个文件用 AES-256-GCM 加密后存在 App 私有目录：
 * - 密钥在 Android Keystore 里（见 VaultKey），密钥材料不进应用进程
 * - GCM 带完整性校验，文件被改过会直接解密失败
 * - 存在 app 私有目录，其他应用读不到；Manifest 里 allowBackup=false，adb 备份也拖不走
 */
class VaultRepository(context: Context) {

    private val vaultFile = File(context.filesDir, VAULT_FILE)
    private val key = VaultKey.getOrCreate()

    /** 读取全部卡片；文件还不存在就返回空列表 */
    @Synchronized
    fun load(): List<BankCard> {
        if (!vaultFile.exists()) return emptyList()
        val sealed = try {
            vaultFile.readBytes()
        } catch (e: IOException) {
            throw VaultException("卡库读取失败", e)
        }
        val plain = try {
            AesGcm.open(sealed, key)
        } catch (e: Exception) {
            throw VaultException("卡库解密失败，密钥可能已失效", e)
        }
        return parse(plain.toString(Charsets.UTF_8))
    }

    @Synchronized
    fun save(cards: List<BankCard>) {
        val json = JSONObject().apply {
            put("version", SCHEMA_VERSION)
            put("cards", JSONArray().apply { cards.forEach { put(it.toJson()) } })
        }.toString()
        val sealed = AesGcm.seal(json.toByteArray(Charsets.UTF_8), key)
        try {
            val tmp = File(vaultFile.parentFile, "$VAULT_FILE.tmp")
            tmp.writeBytes(sealed)
            // 先写临时文件再改名，避免写一半断电把整个卡库弄坏
            if (vaultFile.exists()) vaultFile.delete()
            tmp.renameTo(vaultFile)
        } catch (e: IOException) {
            throw VaultException("卡库写入失败", e)
        }
    }

    /** 导出用的明文 JSON（调用方负责用密码加密后再落盘） */
    fun toExportJson(cards: List<BankCard>): String =
        JSONObject().apply {
            put("version", SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("cards", JSONArray().apply { cards.forEach { put(it.toJson()) } })
        }.toString()

    fun fromExportJson(text: String): List<BankCard> = parse(text)

    private fun parse(text: String): List<BankCard> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("cards") ?: return emptyList()
        val out = ArrayList<BankCard>(arr.length())
        for (i in 0 until arr.length()) {
            out.add(cardFromJson(arr.getJSONObject(i)))
        }
        return out.sortedByDescending { it.updatedAt }
    }

    companion object {
        private const val VAULT_FILE = "cards.vault"
        private const val SCHEMA_VERSION = 1
    }
}

class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun BankCard.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("bankName", bankName)
    put("nickname", nickname)
    put("cardNumber", cardNumber)
    put("holderName", holderName)
    put("expiry", expiry)
    put("type", type.name)
    brandOverride?.let { put("brandOverride", it.name) }
    if (creditLimit.isNotBlank()) put("creditLimit", creditLimit)
    put("phone", phone)
    put("note", note)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun cardFromJson(o: JSONObject): BankCard = BankCard(
    id = o.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
    bankName = o.optString("bankName"),
    nickname = o.optString("nickname"),
    cardNumber = o.optString("cardNumber"),
    holderName = o.optString("holderName"),
    expiry = o.optString("expiry"),
    type = runCatching { CardType.valueOf(o.optString("type", "DEBIT")) }.getOrDefault(CardType.DEBIT),
    brandOverride = runCatching { CardBrand.valueOf(o.optString("brandOverride")) }.getOrNull()
        ?.takeIf { it != CardBrand.UNKNOWN },
    creditLimit = o.optString("creditLimit"),
    phone = o.optString("phone"),
    note = o.optString("note"),
    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
)
