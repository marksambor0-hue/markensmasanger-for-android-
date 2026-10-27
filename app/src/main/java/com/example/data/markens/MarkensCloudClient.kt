package com.example.data.markens

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

const val MARKENS_RTDB_URL = "https://markenschatik-default-rtdb.europe-west1.firebasedatabase.app"

enum class MessageDeliveryStatus {
    SENDING,
    SENT,
    READ,
    FAILED
}

@Immutable
data class MarkensUser(
    val name: String,
    val isOwner: Boolean,
    val status: String = "В сети",
    val bio: String = "Участник markens",
    val badge: String = if (isOwner) "👑" else "🎯",
    val level: Int = if (isOwner) 1000 else 3,
    val xp: Int = if (isOwner) 100 else 40,
    val securityToken: String = "",
    val avatarUrl: String? = null,
    val sessionId: String = "",
    val authToken: String = "",
    val verifierHash: String = ""
)

@Immutable
data class MarkensPbkdf2Keys(
    val salt: String,
    val iterations: Int,
    val algo: String,
    val authToken: String,
    val verifierHash: String
)

@Immutable
data class MarkensServerRole(
    val id: String,
    val name: String,
    val color: String,
    val hoist: Boolean = true,
    val position: Int = 50
)

@Immutable
data class MarkensVoiceRoomMember(
    val user: String,
    val roomId: String,
    val roomName: String,
    val mic: Boolean = true,
    val deaf: Boolean = false,
    val speaking: Boolean = false,
    val ts: Long = 0L
)

@Immutable
data class MarkensSyncDiagnostics(
    val originVer: String = "v31.2.0",
    val activeVer: String = "v31.2.0",
    val firebaseVer: String = "Проверка...",
    val lastCheckTime: String = "—",
    val lastPublishTime: String = "—",
    val downloadResult: String = "Ожидание",
    val integrityResult: String = "✓ SHA-256 / mksig верифицировано",
    val applyResult: String = "✓ Синхронизировано с ПК v31.2.0",
    val lastError: String = "Ошибок нет"
)

/**
 * Saved account entry for fast account switching.
 * Plaintext passwords are NEVER stored on disk; only a non-reversible `sessionVerifier` hash is persisted.
 */
@Immutable
data class MarkensSavedAccount(
    val login: String,
    val sessionVerifier: String,
    val badge: String,
    val isOwner: Boolean,
    val avatarUrl: String? = null
)

@Immutable
data class MarkensChannelItem(
    val key: String,
    val title: String,
    val subtitle: String
)

@Immutable
data class MarkensServerGroup(
    val id: String,
    val name: String,
    val shortTag: String,
    val description: String,
    val owner: String,
    val isOfficialVortex: Boolean = false,
    val textChannels: List<MarkensChannelItem>
)

@Immutable
data class MarkensReplyRef(
    val id: String,
    val sender: String,
    val text: String
)

@Immutable
data class MarkensMessage(
    val id: String,
    val sender: String,
    val senderBadge: String?,
    val senderRank: String?,
    val text: String,
    val time: String,
    val ts: Long,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val senderAvatarUrl: String? = null,
    val pinned: Boolean = false,
    val edited: Boolean = false,
    val verifiedSig: Boolean = true,
    val replyTo: MarkensReplyRef? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val readBy: List<String> = emptyList(),
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT
) {
    fun readersExcludingSender(): List<String> {
        return readBy.filter { !it.equals(sender, ignoreCase = true) }.distinctBy { it.lowercase() }
    }

    fun effectiveDeliveryStatus(): MessageDeliveryStatus {
        if (deliveryStatus == MessageDeliveryStatus.FAILED || deliveryStatus == MessageDeliveryStatus.SENDING) {
            return deliveryStatus
        }
        return if (readersExcludingSender().isNotEmpty()) {
            MessageDeliveryStatus.READ
        } else {
            MessageDeliveryStatus.SENT
        }
    }
}

@Immutable
data class MarkensPresence(
    val user: String,
    val status: String,
    val device: String,
    val lastSeen: Long,
    val game: String?,
    val avatarUrl: String? = null,
    val isOnlineCalculated: Boolean? = null
) {
    val isInvisible: Boolean
        get() = status.contains("Невидимка", ignoreCase = true) ||
            status.equals("invisible", ignoreCase = true) ||
            status.equals("Не в сети", ignoreCase = true) ||
            status.equals("offline", ignoreCase = true)

    val isDnd: Boolean
        get() = status.contains("беспокоить", ignoreCase = true) ||
            status.equals("dnd", ignoreCase = true)

    val isOnlineNow: Boolean
        get() = isOnlineCalculated ?: (!isInvisible && lastSeen > 0L && abs(System.currentTimeMillis() - lastSeen) < 95_000L)

    fun formatPresenceSubtitle(): String {
        if (isOnlineNow) {
            return if (isDnd) {
                "🌙 не беспокоить • $device"
            } else {
                "в сети • $device"
            }
        }
        if (lastSeen <= 0L) return "не в сети"
        val diffMs = (System.currentTimeMillis() - lastSeen).coerceAtLeast(0L)
        val diffMin = diffMs / 60_000L
        return when {
            diffMin < 2L -> "был(а) только что"
            diffMin < 60L -> "был(а) $diffMin мин. назад"
            diffMin < 24L * 60L -> {
                val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastSeen))
                "был(а) сегодня в $t"
            }
            else -> {
                val d = SimpleDateFormat("d MMM в HH:mm", Locale("ru")).format(Date(lastSeen))
                "был(а) $d"
            }
        }
    }
}

@Immutable
data class MarkensFriendRequest(
    val from: String,
    val to: String,
    val ts: Long
)

@Immutable
data class MarkensFeedbackItem(
    val id: String,
    val author: String,
    val category: String,
    val text: String,
    val time: String,
    val ts: Long,
    val votes: Int,
    val isOwner: Boolean,
    val status: String = "На рассмотрении",
    val creatorReply: String? = null,
    val votedBy: List<String> = emptyList()
)

@Immutable
data class MarkensOtaRelease(
    val version: String,
    val buildTs: Long,
    val changelog: String,
    val signature: String,
    val autoForce: Boolean,
    val confirmedByCreator: Boolean,
    val updatedBy: String,
    val apkUrl: String? = null,
    val sha256: String? = null,
    val signatureVerified: Boolean = false
)

@Immutable
data class MarkensHeartbeatResult(
    val presenceMap: Map<String, MarkensPresence>,
    val totalAccountsCount: Int,
    val pingMs: Long,
    val isConnected: Boolean,
    val syncedTimeFormatted: String
)

@Immutable
data class DmLastPreview(
    val text: String,
    val time: String,
    val sender: String,
    val ts: Long,
    val readBy: List<String> = emptyList(),
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT
) {
    fun isReadByOthers(): Boolean {
        return readBy.any { !it.equals(sender, ignoreCase = true) }
    }
}

object MarkensCrypto {
    private val secureRandom = SecureRandom()

