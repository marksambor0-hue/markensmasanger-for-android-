package com.example.data.repository

import android.content.Context
import com.example.data.model.AccountSession
import com.example.data.model.BridgeProfile
import com.example.data.model.Channel
import com.example.data.model.Message
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class UnifiedSyncEngine(
    context: Context,
    private val firestoreRepository: MessengerRepository
) {
    private val prefs = context.applicationContext.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val _channelsFlow = MutableStateFlow<List<Channel>>(emptyList())
    private val _messagesByChannelFlow = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val _profileFlow = MutableStateFlow<BridgeProfile?>(null)

    fun initializeForSession(session: AccountSession) {
        val loadedChannels = loadChannelsFromDisk(session.syncRoomCode)
        val loadedMessages = loadMessagesFromDisk(session.syncRoomCode)
        val loadedProfile = loadProfileFromDisk(session)

        // Start completely clean without any fake bots or fake channels
        _channelsFlow.value = sortChannels(loadedChannels)
        _messagesByChannelFlow.value = loadedMessages
        _profileFlow.value = loadedProfile
    }

    fun observeChannels(): Flow<List<Channel>> = _channelsFlow.asStateFlow()

    fun observeMessages(channelId: String): Flow<List<Message>> =
        _messagesByChannelFlow.map { map -> map[channelId] ?: emptyList() }

    fun observeProfile(): Flow<BridgeProfile?> = _profileFlow.asStateFlow()

    suspend fun createOrOpenDirectChat(
        session: AccountSession,
        targetUsername: String,
        targetDisplayName: String
    ): Channel {
        val cleanHandle = "@${targetUsername.trim().removePrefix("@").lowercase()}"
        val existing = _channelsFlow.value.find {
            it.category == "DIRECT" && (it.name.equals(cleanHandle, ignoreCase = true) || it.name.equals(targetDisplayName, ignoreCase = true))
        }
        if (existing != null) return existing

        return createChannel(
            session = session,
            name = targetDisplayName.ifBlank { cleanHandle },
            topic = cleanHandle,
            category = "DIRECT",
            linkedRepo = ""
        )
    }

    suspend fun createChannel(
        session: AccountSession,
        name: String,
        topic: String,
        category: String,
        linkedRepo: String
    ): Channel {
        val now = Timestamp.now()
        val id = "ch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val cleanName = name.trim()
        val newChannel = Channel(
            id = id,
            userId = session.userId,
            name = cleanName,
            topic = topic.trim().ifBlank {
                if (category == "DIRECT") "Личный диалог" else "Групповой чат"
            },
            category = category,
            linkedRepo = linkedRepo.trim(),
            unreadCount = 0L,
            isPinned = false,
            lastMessageText = "Нет сообщений",
            lastSenderName = session.displayName,
            createdAt = now,
            updatedAt = now
        )

        val updatedList = sortChannels(_channelsFlow.value + newChannel)
        _channelsFlow.value = updatedList
        saveChannelsToDisk(session.syncRoomCode, updatedList)

        if (Firebase.auth.currentUser != null) {
            firestoreRepository.createChannel(
                name = cleanName,
                topic = newChannel.topic,
                category = category,
                linkedRepo = newChannel.linkedRepo,
                customId = id
            )
        }
        return newChannel
    }

    suspend fun toggleChannelPin(session: AccountSession, channel: Channel) {
        val updated = _channelsFlow.value.map {
            if (it.id == channel.id) it.copy(isPinned = !it.isPinned, updatedAt = Timestamp.now()) else it
        }
        _channelsFlow.value = sortChannels(updated)
        saveChannelsToDisk(session.syncRoomCode, _channelsFlow.value)
        if (Firebase.auth.currentUser != null) {
            firestoreRepository.toggleChannelPin(channel)
        }
    }

    suspend fun deleteChannel(session: AccountSession, channelId: String) {
        val updated = _channelsFlow.value.filterNot { it.id == channelId }
        _channelsFlow.value = updated
        val updatedMsgs = _messagesByChannelFlow.value.toMutableMap().apply { remove(channelId) }
        _messagesByChannelFlow.value = updatedMsgs
        saveChannelsToDisk(session.syncRoomCode, updated)
        saveMessagesToDisk(session.syncRoomCode, updatedMsgs)
        if (Firebase.auth.currentUser != null) {
            firestoreRepository.deleteChannel(channelId)
        }
    }

    suspend fun sendMessage(
        session: AccountSession,
        channelId: String,
        text: String,
        senderName: String = session.displayName,
        senderHandle: String = "@${session.login}",
        messageType: String = "TEXT",
        codeLanguage: String = "",
        codeSnippet: String = "",
        commitSha: String = ""
    ): Message {
        val now = Timestamp.now()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val message = Message(
            id = msgId,
            userId = session.userId,
            channelId = channelId,
            senderName = senderName,
            senderHandle = senderHandle,
            text = text.trim(),
            messageType = messageType,
            codeLanguage = codeLanguage,
            codeSnippet = codeSnippet,
            commitSha = commitSha,
            isPinned = false,
            reactionsCount = 0L,
            createdAt = now,
            updatedAt = now
        )

        val currentMap = _messagesByChannelFlow.value.toMutableMap()
        val channelMessages = (currentMap[channelId] ?: emptyList()) + message
        currentMap[channelId] = channelMessages
        _messagesByChannelFlow.value = currentMap

        val targetChannel = _channelsFlow.value.find { it.id == channelId }
        val updatedChannels = _channelsFlow.value.map { ch ->
            if (ch.id == channelId) {
                ch.copy(
                    lastMessageText = text.trim().take(200),
                    lastSenderName = senderName,
                    updatedAt = now
                )
            } else ch
        }
        _channelsFlow.value = sortChannels(updatedChannels)

        saveChannelsToDisk(session.syncRoomCode, _channelsFlow.value)
        saveMessagesToDisk(session.syncRoomCode, currentMap)

        if (session.serverUrl.isNotBlank()) {
            pushMessageToCustomServer(session, targetChannel?.name ?: channelId, message)
        }

        if (Firebase.auth.currentUser != null) {
            firestoreRepository.sendMessage(
                channelId = channelId,
                text = text,
                senderName = senderName,
                senderHandle = senderHandle,
                messageType = messageType,
                codeLanguage = codeLanguage,
                codeSnippet = codeSnippet,
                commitSha = commitSha
            )
        }

        return message
    }

    suspend fun addReaction(session: AccountSession, message: Message) {
        val map = _messagesByChannelFlow.value.toMutableMap()
        val list = map[message.channelId] ?: return
        map[message.channelId] = list.map {
            if (it.id == message.id) it.copy(reactionsCount = it.reactionsCount + 1L) else it
        }
        _messagesByChannelFlow.value = map
        saveMessagesToDisk(session.syncRoomCode, map)
    }

    suspend fun toggleMessagePin(session: AccountSession, message: Message) {
        val map = _messagesByChannelFlow.value.toMutableMap()
        val list = map[message.channelId] ?: return
        map[message.channelId] = list.map {
            if (it.id == message.id) it.copy(isPinned = !it.isPinned) else it
        }
        _messagesByChannelFlow.value = map
        saveMessagesToDisk(session.syncRoomCode, map)
    }

    suspend fun deleteMessage(session: AccountSession, channelId: String, messageId: String) {
        val map = _messagesByChannelFlow.value.toMutableMap()
        val list = map[channelId] ?: return
        map[channelId] = list.filterNot { it.id == messageId }
        _messagesByChannelFlow.value = map
        saveMessagesToDisk(session.syncRoomCode, map)
    }

    suspend fun updateBridgeProfile(
        session: AccountSession,
        displayName: String,
        statusText: String,
        presenceState: String,
        githubRepo: String,
        exeBridgeMode: String
    ) {
        val existing = _profileFlow.value
        val updated = BridgeProfile(
            id = session.userId,
            userId = session.userId,
            displayName = displayName.trim().ifBlank { session.displayName },
            statusText = statusText.trim().ifBlank { "В сети" },
            presenceState = presenceState,
            githubRepo = githubRepo.trim(),
            exeBridgeMode = exeBridgeMode,
            syncedPacketsCount = existing?.syncedPacketsCount ?: 0L,
            createdAt = existing?.createdAt ?: Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        _profileFlow.value = updated
        saveProfileToDisk(session, updated)
    }

    private suspend fun pushMessageToCustomServer(session: AccountSession, channelName: String, message: Message) {
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = session.serverUrl.trim().removeSuffix("/")
                if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return@withContext
                val payload = JSONObject().apply {
                    put("room", session.syncRoomCode)
                    put("channel", channelName)
                    put("sender", message.senderName)
                    put("handle", message.senderHandle)
                    put("text", message.text)
                    put("type", message.messageType)
                }
                val req = Request.Builder()
                    .url("$baseUrl/messages")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {
            }
        }
    }

    private fun sortChannels(list: List<Channel>): List<Channel> {
        return list.sortedWith(
            compareByDescending<Channel> { it.isPinned }
                .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        )
    }

    private fun saveChannelsToDisk(roomCode: String, channels: List<Channel>) {
        val arr = JSONArray()
        for (ch in channels) {
            arr.put(
                JSONObject().apply {
                    put("id", ch.id)
                    put("userId", ch.userId)
                    put("name", ch.name)
                    put("topic", ch.topic)
                    put("category", ch.category)
                    put("linkedRepo", ch.linkedRepo)
                    put("unreadCount", ch.unreadCount)
                    put("isPinned", ch.isPinned)
                    put("lastMessageText", ch.lastMessageText)
                    put("lastSenderName", ch.lastSenderName)
                    put("updatedAtSec", ch.updatedAt?.seconds ?: Timestamp.now().seconds)
                }
            )
        }
        prefs.edit().putString("channels_$roomCode", arr.toString()).apply()
    }

    private fun loadChannelsFromDisk(roomCode: String): List<Channel> {
        val raw = prefs.getString("channels_$roomCode", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<Channel>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ts = Timestamp(o.optLong("updatedAtSec", Timestamp.now().seconds), 0)
                result.add(
                    Channel(
                        id = o.getString("id"),
                        userId = o.optString("userId", ""),
                        name = o.getString("name"),
                        topic = o.optString("topic", ""),
                        category = o.optString("category", "CHANNEL"),
                        linkedRepo = o.optString("linkedRepo", ""),
                        unreadCount = o.optLong("unreadCount", 0L),
                        isPinned = o.optBoolean("isPinned", false),
                        lastMessageText = o.optString("lastMessageText", ""),
                        lastSenderName = o.optString("lastSenderName", ""),
                        createdAt = ts,
                        updatedAt = ts
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveMessagesToDisk(roomCode: String, map: Map<String, List<Message>>) {
        val root = JSONObject()
        for ((channelId, list) in map) {
            val arr = JSONArray()
            for (m in list) {
                arr.put(
                    JSONObject().apply {
                        put("id", m.id)
                        put("userId", m.userId)
                        put("channelId", m.channelId)
                        put("senderName", m.senderName)
                        put("senderHandle", m.senderHandle)
                        put("text", m.text)
                        put("messageType", m.messageType)
                        put("codeLanguage", m.codeLanguage)
                        put("codeSnippet", m.codeSnippet)
                        put("commitSha", m.commitSha)
                        put("isPinned", m.isPinned)
                        put("reactionsCount", m.reactionsCount)
                        put("createdAtSec", m.createdAt?.seconds ?: Timestamp.now().seconds)
                    }
                )
            }
            root.put(channelId, arr)
        }
        prefs.edit().putString("messages_$roomCode", root.toString()).apply()
    }

    private fun loadMessagesFromDisk(roomCode: String): Map<String, List<Message>> {
        val raw = prefs.getString("messages_$roomCode", null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, List<Message>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val chId = keys.next()
                val arr = root.getJSONArray(chId)
                val list = mutableListOf<Message>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ts = Timestamp(o.optLong("createdAtSec", Timestamp.now().seconds), 0)
                    list.add(
                        Message(
                            id = o.getString("id"),
                            userId = o.optString("userId", ""),
                            channelId = o.optString("channelId", chId),
                            senderName = o.optString("senderName", ""),
                            senderHandle = o.optString("senderHandle", ""),
                            text = o.optString("text", ""),
                            messageType = o.optString("messageType", "TEXT"),
                            codeLanguage = o.optString("codeLanguage", ""),
                            codeSnippet = o.optString("codeSnippet", ""),
                            commitSha = o.optString("commitSha", ""),
                            isPinned = o.optBoolean("isPinned", false),
                            reactionsCount = o.optLong("reactionsCount", 0L),
                            createdAt = ts,
                            updatedAt = ts
                        )
                    )
                }
                result[chId] = list
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveProfileToDisk(session: AccountSession, profile: BridgeProfile) {
        val o = JSONObject().apply {
            put("displayName", profile.displayName)
            put("statusText", profile.statusText)
            put("presenceState", profile.presenceState)
            put("githubRepo", profile.githubRepo)
            put("exeBridgeMode", profile.exeBridgeMode)
            put("syncedPacketsCount", profile.syncedPacketsCount)
        }
        prefs.edit().putString("profile_${session.login}", o.toString()).apply()
    }

    private fun loadProfileFromDisk(session: AccountSession): BridgeProfile {
        val raw = prefs.getString("profile_${session.login}", null)
        if (raw != null) {
            try {
                val o = JSONObject(raw)
                return BridgeProfile(
                    id = session.userId,
                    userId = session.userId,
                    displayName = o.optString("displayName", session.displayName),
                    statusText = o.optString("statusText", "В сети"),
                    presenceState = o.optString("presenceState", "ONLINE"),
                    githubRepo = o.optString("githubRepo", ""),
                    exeBridgeMode = o.optString("exeBridgeMode", "ACTIVE_SYNC"),
                    syncedPacketsCount = o.optLong("syncedPacketsCount", 0L),
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            } catch (_: Exception) {}
        }
        return BridgeProfile(
            id = session.userId,
            userId = session.userId,
            displayName = session.displayName,
            statusText = "В сети",
            presenceState = "ONLINE",
            githubRepo = "",
            exeBridgeMode = "ACTIVE_SYNC",
            syncedPacketsCount = 0L,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    }

    companion object {
        private const val SYNC_PREFS = "messenger_clean_store_v3"
    }
}
