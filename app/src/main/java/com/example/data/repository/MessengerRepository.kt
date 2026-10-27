package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BridgeProfile
import com.example.data.model.Channel
import com.example.data.model.Message
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MessengerRepository(private val db: FirebaseFirestore) {

    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeChannels(userId: String): Flow<List<Channel>> {
        val path = "channels"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<Channel> { it.isPinned }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeMessages(userId: String, channelId: String): Flow<List<Message>> {
        val path = "messages"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .whereEqualTo("channelId", channelId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeBridgeProfile(userId: String): Flow<BridgeProfile?> {
        val path = "bridge_profiles/$userId"
        return db.collection("bridge_profiles").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(BridgeProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun getChannelById(channelId: String): Result<Channel> = runCatching {
        val docRef = db.collection("channels").document(channelId)
        try {
            val snapshot = docRef.get().await()
            snapshot.toObject(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw IllegalStateException("Channel not found")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun createChannel(
        name: String,
        topic: String,
        category: String = "CHANNEL",
        linkedRepo: String = "JetBrains/kotlin",
        isPinned: Boolean = false,
        customId: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val channelId = customId ?: "ch_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("channels").document(channelId)
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(60).ifEmpty { "#general" },
            "topic" to topic.trim().take(200).ifEmpty { "Канал портированного мессенджера" },
            "category" to category,
            "linkedRepo" to linkedRepo.trim().take(120),
            "unreadCount" to 0L,
            "isPinned" to isPinned,
            "lastMessageText" to "Канал создан в мобильной версии",
            "lastSenderName" to (auth.currentUser?.displayName?.take(60) ?: "GitPulse Core"),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            channelId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleChannelPin(channel: Channel): Result<Unit> = runCatching {
        require(channel.id.isNotBlank()) { "Channel ID must not be blank" }
        val docRef = db.collection("channels").document(channel.id)
        val updates = mapOf(
            "isPinned" to !channel.isPinned,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteChannel(channelId: String): Result<Unit> = runCatching {
        val docRef = db.collection("channels").document(channelId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun sendMessage(
        channelId: String,
        text: String,
        senderName: String? = null,
        senderHandle: String? = null,
        messageType: String = "TEXT",
        codeLanguage: String = "",
        codeSnippet: String = "",
        commitSha: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("messages").document(msgId)
        val resolvedSender = (senderName ?: auth.currentUser?.displayName ?: "Разработчик").trim().take(80).ifEmpty { "Разработчик" }
        val resolvedHandle = (senderHandle ?: "@${resolvedSender.lowercase().replace(" ", "_").take(20)}").trim().take(60).ifEmpty { "@dev" }
        val cleanText = text.trim().take(4000).ifEmpty { "Пакет данных" }

        val payload = mapOf(
            "userId" to uid,
            "channelId" to channelId,
            "senderName" to resolvedSender,
            "senderHandle" to resolvedHandle,
            "text" to cleanText,
            "messageType" to messageType,
            "codeLanguage" to codeLanguage.trim().take(40),
            "codeSnippet" to codeSnippet.trim().take(4000),
            "commitSha" to commitSha.trim().take(60),
            "isPinned" to false,
            "reactionsCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            // Also update parent channel preview
            val channelRef = db.collection("channels").document(channelId)
            channelRef.update(
                mapOf(
                    "lastMessageText" to cleanText.take(280),
                    "lastSenderName" to resolvedSender,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            msgId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun addReactionToMessage(message: Message): Result<Unit> = runCatching {
        require(message.id.isNotBlank()) { "Message ID must not be blank" }
        val docRef = db.collection("messages").document(message.id)
        val newCount = (message.reactionsCount + 1L).coerceAtMost(10000L)
        try {
            docRef.update(
                mapOf(
                    "reactionsCount" to newCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleMessagePin(message: Message): Result<Unit> = runCatching {
        require(message.id.isNotBlank()) { "Message ID must not be blank" }
        val docRef = db.collection("messages").document(message.id)
        try {
            docRef.update(
                mapOf(
                    "isPinned" to !message.isPinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        val docRef = db.collection("messages").document(messageId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun saveBridgeProfile(
        displayName: String,
        statusText: String,
        presenceState: String,
        githubRepo: String,
        exeBridgeMode: String,
        syncedPacketsCount: Long
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("bridge_profiles").document(uid)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.trim().take(80).ifEmpty { "GitPulse Dev" },
                    "statusText" to statusText.trim().take(120).ifEmpty { "Android APK Port • Online" },
                    "presenceState" to presenceState,
                    "githubRepo" to githubRepo.trim().take(120).ifEmpty { "JetBrains/kotlin" },
                    "exeBridgeMode" to exeBridgeMode,
                    "syncedPacketsCount" to syncedPacketsCount.coerceIn(0L, 1000000L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "displayName" to displayName.trim().take(80).ifEmpty { "GitPulse Dev" },
                    "statusText" to statusText.trim().take(120).ifEmpty { "Android APK Port • Online" },
                    "presenceState" to presenceState,
                    "githubRepo" to githubRepo.trim().take(120).ifEmpty { "JetBrains/kotlin" },
                    "exeBridgeMode" to exeBridgeMode,
                    "syncedPacketsCount" to syncedPacketsCount.coerceIn(0L, 1000000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun seedPortedWorkspaceIfNeeded(): Result<String> = runCatching {
        val uid = requireUserId()
        val existing = db.collection("channels")
            .whereEqualTo("userId", uid)
            .limit(1)
            .get()
            .await()

        if (!existing.isEmpty) {
            return@runCatching existing.documents.first().id
        }

        val userDisplay = auth.currentUser?.displayName?.take(50) ?: "Разработчик"
        saveBridgeProfile(
            displayName = userDisplay,
            statusText = "Android APK v2.4 (Порт gitpulse-client.exe)",
            presenceState = "ONLINE",
            githubRepo = "JetBrains/kotlin",
            exeBridgeMode = "ACTIVE_SYNC",
            syncedPacketsCount = 14L
        )

        val chGeneralId = createChannel(
            name = "#general-exe-port",
            topic = "Главный канал порта десктопного мессенджера с GitHub на Android",
            category = "EXE_SYNC",
            linkedRepo = "JetBrains/kotlin",
            isPinned = true
        ).getOrThrow()

        val chGithubId = createChannel(
            name = "#github-commits",
            topic = "Живая лента коммитов, PR и обсуждений из GitHub репозитория",
            category = "GITHUB_BRIDGE",
            linkedRepo = "JetBrains/kotlin",
            isPinned = true
        ).getOrThrow()

        val chCodeId = createChannel(
            name = "#code-snippets",
            topic = "Обмен сниппетами кода, конфигами сборки и фиксами багов",
            category = "CHANNEL",
            linkedRepo = "JetBrains/kotlin",
            isPinned = false
        ).getOrThrow()

        val chDmId = createChannel(
            name = "@alex_core (Desktop .EXE)",
            topic = "Личный чат с тимлидом десктоп-клиента (Windows x64 .exe)",
            category = "DIRECT",
            linkedRepo = "JetBrains/kotlin",
            isPinned = false
        ).getOrThrow()

        // Seed realistic ported messages into #general-exe-port
        sendMessage(
            channelId = chGeneralId,
            senderName = "Desktop .EXE Bridge",
            senderHandle = "@exe_daemon",
            text = "Бинарный мост [gitpulse-desktop.exe -> Android APK] успешно инициализирован. Схема сокетов переведена на облачный транспорт Firestore.",
            messageType = "EXE_PACKET",
            codeLanguage = "bash",
            codeSnippet = "[IPC_BRIDGE] Handshake OK | Win32Pipe -> AndroidNativeFlow | Latency: 12ms | Encryption: TLS_1.3"
        )

        sendMessage(
            channelId = chGeneralId,
            senderName = "Алексей (Core Dev)",
            senderHandle = "@alex_core",
            text = "Йо! Наконец-то наш мессенджер с гитхаба работает прямо на мобилке без запуска экзешника на ПК. Тут сохранены все каналы, сниппеты кода и импорт коммитов!",
            messageType = "TEXT"
        )

        sendMessage(
            channelId = chGeneralId,
            senderName = "Катя (Build Engineer)",
            senderHandle = "@katya_ci",
            text = "Собрала нативный мост пакетного протокола для мобильной версии:",
            messageType = "CODE_SNIPPET",
            codeLanguage = "kotlin",
            codeSnippet = "class ExePacketTransceiver(private val channelId: String) {\n    fun decodeFrame(raw: ByteArray): ExeFrame = ExeFrame.parse(raw)\n}"
        )

        // Seed into #github-commits
        sendMessage(
            channelId = chGithubId,
            senderName = "GitHub Webhook",
            senderHandle = "@github_bot",
            text = "feat(android-port): migrate Win32 window loop to Jetpack Compose Material 3",
            messageType = "GITHUB_COMMIT",
            commitSha = "8f4d2a1",
            codeLanguage = "kotlin",
            codeSnippet = "git commit -m 'feat(android-port): full mobile UI & GitHub API sync'"
        )

        // Seed into #code-snippets
        sendMessage(
            channelId = chCodeId,
            senderName = "Rust Daemon",
            senderHandle = "@rust_ipc",
            text = "Структура заголовка пакета из старого десктопного .exe клиента:",
            messageType = "CODE_SNIPPET",
            codeLanguage = "rust",
            codeSnippet = "#[repr(C)]\npub struct ExeMsgHeader {\n    pub magic: u32,\n    pub opcode: u16,\n    pub payload_len: u32,\n}"
        )

        // Seed into DM
        sendMessage(
            channelId = chDmId,
            senderName = "Алексей (Core Dev)",
            senderHandle = "@alex_core",
            text = "Если нужно подтянуть свежие коммиты с любого репо на GitHub — жми кнопку синхронизации сверху или во вкладке GitHub Хаб!",
            messageType = "TEXT"
        )

        chGeneralId
    }
}
