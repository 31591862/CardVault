package com.example.cardvault.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val IV_LEN = 12
private const val TAG_BITS = 128

/**
 * 本机卡库的主密钥。存在 Android Keystore 里，密钥材料不进应用进程、也不落盘。
 * 有安全芯片的机器（现在绝大部分）会用硬件保护，取出来的只是一个句柄。
 *
 * 后果要知道：App 卸载或清除数据，Keystore 里的密钥会一起没掉，
 * 原来的卡库文件就再也解不开了 —— 所以换机/重装前必须先走"导出加密备份"。
 */
object VaultKey {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "cardvault_vault_key"

    @Synchronized
    fun getOrCreate(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }
}

/** AES-256-GCM 的封箱/开箱。GCM 自带完整性校验，密文被改过会直接解密失败。 */
object AesGcm {

    /** 输出格式：iv(12B) + 密文
     *
     * 注意：Android Keystore（9.0+）不允许调用者提供 IV，必须让 Cipher 自己生成，
     * 然后从 cipher.iv 拿出来拼到密文前。如果硬塞 IV 会抛 "Caller-provided IV not permitted"。
     */
    fun seal(plain: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = checkNotNull(cipher.iv) { "Provider 没有生成 IV，GCM 模式异常" }
        val body = cipher.doFinal(plain)
        return iv + body
    }

    fun open(payload: ByteArray, key: SecretKey): ByteArray {
        if (payload.size <= IV_LEN) throw BackupFormatException("密文长度不对")
        val iv = payload.copyOfRange(0, IV_LEN)
        val body = payload.copyOfRange(IV_LEN, payload.size)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                doFinal(body)
            }
        } catch (e: Exception) {
            throw BackupFormatException("解密失败，卡库可能已损坏", e)
        }
    }
}

/**
 * 备份文件的加解密。
 *
 * 为什么不直接用 VaultKey 加密备份？因为 Keystore 密钥跟设备绑定、不可导出，
 * 用它加密的文件换台手机永远解不开。所以导出走独立路径：
 * 用户自设密码 → PBKDF2 派生密钥 → AES-256-GCM。
 *
 * 文件格式： "CVBK"(4B) | version(1B) | salt(16B) | iv(12B) | ciphertext(NB)
 */
object BackupCrypto {

    private val MAGIC = "CVBK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_LEN = 16
    private const val HEADER_LEN = 4 + 1 + SALT_LEN + IV_LEN

    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val body = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            doFinal(plaintext)
        }
        return MAGIC + byteArrayOf(VERSION) + salt + iv + body
    }

    fun decrypt(payload: ByteArray, password: String): ByteArray {
        if (payload.size <= HEADER_LEN) throw BackupFormatException("文件太短，不是有效的备份")
        if (!payload.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw BackupFormatException("这不是 CardVault 的备份文件")
        }
        if (payload[4] != VERSION) throw BackupFormatException("备份版本不支持：v${payload[4]}")
        val salt = payload.copyOfRange(5, 5 + SALT_LEN)
        val iv = payload.copyOfRange(5 + SALT_LEN, 5 + SALT_LEN + IV_LEN)
        val body = payload.copyOfRange(HEADER_LEN, payload.size)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
                doFinal(body)
            }
        } catch (e: javax.crypto.AEADBadTagException) {
            throw BackupFormatException("密码错误，或文件已损坏", e)
        } catch (e: Exception) {
            throw BackupFormatException("解密失败：${e.message}", e)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
