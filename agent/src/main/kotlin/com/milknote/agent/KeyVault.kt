package com.milknote.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Key 保管(§3.6):Android Keystore AES-256-GCM 信封加密,密文落
 * filesDir/agent/keys.json(不在 .milk 目录 → 笔记本备份不携带 Key)。
 * 明文仅两处出现:设置页输入框(尾 4 位)与请求头 Bearer。
 */
class KeyVault(context: Context) {

    private val file: File = File(context.filesDir, "agent/keys.json")
    private val rng = SecureRandom()

    private val masterKey: SecretKey by lazy { getOrCreateMaster() }

    /** 取主密钥;若为旧版生成(禁 caller IV)则删除重建 —— Keystore 键跨安装存续 */
    private fun getOrCreateMaster(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { existing ->
            // 旧键(未 setRandomizedEncryptionRequired(false))会在 GCM+自管 IV 时抛
            // Caller-provided IV not permitted(AVD 实证);探测后删除重建
            try {
                Cipher.getInstance(TRANSFORM).init(Cipher.ENCRYPT_MODE, existing, GCMParameterSpec(128, ByteArray(12)))
                return existing
            } catch (_: Exception) {
                runCatching { ks.deleteEntry(KEY_ALIAS) }
            }
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 信封加密由本类自管随机 IV(GCM),Keystore 侧允许 caller-provided IV
                .setRandomizedEncryptionRequired(false)
                .build(),
        )
        return kg.generateKey()
    }

    @Synchronized
    fun put(alias: String, plainKey: String) {
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
        }
        val ct = cipher.doFinal(plainKey.toByteArray(Charsets.UTF_8))
        val map = loadMap().toMutableMap()
        map[alias] = Entry(Base64.encodeToString(iv, Base64.NO_WRAP), Base64.encodeToString(ct, Base64.NO_WRAP))
        saveMap(map)
    }

    @Synchronized
    fun get(alias: String): String? {
        val e = loadMap()[alias] ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, Base64.decode(e.iv, Base64.NO_WRAP)))
            }
            String(cipher.doFinal(Base64.decode(e.ct, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    @Synchronized
    fun remove(alias: String) {
        val map = loadMap().toMutableMap()
        map.remove(alias)
        saveMap(map)
    }

    /** 设置页显示:仅尾 4 位 */
    fun masked(alias: String): String? {
        val k = get(alias) ?: return null
        return if (k.length <= 4) "••••" else "••••" + k.takeLast(4)
    }

    private fun loadMap(): Map<String, Entry> = runCatching {
        if (!file.exists()) return emptyMap()
        val obj = JSONObject(file.readText())
        val out = HashMap<String, Entry>()
        for (key in obj.keys()) {
            val e = obj.getJSONObject(key)
            out[key] = Entry(e.getString("iv"), e.getString("ct"))
        }
        out
    }.getOrDefault(emptyMap())

    private fun saveMap(map: Map<String, Entry>) {
        file.parentFile?.mkdirs()
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, JSONObject().put("iv", v.iv).put("ct", v.ct)) }
        val tmp = File(file.parentFile, "keys.json.tmp")
        tmp.writeText(obj.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "KeyVault save failed" }
        }
    }

    private data class Entry(val iv: String, val ct: String)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "milknote_agent_master"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
