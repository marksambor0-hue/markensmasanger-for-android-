package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

data class AccountSession(
    val userId: String,
    val login: String,
    val displayName: String,
    val syncRoomCode: String = "gitpulse-main",
    val serverUrl: String = "",
    val isGoogleLinked: Boolean = false
)

data class ExeSyncLogEntry(
    val id: String,
    val direction: String, // "OUT_TO_EXE" or "IN_FROM_EXE"
    val summary: String,
    val channelName: String,
    val timestampFormatted: String
)

@IgnoreExtraProperties
data class Channel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val topic: String = "",
    val category: String = "CHANNEL", // CHANNEL, DIRECT, GITHUB_BRIDGE, EXE_SYNC
    val linkedRepo: String = "",
    val unreadCount: Long = 0L,
    val isPinned: Boolean = false,
    val lastMessageText: String = "",
    val lastSenderName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelId: String = "",
    val senderName: String = "",
    val senderHandle: String = "",
    val text: String = "",
    val messageType: String = "TEXT", // TEXT, CODE_SNIPPET, GITHUB_COMMIT, EXE_PACKET
    val codeLanguage: String = "",
    val codeSnippet: String = "",
    val commitSha: String = "",
    val isPinned: Boolean = false,
    val reactionsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BridgeProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val statusText: String = "",
    val presenceState: String = "ONLINE", // ONLINE, CODING, AWAY, DND
    val githubRepo: String = "JetBrains/kotlin",
    val exeBridgeMode: String = "ACTIVE_SYNC", // ACTIVE_SYNC, MOBILE_STANDALONE, LOW_LATENCY
    val syncedPacketsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
