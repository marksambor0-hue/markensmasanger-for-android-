package com.example.data.auth

import android.content.Context
import com.example.data.model.AccountSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class RegisteredUser(
    val userId: String,
    val login: String,
    val displayName: String,
    val bio: String = "В сети"
)

class AuthSessionManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _sessionFlow = MutableStateFlow<AccountSession?>(loadSavedSession())
    val sessionFlow: StateFlow<AccountSession?> = _sessionFlow.asStateFlow()

    private val _registeredUsersFlow = MutableStateFlow<List<RegisteredUser>>(loadAllRegisteredUsers())
    val registeredUsersFlow: StateFlow<List<RegisteredUser>> = _registeredUsersFlow.asStateFlow()

    private fun loadSavedSession(): AccountSession? {
        val activeLogin = prefs.getString(KEY_ACTIVE_LOGIN, null) ?: return null
        val userId = prefs.getString(KEY_ACTIVE_UID, null) ?: return null
        val displayName = prefs.getString(KEY_ACTIVE_DISPLAY_NAME, activeLogin) ?: activeLogin
        val syncRoom = prefs.getString(KEY_ACTIVE_SYNC_ROOM, "main") ?: "main"
        val serverUrl = prefs.getString(KEY_ACTIVE_SERVER_URL, "") ?: ""
        val isGoogle = prefs.getBoolean(KEY_ACTIVE_IS_GOOGLE, false)
        return AccountSession(
            userId = userId,
            login = activeLogin,
            displayName = displayName,
            syncRoomCode = syncRoom,
            serverUrl = serverUrl,
            isGoogleLinked = isGoogle
        )
    }

    private fun loadAllRegisteredUsers(): List<RegisteredUser> {
        val accountsRoot = try {
            JSONObject(prefs.getString(KEY_ACCOUNTS_JSON, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        val list = mutableListOf<RegisteredUser>()
        val keys = accountsRoot.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = accountsRoot.optJSONObject(key) ?: continue
            list.add(
                RegisteredUser(
                    userId = obj.optString("userId", key),
                    login = obj.optString("login", key),
                    displayName = obj.optString("displayName", key),
                    bio = obj.optString("bio", "В сети")
                )
            )
        }
        return list
    }

    fun loginWithCredentials(
        rawLogin: String,
        rawPassword: String,
        syncRoomCode: String = "main",
        serverUrl: String = ""
    ): Result<AccountSession> {
        val cleanLogin = rawLogin.trim().lowercase()
        if (cleanLogin.length < 2) {
            return Result.failure(IllegalArgumentException("Введите логин (минимум 2 символа)"))
        }
        if (rawPassword.length < 3) {
            return Result.failure(IllegalArgumentException("Введите пароль (минимум 3 символа)"))
        }

        val accountsRoot = try {
            JSONObject(prefs.getString(KEY_ACCOUNTS_JSON, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

        val inputHash = sha256(rawPassword)
        val cleanRoom = syncRoomCode.trim().ifBlank { "main" }
        val cleanServer = serverUrl.trim()

        val accountSession = if (accountsRoot.has(cleanLogin)) {
            val acc = accountsRoot.getJSONObject(cleanLogin)
            val savedHash = acc.optString("passwordHash", "")
            if (savedHash.isNotEmpty() && savedHash != inputHash) {
                return Result.failure(IllegalArgumentException("Неверный пароль"))
            }
            val uid = acc.optString("userId", "usr_${cleanLogin.replace(Regex("[^a-z0-9_]"), "_")}")
            val displayName = acc.optString("displayName", rawLogin.trim())
            acc.put("syncRoomCode", cleanRoom)
            acc.put("serverUrl", cleanServer)
            accountsRoot.put(cleanLogin, acc)
            prefs.edit().putString(KEY_ACCOUNTS_JSON, accountsRoot.toString()).apply()

            AccountSession(
                userId = uid,
                login = cleanLogin,
                displayName = displayName,
                syncRoomCode = cleanRoom,
                serverUrl = cleanServer,
                isGoogleLinked = false
            )
        } else {
            val uid = "usr_${cleanLogin.replace(Regex("[^a-z0-9_]"), "_").take(24)}_${UUID.randomUUID().toString().take(6)}"
            val displayName = rawLogin.trim().substringBefore("@")
            val newAcc = JSONObject().apply {
                put("userId", uid)
                put("login", cleanLogin)
                put("displayName", displayName)
                put("passwordHash", inputHash)
                put("syncRoomCode", cleanRoom)
                put("serverUrl", cleanServer)
                put("bio", "В сети")
            }
            accountsRoot.put(cleanLogin, newAcc)
            prefs.edit().putString(KEY_ACCOUNTS_JSON, accountsRoot.toString()).apply()

            AccountSession(
                userId = uid,
                login = cleanLogin,
                displayName = displayName,
                syncRoomCode = cleanRoom,
                serverUrl = cleanServer,
                isGoogleLinked = false
            )
        }

        _registeredUsersFlow.value = loadAllRegisteredUsers()
        persistActiveSession(accountSession)
        return Result.success(accountSession)
    }

    fun registerAccount(
        rawLogin: String,
        rawDisplayName: String,
        rawPassword: String,
        rawConfirmPassword: String,
        syncRoomCode: String = "main",
        serverUrl: String = ""
    ): Result<AccountSession> {
        val cleanLogin = rawLogin.trim().lowercase()
        if (cleanLogin.length < 2) {
            return Result.failure(IllegalArgumentException("Логин должен содержать минимум 2 символа"))
        }
        if (rawPassword.length < 4) {
            return Result.failure(IllegalArgumentException("Пароль должен содержать минимум 4 символа"))
        }
        if (rawConfirmPassword.isNotEmpty() && rawPassword != rawConfirmPassword) {
            return Result.failure(IllegalArgumentException("Пароли не совпадают"))
        }

        val cleanDisplayName = rawDisplayName.trim().ifBlank {
            rawLogin.trim().substringBefore("@")
        }
        val cleanRoom = syncRoomCode.trim().ifBlank { "main" }
        val cleanServer = serverUrl.trim()

        val accountsRoot = try {
            JSONObject(prefs.getString(KEY_ACCOUNTS_JSON, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

        val uid = if (accountsRoot.has(cleanLogin)) {
            accountsRoot.getJSONObject(cleanLogin).optString("userId")
        } else {
            "usr_${cleanLogin.replace(Regex("[^a-z0-9_]"), "_").take(24)}_${UUID.randomUUID().toString().take(6)}"
        }

        val accObj = JSONObject().apply {
            put("userId", uid)
            put("login", cleanLogin)
            put("displayName", cleanDisplayName)
            put("passwordHash", sha256(rawPassword))
            put("syncRoomCode", cleanRoom)
            put("serverUrl", cleanServer)
            put("bio", "В сети")
        }
        accountsRoot.put(cleanLogin, accObj)
        prefs.edit().putString(KEY_ACCOUNTS_JSON, accountsRoot.toString()).apply()

        val session = AccountSession(
            userId = uid,
            login = cleanLogin,
            displayName = cleanDisplayName,
            syncRoomCode = cleanRoom,
            serverUrl = cleanServer,
            isGoogleLinked = false
        )
        _registeredUsersFlow.value = loadAllRegisteredUsers()
        persistActiveSession(session)
        return Result.success(session)
    }

    fun onGoogleSessionAuthenticated(uid: String, email: String?, displayName: String?) {
        val current = _sessionFlow.value
        val login = email?.substringBefore("@") ?: current?.login ?: "user"
        val resolvedName = displayName ?: current?.displayName ?: login
        val session = AccountSession(
            userId = uid,
            login = login,
            displayName = resolvedName,
            syncRoomCode = current?.syncRoomCode ?: "main",
            serverUrl = current?.serverUrl ?: "",
            isGoogleLinked = true
        )
        persistActiveSession(session)
    }

    fun updateSyncConfig(syncRoomCode: String, serverUrl: String, displayName: String) {
        val current = _sessionFlow.value ?: return
        val updated = current.copy(
            displayName = displayName.trim().ifBlank { current.displayName },
            syncRoomCode = syncRoomCode.trim().ifBlank { "main" },
            serverUrl = serverUrl.trim()
        )
        persistActiveSession(updated)
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACTIVE_LOGIN)
            .remove(KEY_ACTIVE_UID)
            .remove(KEY_ACTIVE_DISPLAY_NAME)
            .remove(KEY_ACTIVE_IS_GOOGLE)
            .apply()
        _sessionFlow.value = null
    }

    private fun persistActiveSession(session: AccountSession) {
        prefs.edit()
            .putString(KEY_ACTIVE_LOGIN, session.login)
            .putString(KEY_ACTIVE_UID, session.userId)
            .putString(KEY_ACTIVE_DISPLAY_NAME, session.displayName)
            .putString(KEY_ACTIVE_SYNC_ROOM, session.syncRoomCode)
            .putString(KEY_ACTIVE_SERVER_URL, session.serverUrl)
            .putBoolean(KEY_ACTIVE_IS_GOOGLE, session.isGoogleLinked)
            .apply()
        _sessionFlow.value = session
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS_NAME = "messenger_clean_auth_v3"
        private const val KEY_ACCOUNTS_JSON = "accounts_v3_json"
        private const val KEY_ACTIVE_LOGIN = "active_login"
        private const val KEY_ACTIVE_UID = "active_uid"
        private const val KEY_ACTIVE_DISPLAY_NAME = "active_display_name"
        private const val KEY_ACTIVE_SYNC_ROOM = "active_sync_room"
        private const val KEY_ACTIVE_SERVER_URL = "active_server_url"
        private const val KEY_ACTIVE_IS_GOOGLE = "active_is_google"
    }
}