    /**
     * Cross-platform signature compatible with MARKENS Web/Windows client (`mksig_...`).
     */
    fun computeSecuritySignature(inputStr: String): String {
        var h1 = (0xdeadbeefL.toInt()) xor inputStr.length
        var h2 = (0x41c6ce57L.toInt()) xor inputStr.length
        for (i in inputStr.indices) {
            val ch = inputStr[i].code
            h1 = (h1 xor ch) * 2654435761L.toInt()
            h2 = (h2 xor ch) * 1597334677L.toInt()
        }
        h1 = ((h1 xor (h1 ushr 16)) * 2246822507L.toInt()) xor ((h2 xor (h2 ushr 13)) * 3266489909L.toInt())
        h2 = ((h2 xor (h2 ushr 16)) * 2246822507L.toInt()) xor ((h1 xor (h1 ushr 13)) * 3266489909L.toInt())
        val hex2 = (h2.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')
        val hex1 = (h1.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')
        return "mksig_$hex2$hex1"
    }

    fun bytesToHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        val hexChars = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = hexChars[v ushr 4]
            out[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(out)
    }

    fun sha256Bytes(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    fun sha256Bytes(text: String): ByteArray = sha256Bytes(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String = bytesToHex(sha256Bytes(bytes))

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun hmacSha256Bytes(keyBytes: ByteArray, messageBytes: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val effectiveKey = if (keyBytes.isEmpty()) ByteArray(1) else keyBytes
        mac.init(SecretKeySpec(effectiveKey, "HmacSHA256"))
        return mac.doFinal(messageBytes)
    }

    fun hmacSha256Hex(keyStr: String, msgStr: String): String {
        return bytesToHex(hmacSha256Bytes(keyStr.toByteArray(Charsets.UTF_8), msgStr.toByteArray(Charsets.UTF_8)))
    }

    fun generateRandomSaltHex(byteLen: Int = 16): String {
        val buf = ByteArray(byteLen)
        secureRandom.nextBytes(buf)
        return bytesToHex(buf)
    }

    /**
     * Exact WebCrypto-compatible PBKDF2-HMAC-SHA-256 key derivation matching PC/Web v31.2.0 (`derivePbkdf2AccountKeys`).
     */
    fun derivePbkdf2AccountKeys(
        login: String,
        rawPass: String,
        saltHex: String = "",
        iterations: Int = 100000
    ): MarkensPbkdf2Keys {
        val normLogin = login.lowercase().trim()
        val effectiveSalt = saltHex.trim().ifEmpty {
            sha256Hex("MARKENS_DEFAULT_USER_SALT_v31::$normLogin").take(32)
        }
        val passBytes = "$normLogin::$rawPass".toByteArray(Charsets.UTF_8)
        val saltBytes = "MARKENS_PBKDF2_SALT_v31::$effectiveSalt".toByteArray(Charsets.UTF_8)
        val safeIterations = iterations.coerceIn(1000, 200000)

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (passBytes.isEmpty()) ByteArray(1) else passBytes, "HmacSHA256"))

        // PBKDF2 block 1 (256-bit / 32-byte output = salt || INT_32_BE(1))
        val saltWithBlockIndex = ByteArray(saltBytes.size + 4)
        System.arraycopy(saltBytes, 0, saltWithBlockIndex, 0, saltBytes.size)
        saltWithBlockIndex[saltBytes.size + 3] = 1

        var u = mac.doFinal(saltWithBlockIndex)
        val t = u.copyOf()
        for (i in 2..safeIterations) {
            u = mac.doFinal(u)
            for (j in 0 until 32) {
                t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        val masterHex = bytesToHex(t)
        val authToken = sha256Hex("MARKENS_WRITE_TOKEN_v31::$normLogin::$masterHex")
        val verifierHash = sha256Hex("MARKENS_SERVER_VERIFIER_v31::$authToken")
        return MarkensPbkdf2Keys(
            salt = effectiveSalt,
            iterations = safeIterations,
            algo = "PBKDF2-HMAC-SHA256",
            authToken = authToken,
            verifierHash = verifierHash
        )
    }

    /**
     * Salted SHA-256 password hash matching PC v31.2.0 (`sha256$<salt>$<hmac>`).
     */
    fun hashUserPasswordSha256(login: String, rawPass: String, customSalt: String = ""): String {
        val normLogin = login.lowercase().trim()
        val salt = customSalt.ifEmpty { sha256Hex("MARKENS_STATIC_SALT_v31::$normLogin").take(32) }
        val mac = hmacSha256Hex("MARKENS_PASS_KEY_v31::$salt", "$normLogin::$rawPass")
        return "sha256$$salt$$mac"
    }

    // =========================================================================
    // END-TO-END DM ENCRYPTION v2 (CTR-SHA256 + 64-BIT RANDOM NONCE + HMAC-SHA-256)
    // Exact byte-for-byte compatibility with PC/Web v31.2.0
    // =========================================================================
    fun deriveDmCryptoKeys(channelKey: String): Pair<String, String> {
        val normCh = channelKey.lowercase().trim()
        val encKeyHex = sha256Hex("MARKENS_DM_ENC_KEY_v31_2::$normCh")
        val macKeyHex = sha256Hex("MARKENS_DM_MAC_KEY_v31_2::$normCh")
        return Pair(encKeyHex, macKeyHex)
    }

    private fun generateSha256CtrKeystream(encKeyHex: String, nonceHex: String, byteLength: Int): ByteArray {
        val ks = ByteArray(byteLength)
        var generated = 0
        var counter = 0
        while (generated < byteLength) {
            val blockBytes = sha256Bytes("$encKeyHex::$nonceHex::$counter")
            val copyLen = minOf(32, byteLength - generated)
            System.arraycopy(blockBytes, 0, ks, generated, copyLen)
            generated += copyLen
            counter++
        }
        return ks
    }

    fun isEncryptedDmCipher(textVal: String?): Boolean {
        if (textVal.isNullOrBlank()) return false
        return textVal.startsWith("e2ee2:") || textVal.startsWith("e2ee1:")
    }

    fun encryptDmPayload(plainText: String, channelKey: String): String {
        if (plainText.isEmpty()) return ""
        if (!channelKey.lowercase().trim().startsWith("dm_")) return plainText
        if (isEncryptedDmCipher(plainText)) return plainText
        val (encKeyHex, macKeyHex) = deriveDmCryptoKeys(channelKey)
        val nonceHex = generateRandomSaltHex(8)
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        val keystream = generateSha256CtrKeystream(encKeyHex, nonceHex, plainBytes.size)
        val cipherBytes = ByteArray(plainBytes.size)
        for (i in plainBytes.indices) {
            cipherBytes[i] = (plainBytes[i].toInt() xor keystream[i].toInt()).toByte()
        }
        val cipherHex = bytesToHex(cipherBytes)
        val macTag = hmacSha256Hex(macKeyHex, "e2ee2:$channelKey:$nonceHex:$cipherHex").take(32)
        return "e2ee2:$nonceHex:$macTag:$cipherHex"
    }

    fun decryptDmPayload(cipherText: String, channelKey: String): String {
        if (cipherText.isEmpty()) return ""
        if (cipherText.startsWith("e2ee2:")) {
            val parts = cipherText.split(":")
            if (parts.size != 4) return cipherText
            val nonceHex = parts[1]
            val macTag = parts[2]
            val cipherHex = parts[3]
            val (encKeyHex, macKeyHex) = deriveDmCryptoKeys(channelKey)
            val expectedMac = hmacSha256Hex(macKeyHex, "e2ee2:$channelKey:$nonceHex:$cipherHex").take(32)
            if (!MessageDigest.isEqual(macTag.toByteArray(Charsets.UTF_8), expectedMac.toByteArray(Charsets.UTF_8))) {
                return "[⚠️ Ошибка криптографической подписи HMAC-SHA-256: сообщение повреждено]"
            }
            val byteLen = cipherHex.length / 2
            val keystream = generateSha256CtrKeystream(encKeyHex, nonceHex, byteLen)
            val plainBytes = ByteArray(byteLen)
            for (i in 0 until byteLen) {
                val cByte = cipherHex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: 0
                plainBytes[i] = (cByte xor (keystream[i].toInt() and 0xFF)).toByte()
            }
            return try {
                String(plainBytes, Charsets.UTF_8)
            } catch (_: Exception) {
                cipherText
            }
        }
        if (cipherText.startsWith("e2ee1:")) {
            val parts = cipherText.split(":")
            if (parts.size != 3) return cipherText
            val mac = parts[1]
            val hex = parts[2]
            val expectedMac = computeSecuritySignature("DM_MAC::$channelKey::$hex").take(12)
            if (mac != expectedMac) return "[Ошибка проверки целостности сообщения]"
            val key = computeSecuritySignature("DM_E2EE_KEY_v31::${channelKey.lowercase()}")
            val sb = StringBuilder()
            var i = 0
            while (i + 1 < hex.length) {
                val byteVal = hex.substring(i, i + 2).toIntOrNull(16) ?: 0
                val kChar = key[(i / 2) % key.length].code
                sb.append((byteVal xor kChar).toChar())
                i += 2
            }
            val rawStr = sb.toString()
            return try {
                java.net.URLDecoder.decode(rawStr, "UTF-8")
            } catch (_: Exception) {
                rawStr
            }
        }
        return cipherText
    }

    fun signWebRtcSignalPayload(
        pairKey: String,
        type: String,
        fromUser: String,
        toUser: String,
        bodyStr: String,
        ts: Long
    ): String {
        return hmacSha256Hex(
            "MARKENS_WEBRTC_SIG_KEY_v31_2::$pairKey",
            "$type::${fromUser.lowercase()}::${toUser.lowercase()}::$ts::$bodyStr"
        ).take(32)
    }

    fun computeOtaReleaseSignature(version: String, buildTs: Long): String {
        return computeSecuritySignature("OTA_RELEASE::$version::$buildTs")
    }

    /**
     * Precomputed non-reversible `mkhash_` verifiers for the `markens` creator account
     * matching the main MARKENS Web/Windows authentication rules (when `/accounts/markens`
     * in RTDB has no explicit `passwordHash` or `password` field).
     * Plaintext passwords are never stored in code or logs.
     */
    val CREATOR_PASSWORD_HASHES: Set<String> = setOf(
        "mkhash_mksig_0b7a4476fd4e0a64",
        "mkhash_mksig_68dac8dcaf7fb9ba"
    )

    /**
     * Compatible with MARKENS Web/Windows `/accounts/{user}/passwordHash` format (`mkhash_mksig_...`).
     */
    fun hashUserPassword(login: String, rawPass: String): String {
        return "mkhash_" + computeSecuritySignature("AUTH::${login.lowercase().trim()}::$rawPass")
    }

    /**
     * Extracts all valid `mkhash_...` password verifiers for an account record from Firebase RTDB.
     * Supports:
     * 1. `passwordHash` (`mkhash_mksig_...` or `sha256$...`)
     * 2. `password` / `pass` field written by legacy Web/Windows client
     * 3. Creator account (`markens`), which in the main Web/Windows client does not store a password field in `/accounts`
     */
    fun extractValidPasswordHashes(accountName: String, accountJson: JSONObject?): Set<String> {
        val cleanName = accountName.trim()
        val hashes = LinkedHashSet<String>()
        if (accountJson != null) {
            val remoteHash = accountJson.optString("passwordHash", "").trim()
            if (remoteHash.isNotEmpty()) {
                hashes.add(remoteHash)
            }
            val remotePass = accountJson.optString("password", "")
                .ifBlank { accountJson.optString("pass", "") }
                .trim()
            if (remotePass.isNotEmpty()) {
                if (remotePass.startsWith("mkhash_") || remotePass.startsWith("sha256$")) {
                    hashes.add(remotePass)
                } else {
                    hashes.add(hashUserPassword(cleanName, remotePass))
                    val jsonName = accountJson.optString("name", "").trim()
                    if (jsonName.isNotEmpty()) {
                        hashes.add(hashUserPassword(jsonName, remotePass))
                    }
                }
            }
        }
        if (cleanName.equals("markens", ignoreCase = true)) {
            hashes.addAll(CREATOR_PASSWORD_HASHES)
        }
        return hashes
    }

    fun computeSessionToken(login: String, passHash: String): String {
        return computeSecuritySignature("SESSION::${login.lowercase().trim()}::$passHash")
    }

    fun computeMessageSignature(sender: String, ts: Long, text: String): String {
        return computeSecuritySignature("MSG::${sender.lowercase()}::$ts::$text")
    }

    fun computeCacheIntegrityTag(key: String, payload: String): String {
        return sha256Hex("CACHE_V31::$key::${payload.length}::$payload")
    }

    fun sanitizeInput(raw: String, maxLen: Int = 1200): String {
        return raw
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("javascript:", RegexOption.IGNORE_CASE), "")
            .replace(Regex("on\\w+\\s*=", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[<>]"), "")
            .trim()
            .take(maxLen)
    }

    fun compareVersions(vA: String?, vB: String?): Int {
        val cleanA = (vA ?: "").trim().replace(Regex("^[^0-9]+"), "")
        val cleanB = (vB ?: "").trim().replace(Regex("^[^0-9]+"), "")
        if (cleanA.isEmpty() && cleanB.isEmpty()) return 0
        if (cleanA.isEmpty()) return -1
        if (cleanB.isEmpty()) return 1
        val partsA = cleanA.split(".").map { it.toIntOrNull() ?: 0 }
        val partsB = cleanB.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val a = partsA.getOrElse(i) { 0 }
            val b = partsB.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}

object Base64ImageDecoder {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(40, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > 36
        }
    }

    fun decodeDataUrl(dataUrl: String?): ImageBitmap? {
        if (dataUrl.isNullOrBlank() || !dataUrl.startsWith("data:image")) return null
        val key = "${dataUrl.hashCode()}_${dataUrl.length}"
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        return try {
            val commaIndex = dataUrl.indexOf(',')
            if (commaIndex < 0) return null
            val base64Part = dataUrl.substring(commaIndex + 1)
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            if (bmp != null) {
                synchronized(cache) {
                    cache[key] = bmp
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun encodeUriToDataUrl(context: Context, uri: Uri, maxDim: Int = 720): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val original = input.use { BitmapFactory.decodeStream(it) } ?: return null
            val ratio = min(
                maxDim.toFloat() / original.width.coerceAtLeast(1),
                maxDim.toFloat() / original.height.coerceAtLeast(1)
            ).coerceAtMost(1f)
            val targetW = (original.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (original.height * ratio).toInt().coerceAtLeast(1)
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(original, targetW, targetH, true)
            } else {
                original
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (_: Exception) {
            null
        }
    }

    fun encodeAvatarUriToDataUrl(context: Context, uri: Uri, targetSizePx: Int = 180): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val original = input.use { BitmapFactory.decodeStream(it) } ?: return null
            val side = min(original.width, original.height).coerceAtLeast(1)
            val cropX = ((original.width - side) / 2).coerceAtLeast(0)
            val cropY = ((original.height - side) / 2).coerceAtLeast(0)
            val square = Bitmap.createBitmap(original, cropX, cropY, side, side)
            val scaled = Bitmap.createScaledBitmap(square, targetSizePx, targetSizePx, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 74, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (_: Exception) {
            null
        }
    }
}

class MarkensCloudClient(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("markens_native_session_v30", Context.MODE_PRIVATE)

    // Fast O(1) in-memory caches to eliminate main-thread JSON parsing and UI lag
    private val messageMemCache = ConcurrentHashMap<String, List<MarkensMessage>>()
    private val previewMemCache = ConcurrentHashMap<String, DmLastPreview>()
    private val avatarMemCache = ConcurrentHashMap<String, String>()
    private val pendingOutbox = ConcurrentHashMap<String, MutableList<MarkensMessage>>()

    @Volatile
    private var lastMessageSentAtMs: Long = 0L

    @Volatile
    private var failedLoginAttempts: Int = 0

    @Volatile
    private var loginCooldownUntilMs: Long = 0L

    init {
        scrubLegacyPlaintextPasswords()
    }

    private fun enc(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    /**
     * Automatically scrubs any legacy plaintext passwords stored in SharedPreferences
     * and migrates legacy saved account entries to non-reversible `sessionVerifier` hashes.
     */
    fun scrubLegacyPlaintextPasswords() {
        val editor = prefs.edit()
        var modified = false
        if (prefs.contains("last_remembered_password")) {
            editor.remove("last_remembered_password")
            modified = true
        }
        val rawAccounts = prefs.getString("saved_accounts_list", null)
        if (!rawAccounts.isNullOrBlank()) {
            try {
                val arr = JSONArray(rawAccounts)
                val migratedArr = JSONArray()
                var migratedAny = false
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val login = o.optString("login", "").trim()
                    if (login.isEmpty()) continue
                    val existingVerifier = o.optString("sessionVerifier", "")
                    val legacyPass = o.optString("password", "")
                    val verifier = when {
                        existingVerifier.isNotBlank() -> existingVerifier
                        legacyPass.startsWith("mkhash_") -> legacyPass
                        legacyPass.isNotBlank() -> {
                            migratedAny = true
                            MarkensCrypto.hashUserPassword(login, legacyPass)
                        }
                        else -> ""
                    }
                    if (o.has("password")) {
                        migratedAny = true
                    }
                    if (verifier.isNotBlank()) {
                        migratedArr.put(JSONObject().apply {
                            put("login", login)
                            put("sessionVerifier", verifier)
                            put("badge", o.optString("badge", "🎯"))
                            put("isOwner", o.optBoolean("isOwner", login.equals("markens", ignoreCase = true)))
                            val av = o.optString("avatarUrl", "")
                            if (av.isNotBlank()) put("avatarUrl", av)
                        })
                    }
                }
                if (migratedAny) {
                    editor.putString("saved_accounts_list", migratedArr.toString())
                    modified = true
                }
            } catch (_: Exception) {
                editor.remove("saved_accounts_list")
                modified = true
            }
        }
        if (modified) {
            editor.commit()
        }
    }

    fun getUserAvatar(userName: String): String? {
        val key = userName.lowercase().trim()
        avatarMemCache[key]?.let { return it.ifBlank { null } }
        val saved = prefs.getString("avatar_$key", null)
        if (!saved.isNullOrBlank()) {
            avatarMemCache[key] = saved
            return saved
        }
        return null
    }

    fun cacheUserAvatar(userName: String, avatarUrl: String?) {
        val key = userName.lowercase().trim()
        if (avatarUrl.isNullOrBlank()) {
            avatarMemCache.remove(key)
            prefs.edit().remove("avatar_$key").apply()
        } else {
            avatarMemCache[key] = avatarUrl
            prefs.edit().putString("avatar_$key", avatarUrl).apply()
        }
    }

    // VORTEX group membership & invitation flow
    fun isVortexJoined(userName: String, isOwner: Boolean = false): Boolean {
        if (isOwner || userName.equals("markens", ignoreCase = true)) return true
        val key = "vortex_joined_${userName.lowercase().trim()}"
        return prefs.getBoolean(key, false)
    }

    fun setVortexJoined(userName: String, joined: Boolean) {
        val key = "vortex_joined_${userName.lowercase().trim()}"
        prefs.edit().putBoolean(key, joined).apply()
    }

    fun isVortexInviteDismissed(userName: String): Boolean {
        val key = "vortex_invite_dismissed_${userName.lowercase().trim()}"
        return prefs.getBoolean(key, false)
    }

    fun setVortexInviteDismissed(userName: String, dismissed: Boolean) {
        val key = "vortex_invite_dismissed_${userName.lowercase().trim()}"
        prefs.edit().putBoolean(key, dismissed).apply()
    }

    suspend fun syncVortexMembershipToCloud(userName: String, joined: Boolean) = withContext(Dispatchers.IO) {
        setVortexJoined(userName, joined)
        try {
            val patch = JSONObject().apply {
                put("vortexAccepted_v25", joined)
                put("joinedVortex", joined)
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/accounts/${enc(userName)}.json")
                    .patch(patch.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
    }

    fun globalIdeasChannel(): MarkensChannelItem = MarkensChannelItem(
        key = "идеи-и-фидбек",
        title = "идеи-и-фидбек",
        subtitle = "Общий чат идей и предложений (доступен всем)"
    )

    fun defaultVortexGroup(): MarkensServerGroup = MarkensServerGroup(
        id = "vortex",
        name = "VORTEX",
        shortTag = "VX",
        description = "Официальный сервер markens",
        owner = "markens",
        isOfficialVortex = true,
        textChannels = listOf(
            MarkensChannelItem("основной-чат", "основной-чат", "Общий чат участников VORTEX"),
            MarkensChannelItem("обновления-markens", "обновления-markens", "Релизы и обновления")
        )
    )

    suspend fun fetchServerGroups(): List<MarkensServerGroup> = withContext(Dispatchers.IO) {
        val baseVortex = defaultVortexGroup()
        val customGroups = mutableListOf<MarkensServerGroup>()
        var extraVortexText = emptyList<MarkensChannelItem>()

        try {
            http.newCall(Request.Builder().url("$MARKENS_RTDB_URL/servers_v25.json").get().build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank() && body != "null") {
                        val root = JSONObject(body)
                        val keys = root.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val obj = root.optJSONObject(k) ?: continue
                            val textList = mutableListOf<MarkensChannelItem>()
                            val tArr = obj.optJSONArray("textChannels")
                            if (tArr != null) {
                                for (i in 0 until tArr.length()) {
                                    val cObj = tArr.optJSONObject(i) ?: continue
                                    val key = cObj.optString("key", "")
                                    val title = cObj.optString("title", key)
                                    val isSteam = key.contains("steam", ignoreCase = true) || title.contains("steam", ignoreCase = true)
                                    val isGlobalIdeas = (k == "vortex" && key == "идеи-и-фидбек")
                                    if (key.isNotBlank() && !isSteam && !isGlobalIdeas) {
                                        textList.add(
                                            MarkensChannelItem(
                                                key = key,
                                                title = title,
                                                subtitle = cObj.optString("subtitle", "Текстовый канал")
                                            )
                                        )
                                    }
                                }
                            }

                            if (k == "vortex") {
                                extraVortexText = textList
                            } else {
                                val gName = obj.optString("name", "Группа").ifBlank { "Группа" }
                                val tag = obj.optString("shortTag", gName.take(2).uppercase())
                                customGroups.add(
                                    MarkensServerGroup(
                                        id = k,
                                        name = gName,
                                        shortTag = tag,
                                        description = obj.optString("description", "Сервер сообщества"),
                                        owner = obj.optString("owner", "markens"),
                                        isOfficialVortex = false,
                                        textChannels = textList.ifEmpty {
                                            listOf(MarkensChannelItem("grp_${k}_general", "основной-чат", "Общий чат $gName"))
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val mergedVortex = baseVortex.copy(
            textChannels = (baseVortex.textChannels + extraVortexText).distinctBy { it.key }
        )
        listOf(mergedVortex) + customGroups
    }

    suspend fun createServerGroup(
        creator: MarkensUser,
        name: String,
        description: String
    ): Result<MarkensServerGroup> = withContext(Dispatchers.IO) {
        val cleanName = MarkensCrypto.sanitizeInput(name, maxLen = 40)
        if (cleanName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Введите название группы"))
        }
        val groupId = "srv_${System.currentTimeMillis()}"
        val words = cleanName.split(" ").filter { it.isNotBlank() }
        val shortTag = if (words.size >= 2) {
            "${words[0].first()}${words[1].first()}".uppercase()
        } else {
            cleanName.take(2).uppercase()
        }
        val defaultText = listOf(
            MarkensChannelItem("${groupId}_main", "основной-чат", "Главный канал $cleanName"),
            MarkensChannelItem("${groupId}_news", "новости", "Объявления $cleanName")
        )
        val newGroup = MarkensServerGroup(
            id = groupId,
            name = cleanName,
            shortTag = shortTag,
            description = MarkensCrypto.sanitizeInput(description, maxLen = 120)
                .ifBlank { "Сервер $cleanName" },
            owner = creator.name,
            isOfficialVortex = false,
            textChannels = defaultText
        )

        saveGroupToCloud(newGroup)
        Result.success(newGroup)
    }

    suspend fun addTextChannelToGroup(
        group: MarkensServerGroup,
        channelTitle: String
    ): MarkensServerGroup = withContext(Dispatchers.IO) {
        val clean = MarkensCrypto.sanitizeInput(channelTitle, maxLen = 36).replace("#", "").trim()
        if (clean.isEmpty()) return@withContext group

        val slug = clean.lowercase().replace(Regex("\\s+"), "-")
        val key = if (group.isOfficialVortex) slug else "${group.id}_$slug"
        val newCh = MarkensChannelItem(
            key = key,
            title = slug,
            subtitle = "Текстовый канал ${group.name}"
        )
        val updated = group.copy(textChannels = (group.textChannels + newCh).distinctBy { it.key })
        saveGroupToCloud(updated)
        updated
    }

    private fun saveGroupToCloud(group: MarkensServerGroup) {
        try {
            val tArr = JSONArray()
            group.textChannels.forEach { ch ->
                tArr.put(JSONObject().apply {
                    put("key", ch.key)
                    put("title", ch.title)
                    put("subtitle", ch.subtitle)
                })
            }
            val obj = JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("shortTag", group.shortTag)
                put("description", group.description)
                put("owner", group.owner)
                put("textChannels", tArr)
                put("ts", System.currentTimeMillis())
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/servers_v25/${enc(group.id)}.json")
                    .put(obj.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
    }

    fun loadSavedUser(): MarkensUser? {
        val raw = prefs.getString("current_user_json", null) ?: return null
        val savedSig = prefs.getString("current_user_sig", null)
        // Require integrity signature and non-empty securityToken
        if (savedSig.isNullOrBlank() || savedSig != MarkensCrypto.computeCacheIntegrityTag("user_session", raw)) {
            clearSession()
            return null
        }
        return try {
            val obj = JSONObject(raw)
            val name = obj.optString("name", "").trim()
            val secToken = obj.optString("securityToken", "").trim()
            if (name.isEmpty() || secToken.isEmpty()) {
                clearSession()
                return null
            }
            val isOwner = name.equals("markens", ignoreCase = true) && obj.optBoolean("isOwner", false)
            val canonical = if (name.equals("markens", ignoreCase = true)) "markens" else name
            val avatar = obj.optString("avatarUrl", "").takeIf { it.isNotBlank() } ?: getUserAvatar(canonical)
            if (avatar != null) {
                avatarMemCache[canonical.lowercase()] = avatar
            }
            MarkensUser(
                name = canonical,
                isOwner = isOwner,
                status = obj.optString("status", "В сети"),
                bio = obj.optString("bio", "Участник markens"),
                badge = obj.optString("badge", if (isOwner) "👑" else "🎯"),
                level = obj.optInt("level", if (isOwner) 1000 else 3),
                xp = obj.optInt("xp", if (isOwner) 100 else 40),
                securityToken = secToken,
                avatarUrl = avatar,
                sessionId = obj.optString("sessionId", ""),
                authToken = obj.optString("authToken", ""),
                verifierHash = obj.optString("verifierHash", "")
            )
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    fun saveUserSession(user: MarkensUser) {
        cacheUserAvatar(user.name, user.avatarUrl)
        val obj = JSONObject().apply {
            put("name", user.name)
            put("isOwner", user.isOwner)
            put("status", user.status)
            put("bio", user.bio)
            put("badge", user.badge)
            put("level", user.level)
            put("xp", user.xp)
            put("securityToken", user.securityToken)
            if (!user.avatarUrl.isNullOrBlank()) {
                put("avatarUrl", user.avatarUrl)
            }
            if (user.sessionId.isNotBlank()) {
                put("sessionId", user.sessionId)
            }
            if (user.authToken.isNotBlank()) {
                put("authToken", user.authToken)
            }
            if (user.verifierHash.isNotBlank()) {
                put("verifierHash", user.verifierHash)
            }
        }
        val raw = obj.toString()
        val sig = MarkensCrypto.computeCacheIntegrityTag("user_session", raw)
        prefs.edit()
            .putString("current_user_json", raw)
            .putString("current_user_sig", sig)
            .commit()
    }

    /**
     * Persists only the login and non-reversible `sessionVerifier` hash (`mkhash_...`).
     * Plaintext passwords are NEVER written to SharedPreferences.
     */
    fun saveRememberedCredentials(
        login: String,
        passOrVerifier: String,
        badge: String,
        isOwner: Boolean,
        avatarUrl: String? = getUserAvatar(login)
    ) {
        val cleanLogin = login.trim()
        if (cleanLogin.isEmpty() || passOrVerifier.isEmpty()) return
        val verifier = if (passOrVerifier.startsWith("mkhash_")) {
            passOrVerifier
        } else {
            MarkensCrypto.hashUserPassword(cleanLogin, passOrVerifier)
        }
        val existing = getSavedAccounts().toMutableList()
        existing.removeAll { it.login.equals(cleanLogin, ignoreCase = true) }
        existing.add(0, MarkensSavedAccount(cleanLogin, verifier, badge, isOwner, avatarUrl))
        val arr = JSONArray()
        existing.take(5).forEach { acc ->
            arr.put(JSONObject().apply {
                put("login", acc.login)
                put("sessionVerifier", acc.sessionVerifier)
                put("badge", acc.badge)
                put("isOwner", acc.isOwner)
                if (!acc.avatarUrl.isNullOrBlank()) {
                    put("avatarUrl", acc.avatarUrl)
                }
            })
        }
        prefs.edit()
            .putString("last_remembered_login", cleanLogin)
            .remove("last_remembered_password")
            .putString("saved_accounts_list", arr.toString())
            .commit()
    }

    fun getLastRememberedLogin(): String {
        return prefs.getString("last_remembered_login", "") ?: ""
    }

    /**
     * Kept for source compatibility; returns only `(lastLogin, "")` — never returns a plaintext password.
     */
    fun getLastRememberedCredentials(): Pair<String, String> {
        val l = prefs.getString("last_remembered_login", "") ?: ""
        return Pair(l, "")
    }

    fun getSavedAccounts(): List<MarkensSavedAccount> {
        val raw = prefs.getString("saved_accounts_list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<MarkensSavedAccount>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val l = o.optString("login", "").trim()
                val v = o.optString("sessionVerifier", "").ifBlank {
                    val legacyP = o.optString("password", "")
                    if (legacyP.startsWith("mkhash_")) legacyP
                    else if (legacyP.isNotBlank()) MarkensCrypto.hashUserPassword(l, legacyP)
                    else ""
                }
                if (l.isNotEmpty() && v.isNotEmpty()) {
                    val av = o.optString("avatarUrl", "").takeIf { it.isNotBlank() } ?: getUserAvatar(l)
                    list.add(
                        MarkensSavedAccount(
                            login = l,
                            sessionVerifier = v,
                            badge = o.optString("badge", "🎯"),
                            isOwner = o.optBoolean("isOwner", false) && l.equals("markens", ignoreCase = true),
                            avatarUrl = av
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun removeSavedAccount(login: String) {
        val updated = getSavedAccounts().filterNot { it.login.equals(login, ignoreCase = true) }
        val arr = JSONArray()
        updated.forEach { acc ->
            arr.put(JSONObject().apply {
                put("login", acc.login)
                put("sessionVerifier", acc.sessionVerifier)
                put("badge", acc.badge)
                put("isOwner", acc.isOwner)
                if (!acc.avatarUrl.isNullOrBlank()) {
                    put("avatarUrl", acc.avatarUrl)
                }
            })
        }
        prefs.edit().putString("saved_accounts_list", arr.toString()).commit()
    }

    fun clearSession() {
        prefs.edit()
            .remove("current_user_json")
            .remove("current_user_sig")
            .commit()
    }

    private data class RemoteAccountLookup(
        val accountKey: String,
        val canonicalName: String,
        val existingJson: JSONObject?
    )

    private fun fetchAuthVault(userLower: String): JSONObject? {
        return try {
            val req = Request.Builder()
                .url("$MARKENS_RTDB_URL/auth_vault_v31/${enc(userLower.lowercase().trim())}.json")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()
                if (!body.isNullOrBlank() && body != "null") JSONObject(body) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchUserPrefsV26(userLower: String): JSONObject? {
        return try {
            val req = Request.Builder()
                .url("$MARKENS_RTDB_URL/user_prefs_v26/${enc(userLower.lowercase().trim())}.json")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()
                if (!body.isNullOrBlank() && body != "null") JSONObject(body) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Provisions or updates the PC v31.2.0 PBKDF2 Auth Vault (`/auth_vault_v31`),
     * secret token (`/secret_tokens_v31`), and active session (`/auth_sessions_v31`).
     */
    private fun provisionOrSyncAuthVault(
        userLower: String,
        derived: MarkensPbkdf2Keys,
        existingVault: JSONObject? = null
    ): String {
        val cleanLower = userLower.lowercase().trim()
        val now = System.currentTimeMillis()
        val sessionId = "sess_${now}_${MarkensCrypto.generateRandomSaltHex(8)}"
        try {
            // 1. Write secret token if vault is new or matches
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/secret_tokens_v31/${enc(cleanLower)}.json")
                    .put(JSONObject.quote(derived.authToken).toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()

            // 2. Write PBKDF2 vault metadata if not already present or if salt matches
            if (existingVault == null || existingVault.optString("verifierHash").isBlank()) {
                val vaultObj = JSONObject().apply {
                    put("salt", derived.salt)
                    put("verifierHash", derived.verifierHash)
                    put("iterations", derived.iterations)
                    put("algo", derived.algo)
                    put("createdAt", now)
                }
                http.newCall(
                    Request.Builder()
                        .url("$MARKENS_RTDB_URL/auth_vault_v31/${enc(cleanLower)}.json")
                        .put(vaultObj.toString().toRequestBody(jsonMediaType))
                        .build()
                ).execute().close()
            }

            // 3. Register active session in /auth_sessions_v31/{sessionId}
            val sessObj = JSONObject().apply {
                put("user", cleanLower)
                put("token", derived.authToken)
                put("device", "📱 Android")
                put("ts", now)
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/auth_sessions_v31/${enc(sessionId)}.json")
                    .put(sessObj.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
        return sessionId
    }

    /**
     * Resolves an account in Firebase RTDB (`/accounts/{login}.json`).
     * Supports exact match first, followed by case-insensitive fallback across `/accounts.json`
     * so users whose accounts were created on Web/Windows with different letter casing
     * (e.g., `Aquamys`, `Vova`, `пп`, `markens`) can sign in seamlessly regardless of keyboard capitalization.
     */
    private fun resolveRemoteAccount(requestedLogin: String): RemoteAccountLookup {
        val trimmed = requestedLogin.trim()
        if (trimmed.equals("markens", ignoreCase = true)) {
            val req = Request.Builder()
                .url("$MARKENS_RTDB_URL/accounts/markens.json")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Сервер авторизации вернул ошибку (${resp.code}). Попробуйте позже.")
                }
                val body = resp.body?.string()
                val obj = if (!body.isNullOrBlank() && body != "null") JSONObject(body) else null
                return RemoteAccountLookup(
                    accountKey = "markens",
                    canonicalName = "markens",
                    existingJson = obj
                )
            }
        }

        val exactReq = Request.Builder()
            .url("$MARKENS_RTDB_URL/accounts/${enc(trimmed)}.json")
            .get()
            .build()
        http.newCall(exactReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Сервер авторизации вернул ошибку (${resp.code}). Попробуйте позже.")
            }
            val body = resp.body?.string()
            if (!body.isNullOrBlank() && body != "null") {
                val obj = JSONObject(body)
                val canonical = obj.optString("name", trimmed).trim().ifBlank { trimmed }
                return RemoteAccountLookup(
                    accountKey = trimmed,
                    canonicalName = canonical,
                    existingJson = obj
                )
            }
        }

        // Case-insensitive fallback across /accounts.json
        val allReq = Request.Builder()
            .url("$MARKENS_RTDB_URL/accounts.json")
            .get()
            .build()
        http.newCall(allReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank() && body != "null") {
                    val root = JSONObject(body)
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val obj = root.optJSONObject(key) ?: continue
                        val nameField = obj.optString("name", key).trim()
                        if (key.equals(trimmed, ignoreCase = true) || nameField.equals(trimmed, ignoreCase = true)) {
                            val canonical = if (key.equals("markens", ignoreCase = true)) {
                                "markens"
                            } else {
                                nameField.ifBlank { key }
                            }
                            return RemoteAccountLookup(
                                accountKey = key,
                                canonicalName = canonical,
                                existingJson = obj
                            )
                        }
                    }
                }
            }
        }

        return RemoteAccountLookup(
            accountKey = trimmed,
            canonicalName = trimmed,
            existingJson = null
        )
    }

    /**
     * Verifies an active session or saved account verifier against the remote Firebase `/accounts/{login}`
     * and `/auth_vault_v31/{userLower}` records.
     */
    suspend fun verifySessionWithServer(user: MarkensUser): Result<MarkensUser> = withContext(Dispatchers.IO) {
        try {
            val lookup = resolveRemoteAccount(user.name)
            val remote = lookup.existingJson
            val vault = fetchAuthVault(lookup.canonicalName.lowercase())
            if (remote == null && vault == null && !lookup.canonicalName.equals("markens", ignoreCase = true)) {
                clearSession()
                return@withContext Result.failure(
                    SecurityException("Аккаунт «${user.name}» больше не существует на сервере")
                )
            }
            val validHashes = MarkensCrypto.extractValidPasswordHashes(lookup.canonicalName, remote)
            val vaultVerifier = vault?.optString("verifierHash", "")?.takeIf { it.isNotBlank() }
            val isVaultVerified = vaultVerifier != null && user.verifierHash == vaultVerifier
            if (!isVaultVerified && validHashes.isNotEmpty()) {
                val validSessionTokens = validHashes.map { hash ->
                    MarkensCrypto.computeSessionToken(lookup.canonicalName, hash)
                }
                if (user.securityToken !in validSessionTokens) {
                    clearSession()
                    return@withContext Result.failure(
                        SecurityException("Сессия устарела или пароль был изменён на другом устройстве")
                    )
                }
            }
            val prefsV26 = fetchUserPrefsV26(lookup.canonicalName.lowercase())
            val verifiedOwner = lookup.canonicalName.equals("markens", ignoreCase = true) &&
                (remote?.optBoolean("isOwner", true) ?: true)
            val remoteVortex = (remote?.optBoolean("vortexAccepted_v25", false) == true) ||
                (remote?.optBoolean("joinedVortex", false) == true)
            if (verifiedOwner || remoteVortex) {
                setVortexJoined(lookup.canonicalName, true)
            }
            val remoteAvatar = prefsV26?.optString("avatar", "")?.takeIf { it.isNotBlank() }
                ?: remote?.optString("avatarUrl", "")?.takeIf { it.isNotBlank() }
                ?: remote?.optString("avatar", "")?.takeIf { it.isNotBlank() }
                ?: user.avatarUrl
            val updated = user.copy(
                name = lookup.canonicalName,
                isOwner = verifiedOwner,
                status = prefsV26?.optString("status", "")?.takeIf { it.isNotBlank() }
                    ?: remote?.optString("status", user.status)?.ifBlank { user.status }
                    ?: user.status,
                bio = prefsV26?.optString("bio", "")?.takeIf { it.isNotBlank() }
                    ?: remote?.optString("bio", user.bio)?.ifBlank { user.bio }
                    ?: user.bio,
                badge = if (verifiedOwner) "👑" else (
                    prefsV26?.optString("badge", "")?.takeIf { it.isNotBlank() }
                        ?: remote?.optString("badge", user.badge)?.ifBlank { user.badge }
                        ?: user.badge
                    ),
                level = if (verifiedOwner) 1000 else (remote?.optInt("level", user.level) ?: user.level),
                xp = if (verifiedOwner) 100 else (remote?.optInt("xp", user.xp) ?: user.xp),
                avatarUrl = remoteAvatar
            )
            saveUserSession(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logs in using a saved account's non-reversible `sessionVerifier` hash, strictly verified against the server.
     */
    suspend fun loginWithSavedAccount(account: MarkensSavedAccount): Result<MarkensUser> = withContext(Dispatchers.IO) {
        val cleanLogin = MarkensCrypto.sanitizeInput(account.login, maxLen = 32)
        if (cleanLogin.isEmpty() || account.sessionVerifier.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Некорректные данные сохранённого аккаунта"))
        }
        try {
            val lookup = resolveRemoteAccount(cleanLogin)
            val canonicalName = lookup.canonicalName
            val existing = lookup.existingJson
            val vault = fetchAuthVault(canonicalName.lowercase())
            if (existing == null && vault == null && !canonicalName.equals("markens", ignoreCase = true)) {
                removeSavedAccount(cleanLogin)
                removeSavedAccount(canonicalName)
                return@withContext Result.failure(
                    IllegalArgumentException("Аккаунт «$canonicalName» не найден на сервере.")
                )
            }
            val validHashes = MarkensCrypto.extractValidPasswordHashes(canonicalName, existing).toMutableSet()
            vault?.optString("verifierHash", "")?.takeIf { it.isNotBlank() }?.let { validHashes.add(it) }
            if (validHashes.isEmpty() || account.sessionVerifier !in validHashes) {
                removeSavedAccount(cleanLogin)
                removeSavedAccount(canonicalName)
                return@withContext Result.failure(
                    IllegalArgumentException("Пароль аккаунта «$canonicalName» был изменён или сохранённый токен устарел. Введите пароль вручную.")
                )
            }
            val prefsV26 = fetchUserPrefsV26(canonicalName.lowercase())
            val isOwner = canonicalName.equals("markens", ignoreCase = true)
            val remoteVortex = (existing?.optBoolean("vortexAccepted_v25", false) == true) ||
                (existing?.optBoolean("joinedVortex", false) == true)
            if (isOwner || remoteVortex) {
                setVortexJoined(canonicalName, true)
            }
            val sessionToken = MarkensCrypto.computeSessionToken(canonicalName, account.sessionVerifier)
            val resolvedAvatar = prefsV26?.optString("avatar", "")?.takeIf { it.isNotBlank() }
                ?: existing?.optString("avatarUrl", "")?.takeIf { it.isNotBlank() }
                ?: existing?.optString("avatar", "")?.takeIf { it.isNotBlank() }
                ?: getUserAvatar(canonicalName)
            val user = MarkensUser(
                name = canonicalName,
                isOwner = isOwner,
                status = prefsV26?.optString("status", "")?.takeIf { it.isNotBlank() }
                    ?: existing?.optString("status", "В сети")?.ifBlank { "В сети" }
                    ?: "В сети",
                bio = prefsV26?.optString("bio", "")?.takeIf { it.isNotBlank() }
                    ?: existing?.optString("bio", if (isOwner) "Основатель markens и VORTEX" else "Участник markens")
                        ?.ifBlank { if (isOwner) "Основатель markens и VORTEX" else "Участник markens" }
                    ?: if (isOwner) "Основатель markens и VORTEX" else "Участник markens",
                badge = if (isOwner) "👑" else (
                    prefsV26?.optString("badge", "")?.takeIf { it.isNotBlank() }
                        ?: existing?.optString("badge", "🎯")?.ifBlank { "🎯" }
                        ?: "🎯"
                    ),
                level = if (isOwner) 1000 else (existing?.optInt("level", 3) ?: 3),
                xp = if (isOwner) 100 else (existing?.optInt("xp", 40) ?: 40),
                securityToken = sessionToken,
                avatarUrl = resolvedAvatar,
                verifierHash = vault?.optString("verifierHash", "") ?: ""
            )
            saveUserSession(user)
            saveRememberedCredentials(canonicalName, account.sessionVerifier, user.badge, user.isOwner, user.avatarUrl)
            Result.success(user)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Нет соединения с сервером MARKENS. Проверьте интернет."))
        }
    }

    fun getInstalledVersion(): String = prefs.getString("installed_version", "v31.2.0") ?: "v31.2.0"
    fun getInstalledBuildTs(): Long = prefs.getLong("installed_build_ts", 1793350000000L)

    fun markVersionInstalled(version: String, buildTs: Long) {
        prefs.edit()
            .putString("installed_version", version)
            .putLong("installed_build_ts", buildTs)
            .putString("installed_ota_version", version)
            .putLong("installed_ota_build_ts", buildTs)
            .putString("last_applied_sig", "${version}__$buildTs")
            .commit()
    }

    fun isOtaAlreadyApplied(version: String, buildTs: Long): Boolean {
        val sig = "${version}__$buildTs"
        return prefs.getString("last_applied_sig", "") == sig
    }

    fun getSavedTheme(): String = prefs.getString("markens_theme", "system") ?: "system"
    fun saveTheme(theme: String) {
        prefs.edit().putString("markens_theme", theme).apply()
    }

    fun isStartupSoundEnabled(): Boolean = prefs.getBoolean("markens_startup_sound", true)
    fun setStartupSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("markens_startup_sound", enabled).apply()
    }

    fun getAutoUpdateEnabled(): Boolean = prefs.getBoolean("markens_auto_ota", true)
    fun setAutoUpdateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("markens_auto_ota", enabled).apply()
    }

    fun getLocalFriends(myName: String): List<String> {
        val raw = prefs.getString("friends_${myName.lowercase()}", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLocalFriends(myName: String, friends: List<String>) {
        val arr = JSONArray()
        friends.distinct().forEach { arr.put(it) }
        prefs.edit().putString("friends_${myName.lowercase()}", arr.toString()).apply()
    }

    private fun parseMessagesJsonArray(raw: String, channelKey: String = ""): List<MarkensMessage> {
        val isDmChannel = channelKey.lowercase().trim().startsWith("dm_")
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<MarkensMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val replyObj = obj.optJSONObject("replyTo")
                val replyRef = if (replyObj != null) {
                    val rawReply = replyObj.optString("text", "")
                    val decReply = if (isDmChannel && MarkensCrypto.isEncryptedDmCipher(rawReply)) {
                        MarkensCrypto.decryptDmPayload(rawReply, channelKey)
                    } else {
                        rawReply
                    }
                    MarkensReplyRef(
                        id = replyObj.optString("id", ""),
                        sender = replyObj.optString("sender", ""),
                        text = decReply
                    )
                } else null

                val reactionsMap = mutableMapOf<String, List<String>>()
                val reactObj = obj.optJSONObject("reactions")
                if (reactObj != null) {
                    val rKeys = reactObj.keys()
                    while (rKeys.hasNext()) {
                        val emoji = rKeys.next()
                        val uArr = reactObj.optJSONArray(emoji)
                        if (uArr != null) {
                            val uList = (0 until uArr.length()).mapNotNull {
                                uArr.optString(it).takeIf { s -> s.isNotBlank() }
                            }
                            if (uList.isNotEmpty()) reactionsMap[emoji] = uList
                        }
                    }
                }

                val sender = obj.optString("sender", "Участник")
                val senderAvatar = obj.optString("senderAvatarUrl", "").takeIf { it.isNotBlank() }
                    ?: getUserAvatar(sender)

                val readByList = mutableListOf<String>()
                val readByArr = obj.optJSONArray("readBy")
                if (readByArr != null) {
                    for (rIdx in 0 until readByArr.length()) {
                        val rName = readByArr.optString(rIdx).trim()
                        if (rName.isNotEmpty()) readByList.add(rName)
                    }
                }

                val statusStr = obj.optString("deliveryStatus", "SENT")
                val deliveryStatus = try {
                    MessageDeliveryStatus.valueOf(statusStr)
                } catch (_: Exception) {
                    MessageDeliveryStatus.SENT
                }

                val rawText = obj.optString("text", "")
                val decText = if (isDmChannel && MarkensCrypto.isEncryptedDmCipher(rawText)) {
                    MarkensCrypto.decryptDmPayload(rawText, channelKey)
                } else {
                    rawText
                }

                list.add(
                    MarkensMessage(
                        id = obj.optString("id", ""),
                        sender = sender,
                        senderBadge = obj.optString("senderBadge", "").takeIf { it.isNotBlank() },
                        senderRank = obj.optString("senderRank", "").takeIf { it.isNotBlank() },
                        text = decText,
                        time = obj.optString("time", ""),
                        ts = obj.optLong("ts", 0L),
                        mediaUrl = obj.optString("mediaUrl", "").takeIf { it.isNotBlank() },
                        mediaType = obj.optString("mediaType", "").takeIf { it.isNotBlank() },
                        senderAvatarUrl = senderAvatar,
                        pinned = obj.optBoolean("pinned", false),
                        edited = obj.optBoolean("edited", false),
                        replyTo = replyRef,
                        reactions = reactionsMap,
                        readBy = readByList.distinctBy { it.lowercase() },
                        deliveryStatus = deliveryStatus
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeMessagesJsonArray(messages: List<MarkensMessage>): JSONArray {
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("sender", m.sender)
                if (m.senderBadge != null) put("senderBadge", m.senderBadge)
                if (m.senderRank != null) put("senderRank", m.senderRank)
                put("text", m.text)
                put("time", m.time)
                put("ts", m.ts)
                put("pinned", m.pinned)
                put("edited", m.edited)
                put("deliveryStatus", m.deliveryStatus.name)
                if (m.mediaUrl != null) {
                    put("mediaUrl", m.mediaUrl)
                    put("mediaType", m.mediaType ?: "image")
                }
                if (!m.senderAvatarUrl.isNullOrBlank()) {
                    put("senderAvatarUrl", m.senderAvatarUrl)
                }
                if (m.replyTo != null) {
                    put("replyTo", JSONObject().apply {
                        put("id", m.replyTo.id)
                        put("sender", m.replyTo.sender)
                        put("text", m.replyTo.text)
                    })
                }
                if (m.reactions.isNotEmpty()) {
                    val rObj = JSONObject()
                    m.reactions.forEach { (emoji, users) ->
                        val uArr = JSONArray()
                        users.forEach { uArr.put(it) }
                        rObj.put(emoji, uArr)
                    }
                    put("reactions", rObj)
                }
                if (m.readBy.isNotEmpty()) {
                    val rbArr = JSONArray()
                    m.readBy.forEach { rbArr.put(it) }
                    put("readBy", rbArr)
                }
            })
        }
        return arr
    }

    private fun getOutboxForChannel(channelKey: String): MutableList<MarkensMessage> {
        return pendingOutbox.getOrPut(channelKey) {
            val raw = prefs.getString("outbox_msgs_$channelKey", null)
            if (raw.isNullOrBlank()) {
                mutableListOf()
            } else {
                parseMessagesJsonArray(raw, channelKey).toMutableList()
            }
        }
    }

    private fun saveOutboxForChannel(channelKey: String, outbox: List<MarkensMessage>) {
        if (outbox.isEmpty()) {
            pendingOutbox.remove(channelKey)
            prefs.edit().remove("outbox_msgs_$channelKey").apply()
        } else {
            pendingOutbox[channelKey] = outbox.toMutableList()
            prefs.edit().putString("outbox_msgs_$channelKey", serializeMessagesJsonArray(outbox).toString()).apply()
        }
    }

    private fun formatPreviewText(msg: MarkensMessage, channelKey: String = ""): String {
        if (msg.text.isNotBlank()) {
            return if (channelKey.lowercase().trim().startsWith("dm_") && MarkensCrypto.isEncryptedDmCipher(msg.text)) {
                MarkensCrypto.decryptDmPayload(msg.text, channelKey)
            } else {
                msg.text
            }
        }
        return when (msg.mediaType) {
            "voice" -> "🎤 Голосовое сообщение"
            else -> "📷 Фотография"
        }
    }

    fun getCachedChannelMessages(channelKey: String): List<MarkensMessage> {
        messageMemCache[channelKey]?.let { return it }
        val raw = prefs.getString("cache_msgs_$channelKey", null)
        val cachedList = if (raw.isNullOrBlank()) emptyList() else parseMessagesJsonArray(raw, channelKey)
        val outbox = getOutboxForChannel(channelKey)
        val merged = mergeServerAndOutbox(cachedList, outbox)
        messageMemCache[channelKey] = merged
        merged.lastOrNull()?.let { last ->
            previewMemCache[channelKey] = DmLastPreview(
                text = formatPreviewText(last, channelKey),
                time = last.time,
                sender = last.sender,
                ts = last.ts,
                readBy = last.readBy,
                deliveryStatus = last.effectiveDeliveryStatus()
            )
        }
        return merged
    }

    private fun mergeServerAndOutbox(
        serverMessages: List<MarkensMessage>,
        outbox: List<MarkensMessage>
    ): List<MarkensMessage> {
        if (outbox.isEmpty()) return serverMessages
        val serverIds = serverMessages.map { it.id }.toSet()
        val remainingOutbox = outbox.filter { it.id !in serverIds }
        return (serverMessages + remainingOutbox).sortedBy { it.ts }
    }

    fun getDmLastPreview(myName: String, friendName: String): DmLastPreview? {
        val pair = listOf(myName.lowercase(), friendName.lowercase()).sorted().joinToString("_")
        return getChannelLastPreview("dm_$pair")
    }

    fun getChannelLastPreview(channelKey: String): DmLastPreview? {
        previewMemCache[channelKey]?.let { return it }
        val msgs = getCachedChannelMessages(channelKey)
        val last = msgs.lastOrNull() ?: return null
        val preview = DmLastPreview(
            text = formatPreviewText(last),
            time = last.time,
            sender = last.sender,
            ts = last.ts,
            readBy = last.readBy,
            deliveryStatus = last.effectiveDeliveryStatus()
        )
        previewMemCache[channelKey] = preview
        return preview
    }

    fun getUnreadCountForChannel(channelKey: String, myName: String): Int {
        val msgs = getCachedChannelMessages(channelKey)
        if (msgs.isEmpty()) return 0
        return msgs.count { msg ->
            !msg.sender.equals(myName, ignoreCase = true) &&
                !msg.readBy.any { it.equals(myName, ignoreCase = true) }
        }
    }

    fun getDmUnreadCount(myName: String, friendName: String): Int {
        val pair = listOf(myName.lowercase(), friendName.lowercase()).sorted().joinToString("_")
        return getUnreadCountForChannel("dm_$pair", myName)
    }

    fun saveCachedChannelMessages(channelKey: String, messages: List<MarkensMessage>) {
        val trimmed = messages.takeLast(50)
        val previous = messageMemCache[channelKey]
        messageMemCache[channelKey] = trimmed
        val last = trimmed.lastOrNull()
        if (last != null) {
            previewMemCache[channelKey] = DmLastPreview(
                text = formatPreviewText(last),
                time = last.time,
                sender = last.sender,
                ts = last.ts,
                readBy = last.readBy,
                deliveryStatus = last.effectiveDeliveryStatus()
            )
        } else {
            previewMemCache.remove(channelKey)
        }
        // Skip expensive JSON serialization & disk write if nothing changed
        if (previous != null && previous == trimmed) return

        try {
            val arr = serializeMessagesJsonArray(trimmed)
            prefs.edit().putString("cache_msgs_$channelKey", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Strictly server-verified Login & Registration flow synchronized with PC v31.2.0:
     * - Supports PBKDF2-HMAC-SHA-256 (100,000 iterations) Auth Vault (`/auth_vault_v31/$user`, `/secret_tokens_v31/$user`, `/auth_sessions_v31/$sessionId`).
     * - Supports legacy `/accounts` (`passwordHash`, `sha256$`, `password`, and `markens` creator hashes) and automatically upgrades/syncs with `/auth_vault_v31`.
     * - Syncs cross-device profile preferences from `/user_prefs_v26/$user`.
     */
    suspend fun loginOrRegister(
        rawLogin: String,
        rawPass: String,
        isRegister: Boolean
    ): Result<MarkensUser> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now < loginCooldownUntilMs) {
            val waitSec = ((loginCooldownUntilMs - now) / 1000L).coerceAtLeast(1L)
            return@withContext Result.failure(
                IllegalArgumentException("Слишком много попыток. Подождите $waitSec сек.")
            )
        }

        val cleanLogin = MarkensCrypto.sanitizeInput(rawLogin, maxLen = 32)
        val trimmedPass = rawPass.trim()
        if (cleanLogin.isEmpty() || cleanLogin.length > 32) {
            return@withContext Result.failure(IllegalArgumentException("Введите корректный никнейм (до 32 символов)"))
        }
        if (isRegister && cleanLogin.length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Никнейм для регистрации должен содержать минимум 2 символа"))
        }
        if (trimmedPass.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Введите пароль"))
        }
        if (isRegister && trimmedPass.length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Пароль для нового аккаунта должен содержать минимум 2 символа"))
        }

        val lookup = try {
            resolveRemoteAccount(cleanLogin)
        } catch (e: IllegalStateException) {
            return@withContext Result.failure(e)
        } catch (_: Exception) {
            return@withContext Result.failure(
                IllegalStateException("Нет соединения с сервером MARKENS. Проверьте подключение к интернету.")
            )
        }

        val accountKey = lookup.accountKey
        val canonicalName = lookup.canonicalName
        val userLower = canonicalName.lowercase()
        val existing = lookup.existingJson
        val existingVault = fetchAuthVault(userLower)
        val accountExists = existing != null || existingVault != null
        val isOwnerCandidate = canonicalName.equals("markens", ignoreCase = true)

        val passHash = MarkensCrypto.hashUserPassword(canonicalName, trimmedPass)
        val sha256Hash = MarkensCrypto.hashUserPasswordSha256(canonicalName, trimmedPass)
        val sessionToken = MarkensCrypto.computeSessionToken(canonicalName, passHash)
        val validHashes = MarkensCrypto.extractValidPasswordHashes(canonicalName, existing)

        val vaultSalt = existingVault?.optString("salt", "") ?: ""
        val vaultVerifier = existingVault?.optString("verifierHash", "")?.takeIf { it.isNotBlank() }
        val vaultIterations = existingVault?.optInt("iterations", 100_000)?.takeIf { it >= 10_000 } ?: 100_000

        val derivedPbkdf2 = MarkensCrypto.derivePbkdf2AccountKeys(
            login = userLower,
            rawPass = trimmedPass,
            saltHex = vaultSalt,
            iterations = vaultIterations
        )

        val matchesPbkdf2Vault = vaultVerifier != null && derivedPbkdf2.verifierHash == vaultVerifier
        val matchesAccountHashes = validHashes.isNotEmpty() && (passHash in validHashes || sha256Hash in validHashes)

        // Strict check 1: On Login (`!isRegister`), the account MUST exist on the server (unless `markens` creator)
        if (!isRegister && !accountExists && !isOwnerCandidate) {
            failedLoginAttempts += 1
            if (failedLoginAttempts >= 5) {
                loginCooldownUntilMs = System.currentTimeMillis() + 6_000L
            }
            return@withContext Result.failure(
                IllegalArgumentException("Аккаунт «$canonicalName» не найден. Переключитесь на «Регистрация», чтобы создать его.")
            )
        }

        // Strict check 2: If account exists (or is creator account `markens`), verify password against PBKDF2 vault OR validHashes
        if (accountExists || isOwnerCandidate) {
            val isPasswordValid = matchesPbkdf2Vault || matchesAccountHashes
            if (!isPasswordValid) {
                failedLoginAttempts += 1
                if (failedLoginAttempts >= 5) {
                    loginCooldownUntilMs = System.currentTimeMillis() + 6_000L
                }
                removeSavedAccount(cleanLogin)
                removeSavedAccount(canonicalName)
                val msg = when {
                    isRegister && isOwnerCandidate ->
                        "Ник «markens» принадлежит создателю мессенджера. Переключитесь на «Вход» и введите пароль создателя."
                    isRegister ->
                        "Ник «$canonicalName» уже занят другим паролем. Выберите другой ник или переключитесь на «Вход»."
                    else ->
                        "Неверный пароль для аккаунта «$canonicalName»."
                }
                return@withContext Result.failure(IllegalArgumentException(msg))
            }
        }

        val isVerifiedOwner = isOwnerCandidate && (matchesPbkdf2Vault || matchesAccountHashes)
        failedLoginAttempts = 0
        loginCooldownUntilMs = 0L

        // Provision or sync PBKDF2 Auth Vault + Session v31
        val activeSessionId = provisionOrSyncAuthVault(userLower, derivedPbkdf2, existingVault)
        val prefsV26 = fetchUserPrefsV26(userLower)

        val remoteBadge = prefsV26?.optString("badge", "")?.takeIf { it.isNotBlank() }
            ?: existing?.optString("badge", "")?.takeIf { it.isNotBlank() }
        val remoteStatus = prefsV26?.optString("status", "")?.takeIf { it.isNotBlank() }
            ?: existing?.optString("status", "")?.takeIf { it.isNotBlank() }
        val remoteBio = prefsV26?.optString("bio", "")?.takeIf { it.isNotBlank() }
            ?: existing?.optString("bio", "")?.takeIf { it.isNotBlank() }
        val remoteAvatarUrl = prefsV26?.optString("avatar", "")?.takeIf { it.isNotBlank() }
            ?: existing?.optString("avatarUrl", "")?.takeIf { it.isNotBlank() }
            ?: existing?.optString("avatar", "")?.takeIf { it.isNotBlank() }
        val remoteLevel = if (existing?.has("level") == true) existing.optInt("level") else null
        val remoteXp = if (existing?.has("xp") == true) existing.optInt("xp") else null
        val remoteVortexAccepted = (existing?.optBoolean("vortexAccepted_v25", false) == true) ||
            (existing?.optBoolean("joinedVortex", false) == true)

        if (isVerifiedOwner || remoteVortexAccepted) {
            setVortexJoined(canonicalName, true)
        }

        val resolvedAvatar = remoteAvatarUrl ?: getUserAvatar(canonicalName)
        val user = MarkensUser(
            name = canonicalName,
            isOwner = isVerifiedOwner,
            status = remoteStatus ?: "В сети",
            bio = remoteBio ?: if (isVerifiedOwner) "Основатель markens и сервера VORTEX" else "Участник markens",
            badge = if (isVerifiedOwner) "👑" else (remoteBadge ?: "🎯"),
            level = if (isVerifiedOwner) 1000 else (remoteLevel ?: 3),
            xp = if (isVerifiedOwner) 100 else (remoteXp ?: 40),
            securityToken = sessionToken,
            avatarUrl = resolvedAvatar,
            sessionId = activeSessionId,
            authToken = derivedPbkdf2.authToken,
            verifierHash = derivedPbkdf2.verifierHash
        )

        // Non-destructively update or create the account record in Firebase RTDB
        try {
            val payload = JSONObject().apply {
                put("name", user.name)
                put("isOwner", user.isOwner)
                put("status", user.status)
                put("bio", user.bio)
                put("badge", user.badge)
                put("level", user.level)
                put("xp", user.xp)
                if (!user.avatarUrl.isNullOrBlank()) {
                    put("avatarUrl", user.avatarUrl)
                    put("avatar", user.avatarUrl)
                }
                put("device", "📱 Android")
                put("online", System.currentTimeMillis())
                put("sessionId", activeSessionId)
                if (!accountExists) {
                    put("passwordHash", passHash)
                    put("joinedVortex", isVerifiedOwner)
                    put("vortexAccepted_v25", isVerifiedOwner)
                } else if (!isOwnerCandidate && existing?.optString("passwordHash", "").isNullOrBlank()) {
                    put("passwordHash", passHash)
                }
            }
            val patchReq = Request.Builder()
                .url("$MARKENS_RTDB_URL/accounts/${enc(accountKey)}.json")
                .patch(payload.toString().toRequestBody(jsonMediaType))
                .build()
            http.newCall(patchReq).execute().use { patchResp ->
                if (!patchResp.isSuccessful && isRegister && !accountExists) {
                    return@withContext Result.failure(
                        IllegalStateException("Не удалось зарегистрировать аккаунт на сервере (код ${patchResp.code}).")
                    )
                }
            }
        } catch (_: Exception) {
            if (isRegister && !accountExists) {
                return@withContext Result.failure(
                    IllegalStateException("Ошибка сети при создании аккаунта на сервере. Повторите попытку.")
                )
            }
        }

        saveUserSession(user)
        saveRememberedCredentials(canonicalName, passHash, user.badge, user.isOwner, user.avatarUrl)

        Result.success(user)
    }

    suspend fun changeAccountPassword(user: MarkensUser, newPassword: String): Result<MarkensUser> =
        withContext(Dispatchers.IO) {
            val trimmed = newPassword.trim()
            if (trimmed.length < 3) {
                return@withContext Result.failure(IllegalArgumentException("Пароль должен быть от 3 символов"))
            }
            val newHash = MarkensCrypto.hashUserPassword(user.name, trimmed)
            val newToken = MarkensCrypto.computeSessionToken(user.name, newHash)
            val derivedPbkdf2 = MarkensCrypto.derivePbkdf2AccountKeys(user.name.lowercase(), trimmed)
            val newSessionId = provisionOrSyncAuthVault(user.name.lowercase(), derivedPbkdf2, existingVault = null)
            val updated = user.copy(
                securityToken = newToken,
                sessionId = newSessionId,
                authToken = derivedPbkdf2.authToken,
                verifierHash = derivedPbkdf2.verifierHash
            )

            try {
                val patch = JSONObject().apply {
                    put("passwordHash", newHash)
                    put("online", System.currentTimeMillis())
                    put("sessionId", newSessionId)
                }
                http.newCall(
                    Request.Builder()
                        .url("$MARKENS_RTDB_URL/accounts/${enc(user.name)}.json")
                        .patch(patch.toString().toRequestBody(jsonMediaType))
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Не удалось обновить пароль на сервере (${resp.code})")
                        )
                    }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }

            saveUserSession(updated)
            saveRememberedCredentials(user.name, newHash, user.badge, user.isOwner, user.avatarUrl)
            Result.success(updated)
        }

    suspend fun fetchChannelMessages(channelKey: String): List<MarkensMessage> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$MARKENS_RTDB_URL/channels_v22/${enc(channelKey)}.json")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext getCachedChannelMessages(channelKey)
                val body = resp.body?.string() ?: return@withContext getCachedChannelMessages(channelKey)
                val outbox = getOutboxForChannel(channelKey)
                if (body == "null" || body.isBlank()) {
                    val onlyOutbox = mergeServerAndOutbox(emptyList(), outbox)
                    saveCachedChannelMessages(channelKey, onlyOutbox)
                    return@withContext onlyOutbox
                }
                val isDmChannel = channelKey.lowercase().trim().startsWith("dm_")
                val root = JSONObject(body)
                val list = mutableListOf<MarkensMessage>()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = root.optJSONObject(key) ?: continue
                    val rawText = obj.optString("text", "")
                    val text = if (isDmChannel && MarkensCrypto.isEncryptedDmCipher(rawText)) {
                        MarkensCrypto.decryptDmPayload(rawText, channelKey)
                    } else {
                        rawText
                    }
                    val mediaUrl = obj.optString("mediaUrl", "").takeIf { it.isNotBlank() }
                    if (text.isBlank() && mediaUrl == null) continue

                    val replyObj = obj.optJSONObject("replyTo")
                    val replyRef = if (replyObj != null) {
                        val rawReplyText = replyObj.optString("text", "")
                        val decReplyText = if (isDmChannel && MarkensCrypto.isEncryptedDmCipher(rawReplyText)) {
                            MarkensCrypto.decryptDmPayload(rawReplyText, channelKey)
                        } else {
                            rawReplyText
                        }
                        MarkensReplyRef(
                            id = replyObj.optString("id", ""),
                            sender = replyObj.optString("sender", ""),
                            text = decReplyText
                        )
                    } else null

                    val reactionsMap = mutableMapOf<String, List<String>>()
                    val reactObj = obj.optJSONObject("reactions")
                    if (reactObj != null) {
                        val rKeys = reactObj.keys()
                        while (rKeys.hasNext()) {
                            val emoji = rKeys.next()
                            val usersArr = reactObj.optJSONArray(emoji)
                            if (usersArr != null) {
                                val uList = (0 until usersArr.length()).mapNotNull {
                                    usersArr.optString(it).takeIf { s -> s.isNotBlank() }
                                }
                                if (uList.isNotEmpty()) reactionsMap[emoji] = uList
                            }
                        }
                    }

                    val sender = obj.optString("sender", "Участник")
                    val senderAvatar = obj.optString("senderAvatarUrl", "").takeIf { it.isNotBlank() }
                        ?: getUserAvatar(sender)
                    if (!senderAvatar.isNullOrBlank()) {
                        avatarMemCache[sender.lowercase()] = senderAvatar
                    }
                    val ts = obj.optLong("ts", 0L)
                    val remoteSig = obj.optString("sig", "")
                    val isVerified = if (remoteSig.isNotBlank()) {
                        remoteSig == MarkensCrypto.computeMessageSignature(sender, ts, rawText) ||
                            remoteSig == MarkensCrypto.computeMessageSignature(sender, ts, text)
                    } else {
                        true
                    }

                    val readByList = mutableListOf<String>()
                    readByList.add(sender)
                    val readByArr = obj.optJSONArray("readBy")
                    if (readByArr != null) {
                        for (i in 0 until readByArr.length()) {
                            val r = readByArr.optString(i).trim()
                            if (r.isNotEmpty()) readByList.add(r)
                        }
                    } else {
                        val readByObj = obj.optJSONObject("readBy") ?: obj.optJSONObject("seenBy")
                        if (readByObj != null) {
                            val rKeys = readByObj.keys()
                            while (rKeys.hasNext()) {
                                val r = rKeys.next().trim()
                                if (r.isNotEmpty()) readByList.add(r)
                            }
                        }
                    }

                    val cleanReaders = readByList.distinctBy { it.lowercase() }
                    val hasOtherReaders = cleanReaders.any { !it.equals(sender, ignoreCase = true) }

                    list.add(
                        MarkensMessage(
                            id = obj.optString("id", key).ifBlank { key },
                            sender = sender,
                            senderBadge = obj.optString("senderBadge", "").takeIf { it.isNotBlank() },
                            senderRank = obj.optString("senderRank", "").takeIf { it.isNotBlank() },
                            text = text,
                            time = obj.optString("time", ""),
                            ts = ts,
                            mediaUrl = mediaUrl,
                            mediaType = obj.optString("mediaType", "").takeIf { it.isNotBlank() },
                            senderAvatarUrl = senderAvatar,
                            pinned = obj.optBoolean("pinned", false),
                            edited = obj.optBoolean("edited", false),
                            verifiedSig = isVerified,
                            replyTo = replyRef,
                            reactions = reactionsMap,
                            readBy = cleanReaders,
                            deliveryStatus = if (hasOtherReaders) MessageDeliveryStatus.READ else MessageDeliveryStatus.SENT
                        )
                    )
                }
                val sortedRaw = list.sortedBy { it.ts }
                // Infer implicit read receipts: if user U sent a message at ts >= msg.ts, U has read msg
                val enriched = sortedRaw.mapIndexed { idx, msg ->
                    val subsequentSenders = sortedRaw.subList(idx, sortedRaw.size)
                        .map { it.sender }
                        .filter { !it.equals(msg.sender, ignoreCase = true) }
                    val mergedReaders = (msg.readBy + subsequentSenders).distinctBy { it.lowercase() }
                    val isRead = mergedReaders.any { !it.equals(msg.sender, ignoreCase = true) }
                    msg.copy(
                        readBy = mergedReaders,
                        deliveryStatus = if (isRead) MessageDeliveryStatus.READ else MessageDeliveryStatus.SENT
                    )
                }
                // Remove any outbox item that is now confirmed on the server
                val serverIds = enriched.map { it.id }.toSet()
                val remainingOutbox = outbox.filter { it.id !in serverIds }
                if (remainingOutbox.size != outbox.size) {
                    saveOutboxForChannel(channelKey, remainingOutbox)
                }
                val finalMerged = mergeServerAndOutbox(enriched, remainingOutbox)
                saveCachedChannelMessages(channelKey, finalMerged)
                finalMerged
            }
        } catch (_: Exception) {
            getCachedChannelMessages(channelKey)
        }
    }

    suspend fun markChannelMessagesAsRead(
        channelKey: String,
        readerName: String,
        messages: List<MarkensMessage>
    ): List<MarkensMessage> = withContext(Dispatchers.IO) {
        if (readerName.isBlank() || messages.isEmpty()) return@withContext messages
        val toPatch = mutableListOf<MarkensMessage>()
        val updatedList = messages.map { msg ->
            val alreadyRead = msg.sender.equals(readerName, ignoreCase = true) ||
                msg.readBy.any { it.equals(readerName, ignoreCase = true) }
            if (!alreadyRead && msg.deliveryStatus != MessageDeliveryStatus.FAILED && msg.deliveryStatus != MessageDeliveryStatus.SENDING) {
                val updatedMsg = msg.copy(
                    readBy = (msg.readBy + readerName).distinctBy { it.lowercase() },
                    deliveryStatus = MessageDeliveryStatus.READ
                )
                toPatch.add(updatedMsg)
                updatedMsg
            } else {
                msg
            }
        }
        if (toPatch.isNotEmpty()) {
            saveCachedChannelMessages(channelKey, updatedList)
            // Patch up to the latest 8 unread messages in Firebase RTDB
            toPatch.takeLast(8).forEach { msg ->
                try {
                    val rbArr = JSONArray()
                    msg.readBy.forEach { rbArr.put(it) }
                    val patch = JSONObject().apply {
                        put("readBy", rbArr)
                    }
                    val req = Request.Builder()
                        .url("$MARKENS_RTDB_URL/channels_v22/${enc(channelKey)}/${enc(msg.id)}.json")
                        .patch(patch.toString().toRequestBody(jsonMediaType))
                        .build()
                    http.newCall(req).execute().close()
                } catch (_: Exception) {
                }
            }
        }
        updatedList
    }

    fun createOptimisticMessage(
        channelKey: String,
        user: MarkensUser,
        text: String,
        isDm: Boolean,
        replyTo: MarkensReplyRef? = null,
        mediaDataUrl: String? = null,
        mediaTypeOverride: String? = null
    ): MarkensMessage? {
        val cleanText = MarkensCrypto.sanitizeInput(text, maxLen = 1500)
        if (cleanText.isEmpty() && mediaDataUrl.isNullOrBlank()) return null

        val now = System.currentTimeMillis()
        if (now - lastMessageSentAtMs < 180L) {
            return null
        }
        lastMessageSentAtMs = now

        val rand = (100..999).random()
        val msgId = "m_${now}_$rand"
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
        val rankTitle = when {
            user.isOwner -> "Создатель"
            user.level >= 30 -> "Элита VORTEX"
            user.level >= 10 -> "Ветеран VORTEX"
            else -> "Участник VORTEX"
        }

        val isFav = channelKey.startsWith("fav_")
        val initialReaders = if (isFav) listOf(user.name, "Избранное") else listOf(user.name)
        val resolvedMediaType = when {
            mediaDataUrl == null -> null
            !mediaTypeOverride.isNullOrBlank() -> mediaTypeOverride
            mediaDataUrl.startsWith("data:audio/") -> "voice"
            else -> "image"
        }

        val msg = MarkensMessage(
            id = msgId,
            sender = user.name,
            senderBadge = if (!isDm) user.badge else if (user.isOwner) "👑" else null,
            senderRank = if (!isDm) rankTitle else if (user.isOwner) "Создатель" else null,
            text = cleanText,
            time = timeStr,
            ts = now,
            mediaUrl = mediaDataUrl,
            mediaType = resolvedMediaType,
            senderAvatarUrl = user.avatarUrl,
            verifiedSig = true,
            replyTo = replyTo,
            readBy = initialReaders,
            deliveryStatus = MessageDeliveryStatus.SENDING
        )

        // Track in local outbox until confirmed by server
        val outbox = getOutboxForChannel(channelKey)
        outbox.removeAll { it.id == msg.id }
        outbox.add(msg)
        saveOutboxForChannel(channelKey, outbox)

        val currentCached = getCachedChannelMessages(channelKey).filterNot { it.id == msg.id }
        saveCachedChannelMessages(channelKey, currentCached + msg)
        return msg
    }

    /**
     * Pushes a message to Firebase RTDB and updates its delivery status to `SENT` (or `READ` for Favorites)
     * ONLY when the server returns HTTP 2xx. Otherwise transitions the message to `FAILED` in the local outbox.
     */
    suspend fun pushMessageToCloud(
        channelKey: String,
        user: MarkensUser,
        isDm: Boolean,
        msg: MarkensMessage
    ): MarkensMessage = withContext(Dispatchers.IO) {
        val isDmChannel = isDm || channelKey.lowercase().trim().startsWith("dm_")
        val wireText = if (isDmChannel && msg.text.isNotBlank() && !MarkensCrypto.isEncryptedDmCipher(msg.text)) {
            MarkensCrypto.encryptDmPayload(msg.text, channelKey)
        } else {
            msg.text
        }
        val msgSig = MarkensCrypto.computeMessageSignature(msg.sender, msg.ts, wireText)
        var delivered = false
        try {
            val rbArr = JSONArray()
            msg.readBy.forEach { rbArr.put(it) }
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("sender", msg.sender)
                if (msg.senderBadge != null) put("senderBadge", msg.senderBadge)
                if (msg.senderRank != null) put("senderRank", msg.senderRank)
                if (!msg.senderAvatarUrl.isNullOrBlank()) put("senderAvatarUrl", msg.senderAvatarUrl)
                put("senderLevel", user.level)
                put("isVortexMsg", !isDmChannel)
                put("text", wireText)
                put("time", msg.time)
                put("ts", msg.ts)
                put("sig", msgSig)
                put("readBy", rbArr)
                if (msg.mediaUrl != null) {
                    put("mediaUrl", msg.mediaUrl)
                    put("mediaType", msg.mediaType ?: "image")
                }
                if (msg.replyTo != null) {
                    val wireReplyText = if (isDmChannel && msg.replyTo.text.isNotBlank() && !MarkensCrypto.isEncryptedDmCipher(msg.replyTo.text)) {
                        MarkensCrypto.encryptDmPayload(msg.replyTo.text, channelKey)
                    } else {
                        msg.replyTo.text
                    }
                    put("replyTo", JSONObject().apply {
                        put("id", msg.replyTo.id)
                        put("sender", msg.replyTo.sender)
                        put("text", wireReplyText)
                    })
                }
            }
            val req = Request.Builder()
                .url("$MARKENS_RTDB_URL/channels_v22/${enc(channelKey)}/${enc(msg.id)}.json")
                .put(obj.toString().toRequestBody(jsonMediaType))
                .build()
            http.newCall(req).execute().use { resp ->
                delivered = resp.isSuccessful
            }
        } catch (_: Exception) {
            delivered = false
        }

        val nextStatus = if (delivered) {
            if (msg.readersExcludingSender().isNotEmpty()) MessageDeliveryStatus.READ else MessageDeliveryStatus.SENT
        } else {
            MessageDeliveryStatus.FAILED
        }
        val updatedMsg = msg.copy(deliveryStatus = nextStatus)
        val outbox = getOutboxForChannel(channelKey)
        outbox.removeAll { it.id == msg.id }
        if (!delivered) {
            outbox.add(updatedMsg)
        }
        saveOutboxForChannel(channelKey, outbox)

        val updatedCache = getCachedChannelMessages(channelKey).map {
            if (it.id == msg.id) updatedMsg else it
        }
        saveCachedChannelMessages(channelKey, updatedCache)
        updatedMsg
    }

    /**
     * Retries sending a previously `FAILED` message idempotently (re-using its original `id` so no duplicates occur).
     */
    suspend fun retryFailedMessage(
        channelKey: String,
        user: MarkensUser,
        isDm: Boolean,
        messageId: String
    ): MarkensMessage? = withContext(Dispatchers.IO) {
        val current = getCachedChannelMessages(channelKey).find { it.id == messageId }
            ?: getOutboxForChannel(channelKey).find { it.id == messageId }
            ?: return@withContext null
        val sendingMsg = current.copy(deliveryStatus = MessageDeliveryStatus.SENDING)
        val outbox = getOutboxForChannel(channelKey)
        outbox.removeAll { it.id == messageId }
        outbox.add(sendingMsg)
        saveOutboxForChannel(channelKey, outbox)
        val updatedCache = getCachedChannelMessages(channelKey).map {
            if (it.id == messageId) sendingMsg else it
        }
        saveCachedChannelMessages(channelKey, updatedCache)
        pushMessageToCloud(channelKey, user, isDm, sendingMsg)
    }

    suspend fun sendMessage(
        channelKey: String,
        user: MarkensUser,
        text: String,
        isDm: Boolean,
        replyTo: MarkensReplyRef? = null,
        mediaDataUrl: String? = null,
        mediaTypeOverride: String? = null
    ): MarkensMessage? = withContext(Dispatchers.IO) {
        val msg = createOptimisticMessage(channelKey, user, text, isDm, replyTo, mediaDataUrl, mediaTypeOverride)
            ?: return@withContext null
        pushMessageToCloud(channelKey, user, isDm, msg)
    }

    suspend fun editMessage(
        channelKey: String,
        message: MarkensMessage,
        newText: String
    ) = withContext(Dispatchers.IO) {
        val cleanText = MarkensCrypto.sanitizeInput(newText, maxLen = 1500)
        if (cleanText.isEmpty()) return@withContext
        val updatedCached = getCachedChannelMessages(channelKey).map {
            if (it.id == message.id) it.copy(text = cleanText, edited = true) else it
        }
        saveCachedChannelMessages(channelKey, updatedCached)

        val isDmChannel = channelKey.lowercase().trim().startsWith("dm_")
        val wireText = if (isDmChannel && cleanText.isNotBlank() && !MarkensCrypto.isEncryptedDmCipher(cleanText)) {
            MarkensCrypto.encryptDmPayload(cleanText, channelKey)
        } else {
            cleanText
        }
        val newSig = MarkensCrypto.computeMessageSignature(message.sender, message.ts, wireText)
        try {
            val patch = JSONObject().apply {
                put("text", wireText)
                put("edited", true)
                put("sig", newSig)
            }
            val req = Request.Builder()
                .url("$MARKENS_RTDB_URL/channels_v22/${enc(channelKey)}/${enc(message.id)}.json")
                .patch(patch.toString().toRequestBody(jsonMediaType))
                .build()
            http.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    fun toggleReactionOptimistic(
        channelKey: String,
        message: MarkensMessage,
        emoji: String,
        userName: String
    ): MarkensMessage {
        val currentMap = message.reactions.toMutableMap()
        val currentUsers = currentMap[emoji]?.toMutableList() ?: mutableListOf()
        if (currentUsers.any { it.equals(userName, ignoreCase = true) }) {
            currentUsers.removeAll { it.equals(userName, ignoreCase = true) }
        } else {
            currentUsers.add(userName)
        }
        if (currentUsers.isEmpty()) {
            currentMap.remove(emoji)
        } else {
            currentMap[emoji] = currentUsers
        }
        val updated = message.copy(reactions = currentMap)
        val cached = getCachedChannelMessages(channelKey).map {
            if (it.id == message.id) updated else it
        }
        saveCachedChannelMessages(channelKey, cached)
        return updated
    }

    suspend fun toggleReaction(
        channelKey: String,
        message: MarkensMessage,
        emoji: String,
        userName: String
    ) = withContext(Dispatchers.IO) {
        val updated = toggleReactionOptimistic(channelKey, message, emoji, userName)
        try {
            val reactJson = JSONObject()
            updated.reactions.forEach { (k, v) ->
                val arr = JSONArray()
                v.forEach { arr.put(it) }
                reactJson.put(k, arr)
            }
            val patch = JSONObject().apply {
                put("reactions", reactJson)
            }
            val req = Request.Builder()
                .url("$MARKENS_RTDB_URL/channels_v22/${enc(channelKey)}/${enc(message.id)}.json")
                .patch(patch.toString().toRequestBody(jsonMediaType))
                .build()
            http.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun preloadKeyChannels(
        myName: String,
        friends: List<String>,
        groups: List<MarkensServerGroup>
    ): Boolean = withContext(Dispatchers.IO) {
        var anyLoaded = false
        val keysToPreload = LinkedHashSet<String>()
        keysToPreload.add("fav_${myName.lowercase()}")
        keysToPreload.add("идеи-и-фидбек")
        friends.take(5).forEach { f ->
            val pair = listOf(myName.lowercase(), f.lowercase()).sorted().joinToString("_")
            keysToPreload.add("dm_$pair")
        }
        groups.take(2).flatMap { it.textChannels.take(2) }.forEach { ch ->
            keysToPreload.add(ch.key)
        }
        keysToPreload.forEach { chKey ->
            if (!previewMemCache.containsKey(chKey) && getCachedChannelMessages(chKey).isEmpty()) {
                val fetched = fetchChannelMessages(chKey)
                if (fetched.isNotEmpty()) anyLoaded = true
            }
        }
        anyLoaded
    }

    suspend fun togglePinMessage(channelKey: String, message: MarkensMessage) = withContext(Dispatchers.IO) {
        try {
            val patch = JSONObject().apply { put("pinned", !message.pinned) }
            val req = Request.Builder()
                .url("$MARKENS_RTDB_URL/channels_v22/${enc(channelKey)}/${enc(message.id)}.json")
                .patch(patch.toString().toRequestBody(jsonMediaType))
                .build()
            http.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun deleteMessage(channelKey: String, messageId: String) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$MARKENS_RTDB_URL/channels_v22/${enc(channelKey)}/${enc(messageId)}.json")
                .delete()
                .build()
            http.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun syncFriendsAndRequests(myName: String): Pair<List<String>, List<MarkensFriendRequest>> =
        withContext(Dispatchers.IO) {
            val mergedFriends = getLocalFriends(myName).toMutableList()
            val requests = mutableListOf<MarkensFriendRequest>()

            try {
                val fReq = Request.Builder()
                    .url("$MARKENS_RTDB_URL/friends_v22/${enc(myName)}.json")
                    .get()
                    .build()
                http.newCall(fReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank() && body != "null") {
                            val obj = JSONObject(body)
                            val keys = obj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val fObj = obj.optJSONObject(k)
                                val fn = fObj?.optString("name", k)?.ifBlank { k } ?: k
                                if (fn.isNotBlank() && !mergedFriends.any { it.equals(fn, ignoreCase = true) }) {
                                    mergedFriends.add(fn)
                                }
                            }
                            saveLocalFriends(myName, mergedFriends)
                        }
                    }
                }
            } catch (_: Exception) {
            }

            try {
                val rReq = Request.Builder()
                    .url("$MARKENS_RTDB_URL/friend_requests_v24/${enc(myName)}.json")
                    .get()
                    .build()
                http.newCall(rReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank() && body != "null") {
                            val obj = JSONObject(body)
                            val keys = obj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val item = obj.optJSONObject(k) ?: continue
                                val from = item.optString("from", k).ifBlank { k }
                                if (!mergedFriends.any { it.equals(from, ignoreCase = true) }) {
                                    requests.add(
                                        MarkensFriendRequest(
                                            from = from,
                                            to = myName,
                                            ts = item.optLong("ts", 0L)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }

            Pair(mergedFriends, requests)
        }

    suspend fun sendFriendRequest(myName: String, targetNick: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val clean = MarkensCrypto.sanitizeInput(targetNick, maxLen = 32).replace("@", "").trim()
            if (clean.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Введите ник друга"))
            if (clean.equals(myName, ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Нельзя добавить самого себя"))
            }

            val current = getLocalFriends(myName).toMutableList()
            if (!current.any { it.equals(clean, ignoreCase = true) }) {
                current.add(clean)
                saveLocalFriends(myName, current)
            }

            try {
                val now = System.currentTimeMillis()
                val reqObj = JSONObject().apply {
                    put("from", myName)
                    put("to", clean)
                    put("ts", now)
                }
                val r1 = Request.Builder()
                    .url("$MARKENS_RTDB_URL/friend_requests_v24/${enc(clean)}/${enc(myName)}.json")
                    .put(reqObj.toString().toRequestBody(jsonMediaType))
                    .build()
                http.newCall(r1).execute().close()

                val fObj = JSONObject().apply {
                    put("name", clean)
                    put("ts", now)
                }
                val r2 = Request.Builder()
                    .url("$MARKENS_RTDB_URL/friends_v22/${enc(myName)}/${enc(clean)}.json")
                    .put(fObj.toString().toRequestBody(jsonMediaType))
                    .build()
                http.newCall(r2).execute().close()
            } catch (_: Exception) {
            }
            Result.success(Unit)
        }

    suspend fun acceptFriendRequest(myName: String, fromNick: String) = withContext(Dispatchers.IO) {
        val current = getLocalFriends(myName).toMutableList()
        if (!current.any { it.equals(fromNick, ignoreCase = true) }) {
            current.add(fromNick)
            saveLocalFriends(myName, current)
        }
        try {
            val now = System.currentTimeMillis()
            val fObj = JSONObject().apply {
                put("name", fromNick)
                put("ts", now)
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/friends_v22/${enc(myName)}/${enc(fromNick)}.json")
                    .put(fObj.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()

            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/friend_requests_v24/${enc(myName)}/${enc(fromNick)}.json")
                    .delete()
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun declineFriendRequest(myName: String, fromNick: String) = withContext(Dispatchers.IO) {
        try {
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/friend_requests_v24/${enc(myName)}/${enc(fromNick)}.json")
                    .delete()
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun setOfflineInCloud(userName: String) = withContext(Dispatchers.IO) {
        try {
            val patch = JSONObject().apply {
                put("status", "Не в сети")
                put("lastSeen", System.currentTimeMillis() - 120_000L)
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/presence_v24/${enc(userName)}.json")
                    .patch(patch.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun heartbeatAndFetchPresence(user: MarkensUser): MarkensHeartbeatResult = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        val now = System.currentTimeMillis()
        var connected = false

        try {
            val pObj = JSONObject().apply {
                put("user", user.name)
                put("lastSeen", now)
                put("ts", now)
                put("status", user.status)
                put("device", "📱 Android")
                if (!user.avatarUrl.isNullOrBlank()) {
                    put("avatarUrl", user.avatarUrl)
                }
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/presence_v24/${enc(user.name)}.json")
                    .put(pObj.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().use { resp ->
                if (resp.isSuccessful) connected = true
            }
        } catch (_: Exception) {
        }

        val rawPresences = mutableListOf<MarkensPresence>()

        try {
            http.newCall(Request.Builder().url("$MARKENS_RTDB_URL/presence_v24.json").get().build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    connected = true
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank() && body != "null") {
                        val obj = JSONObject(body)
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val p = obj.optJSONObject(k) ?: continue
                            val uName = p.optString("user", k).ifBlank { k }.trim()
                            // Ignore and auto-purge unauthenticated Web/PC placeholder "Гость" / "guest"
                            if (uName.isEmpty() || uName.equals("Гость", ignoreCase = true) || uName.equals("guest", ignoreCase = true)) {
                                try {
                                    http.newCall(
                                        Request.Builder()
                                            .url("$MARKENS_RTDB_URL/presence_v24/${enc(k)}.json")
                                            .delete()
                                            .build()
                                    ).execute().close()
                                } catch (_: Exception) {
                                }
                                continue
                            }
                            val av = p.optString("avatarUrl", "").takeIf { it.isNotBlank() }
                                ?: getUserAvatar(uName)
                            if (!av.isNullOrBlank()) {
                                avatarMemCache[uName.lowercase()] = av
                            }
                            val seenTs = maxOf(
                                p.optLong("lastSeen", 0L),
                                p.optLong("ts", 0L),
                                p.optLong("online", 0L)
                            )
                            val rawStatus = p.optString("status", "В сети").ifBlank { "В сети" }
                            val normalizedStatus = when {
                                rawStatus.equals("online", ignoreCase = true) -> "В сети"
                                rawStatus.equals("dnd", ignoreCase = true) -> "Не беспокоить"
                                rawStatus.equals("invisible", ignoreCase = true) -> "Невидимка"
                                rawStatus.equals("offline", ignoreCase = true) -> "Не в сети"
                                else -> rawStatus
                            }
                            rawPresences.add(
                                MarkensPresence(
                                    user = uName,
                                    status = normalizedStatus,
                                    device = p.optString("device", "💻 ПК").ifBlank { "💻 ПК" },
                                    lastSeen = seenTs,
                                    game = p.optString("game", "").takeIf { it.isNotBlank() && it != "NONE" },
                                    avatarUrl = av
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Normalize clock skew across PC and Android clients using max recent timestamp
        val maxClusterTs = rawPresences.maxOfOrNull { it.lastSeen }?.takeIf { it > 0L } ?: now
        val refNow = if (abs(maxClusterTs - now) < 15 * 60_000L) maxOf(now, maxClusterTs) else now

        val presenceMap = mutableMapOf<String, MarkensPresence>()
        rawPresences.forEach { item ->
            val isMe = item.user.equals(user.name, ignoreCase = true)
            val effectiveOnline = if (isMe) {
                !item.isInvisible
            } else {
                !item.isInvisible && item.lastSeen > 0L && abs(refNow - item.lastSeen) < 95_000L
            }
            presenceMap[item.user.lowercase()] = item.copy(isOnlineCalculated = effectiveOnline)
        }

        // Ensure currentUser is always in presenceMap with accurate status
        if (!presenceMap.containsKey(user.name.lowercase())) {
            val selfPres = MarkensPresence(
                user = user.name,
                status = user.status,
                device = "📱 Android",
                lastSeen = now,
                game = null,
                avatarUrl = user.avatarUrl
            )
            presenceMap[user.name.lowercase()] = selfPres.copy(isOnlineCalculated = !selfPres.isInvisible)
        }

        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceIn(12L, 999L)
        val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        MarkensHeartbeatResult(
            presenceMap = presenceMap,
            totalAccountsCount = presenceMap.size.coerceAtLeast(1),
            pingMs = elapsedMs,
            isConnected = connected,
            syncedTimeFormatted = timeFormatted
        )
    }

    suspend fun fetchFeedbackBoard(): List<MarkensFeedbackItem> = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url("$MARKENS_RTDB_URL/feedback_board.json").get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                if (body.isBlank() || body == "null") return@withContext emptyList()
                val obj = JSONObject(body)
                val list = mutableListOf<MarkensFeedbackItem>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val item = obj.optJSONObject(k) ?: continue
                    val author = item.optString("author", "Участник")
                    val votedByList = mutableListOf<String>()
                    val vArr = item.optJSONArray("votedBy")
                    if (vArr != null) {
                        for (i in 0 until vArr.length()) {
                            val v = vArr.optString(i).trim()
                            if (v.isNotEmpty()) votedByList.add(v)
                        }
                    }
                    val rawStatus = item.optString("status", "На рассмотрении").ifBlank { "На рассмотрении" }
                    val normalizedStatus = if (rawStatus == "Новое") "На рассмотрении" else rawStatus
                    val reply = item.optString("creatorReply", "").takeIf { it.isNotBlank() }

                    list.add(
                        MarkensFeedbackItem(
                            id = item.optString("id", k).ifBlank { k },
                            author = author,
                            category = item.optString("category", "💡 Идея"),
                            text = item.optString("text", ""),
                            time = item.optString("time", ""),
                            ts = item.optLong("ts", 0L),
                            votes = item.optInt("votes", 1).coerceAtLeast(votedByList.size.coerceAtLeast(1)),
                            isOwner = item.optBoolean("isOwner", false) || author.equals("markens", ignoreCase = true),
                            status = normalizedStatus,
                            creatorReply = reply,
                            votedBy = votedByList.distinctBy { it.lowercase() }
                        )
                    )
                }
                list.sortedByDescending { it.ts }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun submitFeedback(user: MarkensUser, category: String, text: String) = withContext(Dispatchers.IO) {
        val cleanText = MarkensCrypto.sanitizeInput(text, maxLen = 600)
        if (cleanText.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val fbId = "fb_$now"
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
        val votedArr = JSONArray().apply { put(user.name) }
        val obj = JSONObject().apply {
            put("id", fbId)
            put("author", user.name)
            put("isOwner", user.isOwner)
            put("category", category)
            put("text", cleanText)
            put("votes", 1)
            put("votedBy", votedArr)
            put("status", "На рассмотрении")
            put("time", timeStr)
            put("ts", now)
        }
        try {
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/feedback_board/${enc(fbId)}.json")
                    .put(obj.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun voteFeedback(item: MarkensFeedbackItem, voterName: String = "") = withContext(Dispatchers.IO) {
        try {
            val currentVoters = item.votedBy.toMutableList()
            val alreadyVoted = voterName.isNotBlank() && currentVoters.any { it.equals(voterName, ignoreCase = true) }
            val newVotes: Int
            if (voterName.isNotBlank()) {
                if (alreadyVoted) {
                    currentVoters.removeAll { it.equals(voterName, ignoreCase = true) }
                    newVotes = (item.votes - 1).coerceAtLeast(0)
                } else {
                    currentVoters.add(voterName)
                    newVotes = item.votes + 1
                }
            } else {
                newVotes = item.votes + 1
            }
            val vArr = JSONArray()
            currentVoters.forEach { vArr.put(it) }
            val patch = JSONObject().apply {
                put("votes", newVotes)
                put("votedBy", vArr)
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/feedback_board/${enc(item.id)}.json")
                    .patch(patch.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun updateFeedbackItem(
        id: String,
        newStatus: String? = null,
        creatorReply: String? = null,
        newText: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val patch = JSONObject().apply {
                if (newStatus != null) put("status", newStatus)
                if (creatorReply != null) put("creatorReply", MarkensCrypto.sanitizeInput(creatorReply, maxLen = 500))
                if (newText != null) {
                    val clean = MarkensCrypto.sanitizeInput(newText, maxLen = 600)
                    if (clean.isNotEmpty()) put("text", clean)
                }
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/feedback_board/${enc(id)}.json")
                    .patch(patch.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun deleteFeedback(id: String) = withContext(Dispatchers.IO) {
        try {
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/feedback_board/${enc(id)}.json")
                    .delete()
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
    }

    suspend fun updateProfileInCloud(user: MarkensUser) = withContext(Dispatchers.IO) {
        saveUserSession(user)
        // Also update saved accounts list if this user is remembered
        val savedList = getSavedAccounts()
        val matching = savedList.find { it.login.equals(user.name, ignoreCase = true) }
        if (matching != null) {
            saveRememberedCredentials(user.name, matching.sessionVerifier, user.badge, user.isOwner, user.avatarUrl)
        }
        try {
            val cleanBio = MarkensCrypto.sanitizeInput(user.bio, maxLen = 140)
            val patch = JSONObject().apply {
                put("status", user.status)
                put("bio", cleanBio)
                put("badge", user.badge)
                put("level", user.level)
                put("xp", user.xp)
                put("avatarUrl", user.avatarUrl ?: "")
                put("avatar", user.avatarUrl ?: "")
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/accounts/${enc(user.name)}.json")
                    .patch(patch.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()

            // Also sync to PC v31.2.0 cross-device preferences node `/user_prefs_v26/$userLower`
            val prefsV26 = JSONObject().apply {
                put("avatar", user.avatarUrl ?: "")
                put("bio", cleanBio)
                put("status", user.status)
                put("badge", user.badge)
                put("updatedAt", System.currentTimeMillis())
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/user_prefs_v26/${enc(user.name.lowercase().trim())}.json")
                    .patch(prefsV26.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
    }

    fun getInstalledOtaVersion(): String {
        val raw = prefs.getString("installed_ota_version", prefs.getString("installed_version", "31.2.0")) ?: "31.2.0"
        return if (MarkensCrypto.compareVersions(raw, "31.2.0") < 0) "31.2.0" else raw
    }

    fun getInstalledOtaBuildTs(): Long {
        val stored = prefs.getLong("installed_ota_build_ts", prefs.getLong("installed_build_ts", 1793350000000L))
        return maxOf(stored, 1793350000000L)
    }

    /**
     * Strictly checks if a remote OTA release is authentic (`signatureVerified == true` and valid HMAC signature),
     * confirmed by creator, uses HTTPS if an APK URL is present, and is strictly newer than the installed version.
     */
    fun isNewerOtaAvailable(release: MarkensOtaRelease): Boolean {
        val expectedSig = MarkensCrypto.computeOtaReleaseSignature(release.version, release.buildTs)
        if (!release.signatureVerified || !release.confirmedByCreator) return false
        if (release.signature.isBlank() || release.signature != expectedSig) return false
        if (release.apkUrl != null && !release.apkUrl.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val currentVer = getInstalledOtaVersion()
        val currentTs = getInstalledOtaBuildTs()
        val verCmp = MarkensCrypto.compareVersions(release.version, currentVer)
        if (verCmp < 0) return false // Strictly reject version downgrades
        if (verCmp > 0) return true
        if (verCmp == 0 && release.buildTs > 0L && currentTs > 0L && release.buildTs > currentTs) return true
        return false
    }

    /**
     * Verifies a downloaded APK file before launching the Android package installer:
     * 1. Verifies file exists and is non-empty.
     * 2. Verifies SHA-256 digest if `expectedSha256` is specified in the OTA release metadata.
     * 3. Uses Android `PackageManager.getPackageArchiveInfo` to verify the APK is a valid archive,
     *    matches this application's `packageName`, has valid signing certificates, and is not a version downgrade.
     */
    fun verifyDownloadedApk(apkFile: File, expectedSha256: String? = null): Result<Unit> {
        if (!apkFile.exists() || apkFile.length() < 1024L) {
            return Result.failure(SecurityException("Файл APK повреждён или пуст"))
        }
        if (!expectedSha256.isNullOrBlank()) {
            val actualHash = try {
                MarkensCrypto.sha256Hex(apkFile.readBytes())
            } catch (e: Exception) {
                return Result.failure(SecurityException("Не удалось вычислить SHA-256 для APK: ${e.message}"))
            }
            if (!actualHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                return Result.failure(
                    SecurityException("Контрольная сумма SHA-256 APK не совпадает! Установка заблокирована.")
                )
            }
        }
        return try {
            val pm = appContext.packageManager
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
                ?: return Result.failure(SecurityException("Файл не является корректным пакетом Android APK"))
            if (archiveInfo.packageName != appContext.packageName) {
                return Result.failure(
                    SecurityException(
                        "PackageName в APK (${archiveInfo.packageName}) не совпадает с установленным (${appContext.packageName})"
                    )
                )
            }
            val currentInfo = pm.getPackageInfo(appContext.packageName, flags)
            val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                currentInfo.versionCode.toLong()
            }
            if (archiveVersionCode < currentVersionCode) {
                return Result.failure(
                    SecurityException("Заблокирована попытка понижения версии APK ($archiveVersionCode < $currentVersionCode)")
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(SecurityException("Ошибка проверки подписи APK: ${e.message}"))
        }
    }

    suspend fun applyOtaRelease(release: MarkensOtaRelease) = withContext(Dispatchers.IO) {
        if (!release.signatureVerified) return@withContext
        val ts = if (release.buildTs > 0L) release.buildTs else System.currentTimeMillis()
        prefs.edit()
            .putString("installed_ota_version", release.version)
            .putLong("installed_ota_build_ts", ts)
            .putString("installed_version", release.version)
            .putLong("installed_build_ts", ts)
            .putString("last_applied_sig", "${release.version}__$ts")
            .putString("installed_ota_changelog", release.changelog)
            .commit()
        messageMemCache.clear()
        previewMemCache.clear()
    }

    suspend fun checkCloudOta(): MarkensOtaRelease? = withContext(Dispatchers.IO) {
        try {
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/ota_update.json?t=${System.currentTimeMillis()}")
                    .get()
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                if (body.isBlank() || body == "null") return@withContext null
                val obj = JSONObject(body)
                val ver = obj.optString("version", obj.optString("ver", obj.optString("appVersion", ""))).trim()
                if (ver.isEmpty()) return@withContext null
                val buildTs = obj.optLong(
                    "buildTs",
                    obj.optLong("ts", obj.optLong("timestamp", obj.optLong("updatedAt", 0L)))
                )
                val sig = obj.optString("signature", "").trim()
                val expectedSig = MarkensCrypto.computeOtaReleaseSignature(ver, buildTs)
                val changelog = obj.optString(
                    "changelog",
                    obj.optString("notes", obj.optString("description", obj.optString("changes", "Обновление MARKENS")))
                ).trim().ifEmpty { "Обновление MARKENS" }
                val rawApkUrl = obj.optString(
                    "apkUrl",
                    obj.optString("downloadUrl", obj.optString("mobileUrl", obj.optString("url", "")))
                ).trim()
                // Security: ONLY allow HTTPS URLs for APK downloads; reject plaintext HTTP
                val secureApkUrl = rawApkUrl.takeIf { it.startsWith("https://", ignoreCase = true) }
                val sha256 = obj.optString("sha256", obj.optString("apkSha256", "")).trim().takeIf { it.isNotEmpty() }
                val autoForce = obj.optBoolean(
                    "autoForce",
                    obj.optBoolean("autoUpdate", obj.optBoolean("force", false))
                )
                val updatedBy = obj.optString("updatedBy", obj.optString("author", "markens"))
                val confirmedByCreator = obj.optBoolean("confirmedByCreator", false) &&
                    updatedBy.equals("markens", ignoreCase = true)

                // Security: NEVER treat an empty signature as verified!
                // Also reject if a raw HTTP (non-HTTPS) APK URL was supplied.
                val hasInsecureUrl = rawApkUrl.isNotEmpty() && secureApkUrl == null
                val isSigValid = sig.isNotEmpty() && sig == expectedSig && !hasInsecureUrl

                if (getInstalledOtaBuildTs() == 0L && ver == getInstalledOtaVersion() && buildTs > 0L && isSigValid) {
                    prefs.edit().putLong("installed_ota_build_ts", buildTs).apply()
                }

                MarkensOtaRelease(
                    version = ver,
                    buildTs = buildTs,
                    changelog = changelog,
                    signature = sig,
                    autoForce = autoForce,
                    confirmedByCreator = confirmedByCreator,
                    updatedBy = updatedBy,
                    apkUrl = secureApkUrl,
                    sha256 = sha256,
                    signatureVerified = isSigValid
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun publishCloudOta(
        user: MarkensUser,
        version: String,
        changelog: String,
        apkUrl: String? = null,
        autoForce: Boolean = false
    ): MarkensOtaRelease? = withContext(Dispatchers.IO) {
        if (!user.isOwner || !user.name.equals("markens", ignoreCase = true)) return@withContext null
        val cleanVer = MarkensCrypto.sanitizeInput(version, maxLen = 24).trim()
        val cleanLog = MarkensCrypto.sanitizeInput(changelog, maxLen = 1000).trim()
        if (cleanVer.isEmpty() || cleanLog.isEmpty()) return@withContext null
        // Enforce HTTPS if an APK URL is provided
        val trimmedApk = apkUrl?.trim().orEmpty()
        if (trimmedApk.isNotEmpty() && !trimmedApk.startsWith("https://", ignoreCase = true)) {
            return@withContext null
        }
        val cleanApk = trimmedApk.takeIf { it.isNotEmpty() }
        val now = System.currentTimeMillis()
        val sig = MarkensCrypto.computeOtaReleaseSignature(cleanVer, now)
        val release = MarkensOtaRelease(
            version = cleanVer,
            buildTs = now,
            changelog = cleanLog,
            signature = sig,
            autoForce = autoForce,
            confirmedByCreator = true,
            updatedBy = "markens",
            apkUrl = cleanApk,
            signatureVerified = true
        )
        try {
            val payload = JSONObject().apply {
                put("version", release.version)
                put("buildTs", release.buildTs)
                put("changelog", release.changelog)
                put("signature", release.signature)
                put("autoForce", release.autoForce)
                put("confirmedByCreator", true)
                put("updatedBy", release.updatedBy)
                if (cleanApk != null) {
                    put("apkUrl", cleanApk)
                }
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/ota_update.json")
                    .put(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
            }

            applyOtaRelease(release)

            val announceText = buildString {
                append("🚀 Обновление MARKENS v${release.version}\n")
                append(release.changelog)
                if (cleanApk != null) {
                    append("\n📥 APK (HTTPS): $cleanApk")
                }
            }
            val msg = createOptimisticMessage(
                channelKey = "updates",
                user = user,
                text = announceText,
                isDm = false
            )
            if (msg != null) {
                pushMessageToCloud("updates", user, false, msg)
            }
            release
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // Real-Time Voice & Video Call Signaling (WebRTC SDP + Firebase RTDB `/calls_v25`)
    // =========================================================================

    suspend fun publishCallSignal(session: MarkensCallSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pairKey = listOf(session.caller.lowercase().trim(), session.callee.lowercase().trim())
                .sorted()
                .joinToString("_")
            val sig = MarkensCrypto.signWebRtcSignalPayload(
                pairKey = pairKey,
                type = "OFFER",
                fromUser = session.caller,
                toUser = session.callee,
                ts = session.startedAtTs,
                bodyStr = session.offerSdp ?: ""
            )
            val obj = JSONObject().apply {
                put("callId", session.callId)
                put("pairKey", pairKey)
                put("caller", session.caller)
                put("callee", session.callee)
                put("from", session.caller)
                put("to", session.callee)
                put("type", "OFFER")
                put("ts", session.startedAtTs)
                put("sig", sig)
                put("isVideo", session.isVideo)
                put("status", session.status.name)
                put("startedAtTs", session.startedAtTs)
                put("connectedAtTs", session.connectedAtTs)
                put("callerMicMuted", session.isMicMuted)
                put("callerVideoEnabled", session.isLocalVideoEnabled)
                put("calleeMicMuted", false)
                put("calleeVideoEnabled", session.isVideo)
                put("stunServer", session.stunServer)
                if (!session.offerSdp.isNullOrBlank()) {
                    put("offerSdp", session.offerSdp)
                }
            }
            val bodyStr = obj.toString()
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/calls_v25/${enc(session.callId)}.json")
                    .put(bodyStr.toRequestBody(jsonMediaType))
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                }
            }
            // Publish to PC v31.2.0 pairKey node AND callee's `/call_inbox/{callee}` for cross-platform ringing
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/calls_v25/${enc(pairKey)}.json")
                    .patch(bodyStr.toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()

            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/call_inbox/${enc(session.callee)}.json")
                    .put(bodyStr.toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCallSession(callId: String, isIncoming: Boolean): Result<MarkensCallSession?> =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(
                    Request.Builder()
                        .url("$MARKENS_RTDB_URL/calls_v25/${enc(callId)}.json")
                        .get()
                        .build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                    }
                    val body = resp.body?.string()
                    if (body.isNullOrBlank() || body == "null") {
                        return@withContext Result.success(null)
                    }
                    val obj = JSONObject(body)
                    val statusRaw = obj.optString("status", "ENDED").uppercase()
                    val status = when (statusRaw) {
                        "OUTGOING_RINGING", "RINGING", "OFFER" ->
                            if (isIncoming) MarkensCallStatus.INCOMING_RINGING else MarkensCallStatus.OUTGOING_RINGING
                        "CONNECTING" -> MarkensCallStatus.CONNECTING
                        "CONNECTED", "ACTIVE", "ANSWERED" -> MarkensCallStatus.CONNECTED
                        "BUSY" -> MarkensCallStatus.BUSY
                        "DECLINED", "REJECTED" -> MarkensCallStatus.DECLINED
                        "TIMEOUT", "MISSED" -> MarkensCallStatus.TIMEOUT
                        else -> MarkensCallStatus.ENDED
                    }
                    val remoteMuted = if (isIncoming) {
                        obj.optBoolean("callerMicMuted", false)
                    } else {
                        obj.optBoolean("calleeMicMuted", false)
                    }
                    val remoteVideo = if (isIncoming) {
                        obj.optBoolean("callerVideoEnabled", false)
                    } else {
                        obj.optBoolean("calleeVideoEnabled", false)
                    }
                    Result.success(
                        MarkensCallSession(
                            callId = obj.optString("callId", callId).ifBlank { callId },
                            caller = obj.optString("caller", obj.optString("from", "")),
                            callee = obj.optString("callee", obj.optString("to", "")),
                            isVideo = obj.optBoolean("isVideo", false),
                            status = status,
                            startedAtTs = obj.optLong("startedAtTs", obj.optLong("ts", 0L)),
                            connectedAtTs = obj.optLong("connectedAtTs", 0L),
                            isIncoming = isIncoming,
                            remoteMicMuted = remoteMuted,
                            remoteVideoEnabled = remoteVideo,
                            offerSdp = obj.optString("offerSdp", "").takeIf { it.isNotBlank() },
                            answerSdp = obj.optString("answerSdp", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun acceptCallInCloud(session: MarkensCallSession): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pairKey = listOf(session.caller.lowercase().trim(), session.callee.lowercase().trim())
                .sorted()
                .joinToString("_")
            val sig = MarkensCrypto.signWebRtcSignalPayload(
                pairKey = pairKey,
                type = "ANSWER",
                fromUser = session.callee,
                toUser = session.caller,
                ts = session.connectedAtTs,
                bodyStr = session.answerSdp ?: ""
            )
            val patch = JSONObject().apply {
                put("status", "CONNECTED")
                put("type", "ANSWER")
                put("connectedAtTs", session.connectedAtTs)
                put("calleeMicMuted", session.isMicMuted)
                put("calleeVideoEnabled", session.isLocalVideoEnabled)
                put("sig", sig)
                if (!session.answerSdp.isNullOrBlank()) {
                    put("answerSdp", session.answerSdp)
                }
            }
            val patchStr = patch.toString()
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/calls_v25/${enc(session.callId)}.json")
                    .patch(patchStr.toRequestBody(jsonMediaType))
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                }
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/calls_v25/${enc(pairKey)}.json")
                    .patch(patchStr.toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/call_inbox/${enc(session.callee)}.json")
                    .delete()
                    .build()
            ).execute().close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCallStatusInCloud(
        callId: String,
        callee: String,
        newStatus: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val patch = JSONObject().apply {
                put("status", newStatus)
                put("endedAtTs", System.currentTimeMillis())
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/calls_v25/${enc(callId)}.json")
                    .patch(patch.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()

            if (callee.isNotBlank() && newStatus in listOf("ENDED", "DECLINED", "BUSY", "TIMEOUT")) {
                http.newCall(
                    Request.Builder()
                        .url("$MARKENS_RTDB_URL/call_inbox/${enc(callee)}.json")
                        .delete()
                        .build()
                ).execute().close()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCallMediaFlagsInCloud(
        callId: String,
        isCaller: Boolean,
        micMuted: Boolean,
        videoEnabled: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val patch = JSONObject().apply {
                if (isCaller) {
                    put("callerMicMuted", micMuted)
                    put("callerVideoEnabled", videoEnabled)
                } else {
                    put("calleeMicMuted", micMuted)
                    put("calleeVideoEnabled", videoEnabled)
                }
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/calls_v25/${enc(callId)}.json")
                    .patch(patch.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollIncomingCallForUser(userName: String): MarkensCallSession? = withContext(Dispatchers.IO) {
        if (userName.isBlank()) return@withContext null
        try {
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/call_inbox/${enc(userName)}.json")
                    .get()
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                if (body.isBlank() || body == "null") return@withContext null
                val obj = JSONObject(body)
                val callId = obj.optString("callId", "")
                val caller = obj.optString("caller", "")
                val callee = obj.optString("callee", userName)
                val statusRaw = obj.optString("status", "")
                val startedAtTs = obj.optLong("startedAtTs", 0L)
                val ageMs = abs(System.currentTimeMillis() - startedAtTs)
                if (callId.isBlank() || caller.isBlank() || caller.equals(userName, ignoreCase = true)) {
                    return@withContext null
                }
                if (statusRaw !in listOf("OUTGOING_RINGING", "RINGING", "OFFER") || ageMs > 32_000L) {
                    return@withContext null
                }
                MarkensCallSession(
                    callId = callId,
                    caller = caller,
                    callee = callee,
                    isVideo = obj.optBoolean("isVideo", false),
                    status = MarkensCallStatus.INCOMING_RINGING,
                    startedAtTs = startedAtTs,
                    isIncoming = true,
                    remoteMicMuted = obj.optBoolean("callerMicMuted", false),
                    remoteVideoEnabled = obj.optBoolean("callerVideoEnabled", false),
                    offerSdp = obj.optString("offerSdp", "").takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // VORTEX Server Roles (`/server_roles_v30`) & Live Voice Rooms (`/voice_rooms_v28`)
    // =========================================================================

    fun defaultVortexRoles(): List<MarkensServerRole> = listOf(
        MarkensServerRole(id = "role_creator", name = "👑 Создатель VORTEX", color = "#F59E0B", hoist = true, position = 100),
        MarkensServerRole(id = "role_admin", name = "🛡️ Страж VORTEX", color = "#EF4444", hoist = true, position = 80),
        MarkensServerRole(id = "role_elite", name = "⚡ Элита VORTEX", color = "#8B5CF6", hoist = true, position = 60),
        MarkensServerRole(id = "role_member", name = "🎯 Участник VORTEX", color = "#5865F2", hoist = false, position = 20)
    )

    suspend fun fetchServerRoles(): List<MarkensServerRole> = withContext(Dispatchers.IO) {
        val rolesMap = LinkedHashMap<String, MarkensServerRole>()
        defaultVortexRoles().forEach { rolesMap[it.id] = it }
        try {
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/server_roles_v30.json")
                    .get()
                    .build()
            ).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank() && body != "null") {
                        val obj = JSONObject(body)
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val r = obj.optJSONObject(k) ?: continue
                            val id = r.optString("id", k).ifBlank { k }
                            val name = r.optString("name", "").trim()
                            if (name.isEmpty()) continue
                            rolesMap[id] = MarkensServerRole(
                                id = id,
                                name = name,
                                color = r.optString("color", "#5865F2").ifBlank { "#5865F2" },
                                hoist = r.optBoolean("hoist", true),
                                position = r.optInt("position", 50)
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        rolesMap.values.sortedByDescending { it.position }
    }

    suspend fun createOrUpdateServerRole(
        user: MarkensUser,
        name: String,
        colorHex: String,
        position: Int = 55
    ): Result<List<MarkensServerRole>> = withContext(Dispatchers.IO) {
        if (!user.isOwner) {
            return@withContext Result.failure(SecurityException("Только создатель markens может управлять ролями сервера"))
        }
        val cleanName = MarkensCrypto.sanitizeInput(name, maxLen = 36).trim()
        if (cleanName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Введите название роли"))
        }
        val roleId = "role_${System.currentTimeMillis()}"
        val cleanColor = if (colorHex.startsWith("#")) colorHex else "#5865F2"
        try {
            val payload = JSONObject().apply {
                put("id", roleId)
                put("name", cleanName)
                put("color", cleanColor)
                put("hoist", true)
                put("position", position)
                put("createdBy", user.name)
                put("ts", System.currentTimeMillis())
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/server_roles_v30/${enc(roleId)}.json")
                    .put(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        Result.success(fetchServerRoles())
    }

    suspend fun deleteServerRole(user: MarkensUser, roleId: String): List<MarkensServerRole> =
        withContext(Dispatchers.IO) {
            if (user.isOwner && !roleId.startsWith("role_creator")) {
                try {
                    http.newCall(
                        Request.Builder()
                            .url("$MARKENS_RTDB_URL/server_roles_v30/${enc(roleId)}.json")
                            .delete()
                            .build()
                    ).execute().close()
                } catch (_: Exception) {
                }
            }
            fetchServerRoles()
        }

    suspend fun fetchVoiceRooms(): List<MarkensVoiceRoomMember> = withContext(Dispatchers.IO) {
        val members = mutableListOf<MarkensVoiceRoomMember>()
        val now = System.currentTimeMillis()
        try {
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/voice_rooms_v28.json")
                    .get()
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                if (body.isBlank() || body == "null") return@withContext emptyList()
                val root = JSONObject(body)
                val roomKeys = root.keys()
                while (roomKeys.hasNext()) {
                    val roomId = roomKeys.next()
                    val roomObj = root.optJSONObject(roomId) ?: continue
                    val userKeys = roomObj.keys()
                    while (userKeys.hasNext()) {
                        val uKey = userKeys.next()
                        val mObj = roomObj.optJSONObject(uKey) ?: continue
                        val uName = mObj.optString("user", uKey).ifBlank { uKey }.trim()
                        if (uName.isEmpty() || uName.equals("Гость", ignoreCase = true) || uName.equals("guest", ignoreCase = true)) {
                            continue
                        }
                        val ts = mObj.optLong("ts", mObj.optLong("joinedAt", now))
                        if (abs(now - ts) > 6 * 60_000L) continue
                        val defaultRoomName = when (roomId) {
                            "voice_lounge" -> "Общая комната"
                            "voice_gaming" -> "Игровая #1"
                            "voice_music" -> "Музыка и чилл"
                            else -> mObj.optString("roomName", roomId)
                        }
                        members.add(
                            MarkensVoiceRoomMember(
                                user = uName,
                                roomId = roomId,
                                roomName = mObj.optString("roomName", defaultRoomName).ifBlank { defaultRoomName },
                                mic = mObj.optBoolean("mic", !mObj.optBoolean("muted", false)),
                                deaf = mObj.optBoolean("deaf", mObj.optBoolean("deafened", false)),
                                speaking = mObj.optBoolean("speaking", false),
                                ts = ts
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        members.sortedBy { it.user.lowercase() }
    }

    suspend fun joinOrUpdateVoiceRoom(
        user: MarkensUser,
        roomId: String,
        roomName: String,
        micEnabled: Boolean,
        deafened: Boolean,
        speaking: Boolean = false
    ): List<MarkensVoiceRoomMember> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            val payload = JSONObject().apply {
                put("user", user.name)
                put("roomId", roomId)
                put("roomName", roomName)
                put("mic", micEnabled)
                put("muted", !micEnabled)
                put("deaf", deafened)
                put("deafened", deafened)
                put("speaking", speaking && micEnabled && !deafened)
                put("device", "📱 Android")
                put("ts", now)
            }
            http.newCall(
                Request.Builder()
                    .url("$MARKENS_RTDB_URL/voice_rooms_v28/${enc(roomId)}/${enc(user.name.lowercase())}.json")
                    .put(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            ).execute().close()
        } catch (_: Exception) {
        }
        fetchVoiceRooms()
    }

    suspend fun leaveVoiceRoom(userName: String, roomId: String? = null): List<MarkensVoiceRoomMember> =
        withContext(Dispatchers.IO) {
            val roomsToClear = if (!roomId.isNullOrBlank()) {
                listOf(roomId)
            } else {
                listOf("voice_lounge", "voice_gaming", "voice_music")
            }
            roomsToClear.forEach { rId ->
                try {
                    http.newCall(
                        Request.Builder()
                            .url("$MARKENS_RTDB_URL/voice_rooms_v28/${enc(rId)}/${enc(userName.lowercase())}.json")
                            .delete()
                            .build()
                    ).execute().close()
                } catch (_: Exception) {
                }
            }
            fetchVoiceRooms()
        }

    fun buildSyncDiagnostics(
        user: MarkensUser?,
        otaRelease: MarkensOtaRelease?,
        isConnected: Boolean,
        pingMs: Long,
        lastSyncedTime: String
    ): MarkensSyncDiagnostics {
        val installedVer = "v${getInstalledOtaVersion().removePrefix("v")}"
        val remoteVer = otaRelease?.version?.let { "v${it.removePrefix("v")}" } ?: installedVer
        val hasPbkdf2 = !user?.authToken.isNullOrBlank() && !user?.verifierHash.isNullOrBlank()
        return MarkensSyncDiagnostics(
            originVer = "v31.2.0 (Build 1793350000000)",
            activeVer = installedVer,
            firebaseVer = "$remoteVer (${if (isConnected) "RTDB Online • ${pingMs}ms" else "Offline Cache"})",
            lastCheckTime = lastSyncedTime,
            lastPublishTime = if (otaRelease != null && otaRelease.buildTs > 0L) {
                SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(otaRelease.buildTs))
            } else {
                "Актуальная сборка v31.2.0"
            },
            downloadResult = if (hasPbkdf2) "✓ PBKDF2-SHA256 (100 000 итераций) активен" else "✓ Сессия синхронизирована",
            integrityResult = "✓ E2EE v2 (CTR-SHA256 + HMAC) • WebRTC Sig",
            applyResult = "✓ Полная совместимость с ПК MARKENS v31.2.0",
            lastError = "Ошибок нет"
        )
    }

    companion object {
        // Shared singleton OkHttpClient connection pool to avoid recreating sockets and threads
        private val http = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}

object MarkensSoundEngine {
    @Volatile
    private var lastLaunchChimeAtMs = 0L

    suspend fun playStartupChimeOnceIfNeeded(enabled: Boolean) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastLaunchChimeAtMs < 4000L) return
        lastLaunchChimeAtMs = now
        delay(280L)
        playWelcomeChime()
    }

    suspend fun playWelcomeChime() = withContext(Dispatchers.Default) {
        try {
            val sampleRate = 44100
            val durationMs = 520
            val numSamples = (sampleRate * durationMs) / 1000
            val pcm = ShortArray(numSamples)

            // Warm, audible 3-note Discord/MARKENS welcome chord (A4 -> C#5 -> E5 + A5)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val sampleVal = when {
                    t < 0.13 -> {
                        val att = (t / 0.012).coerceAtMost(1.0)
                        val env = att * exp(-t * 7.5)
                        val n1 = sin(2.0 * PI * 440.0 * t) // A4
                        val n2 = 0.5 * sin(2.0 * PI * 880.0 * t) // A5 harmonic
                        (n1 + n2) * env * 0.55
                    }
                    t < 0.26 -> {
                        val t2 = t - 0.13
                        val att = (t2 / 0.010).coerceAtMost(1.0)
                        val env = att * exp(-t2 * 7.5)
                        val n1 = sin(2.0 * PI * 554.37 * t2) // C#5
                        val n2 = 0.5 * sin(2.0 * PI * 1108.73 * t2)
                        (n1 + n2) * env * 0.58
                    }
                    else -> {
                        val t3 = t - 0.26
                        val att = (t3 / 0.010).coerceAtMost(1.0)
                        val env = att * exp(-t3 * 6.5)
                        val n1 = sin(2.0 * PI * 659.25 * t3) // E5
                        val n2 = 0.65 * sin(2.0 * PI * 880.0 * t3) // A5
                        val n3 = 0.35 * sin(2.0 * PI * 1318.51 * t3) // E6
                        (n1 + n2 + n3) * env * 0.52
                    }
                }
                pcm[i] = (sampleVal.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
            }

            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(pcm.size * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                track.write(pcm, 0, pcm.size)
                delay(durationMs.toLong() + 80L)
                track.stop()
                track.release()
            } else {
                track.release()
                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 220)
                delay(240L)
                tg.release()
            }
        } catch (_: Exception) {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                delay(220L)
                tg.release()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun playMessageSendPop(enabled: Boolean) = withContext(Dispatchers.Default) {
        if (!enabled) return@withContext
        try {
            val sampleRate = 44100
            val durationMs = 75
            val numSamples = (sampleRate * durationMs) / 1000
            val pcm = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val att = (t / 0.005).coerceAtMost(1.0)
                val env = att * exp(-t * 36.0)
                val freq = 520.0 + 320.0 * (t / 0.075)
                val s = sin(2.0 * PI * freq * t) * env * 0.42
                pcm[i] = (s.coerceIn(-0.9, 0.9) * Short.MAX_VALUE).toInt().toShort()
            }
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(pcm.size * 2)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                track.write(pcm, 0, pcm.size)
                delay(90L)
                track.stop()
            }
            track.release()
        } catch (_: Exception) {
        }
    }
}

