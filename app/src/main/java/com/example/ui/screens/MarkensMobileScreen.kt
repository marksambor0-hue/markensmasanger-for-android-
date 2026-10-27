package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.markens.Base64ImageDecoder
import com.example.data.markens.MarkensCallManager
import com.example.data.markens.MarkensChannelItem
import com.example.data.markens.MarkensCloudClient
import com.example.data.markens.MarkensFeedbackItem
import com.example.data.markens.MarkensFriendRequest
import com.example.data.markens.MarkensMessage
import com.example.data.markens.MarkensOtaRelease
import com.example.data.markens.MarkensPresence
import com.example.data.markens.MarkensSavedAccount
import com.example.data.markens.MarkensServerGroup
import com.example.data.markens.MarkensServerRole
import com.example.data.markens.MarkensSoundEngine
import com.example.data.markens.MarkensSyncDiagnostics
import com.example.data.markens.MarkensUser
import com.example.data.markens.MarkensVoiceRoomMember
import com.example.data.markens.MessageDeliveryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MarkensBottomTab {
    CHATS,
    GROUPS,
    IDEAS,
    PROFILE
}

private enum class PersonalChatFilter(val title: String) {
    ALL("Все личные"),
    ONLINE("В сети")
}

private val VORTEX_BADGES = listOf(
    "👑" to "Капитан",
    "🎯" to "Снайпер",
    "🔥" to "Душа компании",
    "🍿" to "Киноман",
    "💎" to "Олд сервера",
    "⚡" to "Спидраннер"
)

@Immutable
data class MarkensPalette(
    val isLight: Boolean,
    val bgPrimary: Color,
    val bgSecondary: Color,
    val bgTertiary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderColor: Color,
    val dividerColor: Color,
    val brandColor: Color = Color(0xFF4F46E5),
    val greenColor: Color = Color(0xFF10B981)
)

@Composable
private fun rememberMarkensPalette(activeTheme: String): MarkensPalette {
    val systemDark = isSystemInDarkTheme()
    val effectiveMode = when (activeTheme) {
        "light" -> "light"
        "amoled" -> "amoled"
        "system" -> if (systemDark) "dark" else "light"
        else -> "dark" // "dark" or "default"
    }
    return remember(effectiveMode) {
        when (effectiveMode) {
            "light" -> MarkensPalette(
                isLight = true,
                bgPrimary = Color(0xFFF8FAFC),
                bgSecondary = Color(0xFFFFFFFF),
                bgTertiary = Color(0xFFEEF2F6),
                textPrimary = Color(0xFF0F172A),
                textSecondary = Color(0xFF334155),
                textMuted = Color(0xFF64748B),
                borderColor = Color(0xFFCBD5E1),
                dividerColor = Color(0xFFE2E8F0)
            )
            "amoled" -> MarkensPalette(
                isLight = false,
                bgPrimary = Color(0xFF090A0C),
                bgSecondary = Color(0xFF040406),
                bgTertiary = Color(0xFF000000),
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFFDCDFE4),
                textMuted = Color(0xFF8E95A2),
                borderColor = Color(0xFF22252D),
                dividerColor = Color(0xFF14161B)
            )
            else -> MarkensPalette(
                isLight = false,
                bgPrimary = Color(0xFF242830),
                bgSecondary = Color(0xFF1D2027),
                bgTertiary = Color(0xFF14161B),
                textPrimary = Color(0xFFF3F4F6),
                textSecondary = Color(0xFFDCDFE4),
                textMuted = Color(0xFF8E95A2),
                borderColor = Color(0xFF2B303B),
                dividerColor = Color(0xFF14161B)
            )
        }
    }
}

@Composable
fun MarkensMobileScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cloudClient = remember { MarkensCloudClient(context) }
    val callManager = remember(cloudClient) { MarkensCallManager(context, cloudClient) }
    val scope = rememberCoroutineScope()

    DisposableEffect(callManager) {
        onDispose {
            callManager.destroy()
        }
    }

    var currentUser by remember { mutableStateOf(cloudClient.loadSavedUser()) }
    var savedAccounts by remember { mutableStateOf(cloudClient.getSavedAccounts()) }
    var rememberedCreds by remember { mutableStateOf(cloudClient.getLastRememberedCredentials()) }

    var activeTheme by remember { mutableStateOf(cloudClient.getSavedTheme()) }
    var startupSoundEnabled by remember { mutableStateOf(cloudClient.isStartupSoundEnabled()) }
    val palette = rememberMarkensPalette(activeTheme)

    LaunchedEffect(Unit) {
        MarkensSoundEngine.playStartupChimeOnceIfNeeded(startupSoundEnabled)
    }

    val user = currentUser
    if (user == null) {
        MarkensNativeAuthScreen(
            initialLogin = rememberedCreds.first,
            initialPassword = rememberedCreds.second,
            savedAccounts = savedAccounts,
            palette = palette,
            onRemoveSavedAccount = { login ->
                cloudClient.removeSavedAccount(login)
                savedAccounts = cloudClient.getSavedAccounts()
            },
            onLoginWithSavedAccount = { acc ->
                val res = cloudClient.loginWithSavedAccount(acc)
                res.onSuccess { loggedIn ->
                    savedAccounts = cloudClient.getSavedAccounts()
                    rememberedCreds = cloudClient.getLastRememberedCredentials()
                    currentUser = loggedIn
                    if (startupSoundEnabled) {
                        MarkensSoundEngine.playWelcomeChime()
                    }
                }.onFailure {
                    savedAccounts = cloudClient.getSavedAccounts()
                }
                res
            },
            onAuthenticate = { login, pass, isRegister ->
                val res = cloudClient.loginOrRegister(login, pass, isRegister)
                res.onSuccess { loggedIn ->
                    savedAccounts = cloudClient.getSavedAccounts()
                    rememberedCreds = cloudClient.getLastRememberedCredentials()
                    currentUser = loggedIn
                    if (startupSoundEnabled) {
                        MarkensSoundEngine.playWelcomeChime()
                    }
                }
                res
            }
        )
        return
    }

    var selectedTab by remember { mutableStateOf(MarkensBottomTab.CHATS) }
    var activeChat by remember { mutableStateOf<ActiveChatSession?>(null) }
    var chatMessages by remember { mutableStateOf<List<MarkensMessage>>(emptyList()) }

    var serverGroups by remember { mutableStateOf(listOf(cloudClient.defaultVortexGroup())) }
    var selectedGroupId by remember { mutableStateOf("vortex") }

    var vortexJoined by remember(user.name, user.isOwner) {
        mutableStateOf(cloudClient.isVortexJoined(user.name, user.isOwner))
    }
    var vortexInviteDismissed by remember(user.name) {
        mutableStateOf(cloudClient.isVortexInviteDismissed(user.name))
    }

    var friends by remember(user.name) { mutableStateOf(cloudClient.getLocalFriends(user.name)) }
    var friendRequests by remember { mutableStateOf<List<MarkensFriendRequest>>(emptyList()) }
    var presenceMap by remember { mutableStateOf<Map<String, MarkensPresence>>(emptyMap()) }
    var previewRefreshTick by remember { mutableIntStateOf(0) }
    var feedbackItems by remember { mutableStateOf<List<MarkensFeedbackItem>>(emptyList()) }
    var toastBanner by remember { mutableStateOf<String?>(null) }
    var activeOtaPrompt by remember { mutableStateOf<MarkensOtaRelease?>(null) }
    var installedOtaVer by remember { mutableStateOf(cloudClient.getInstalledOtaVersion()) }

    var voiceRoomMembers by remember { mutableStateOf<List<MarkensVoiceRoomMember>>(emptyList()) }
    var activeVoiceRoomId by remember { mutableStateOf<String?>(null) }
    var activeVoiceRoomName by remember { mutableStateOf("Общая комната") }
    var voiceMicEnabled by remember { mutableStateOf(true) }
    var voiceDeafened by remember { mutableStateOf(false) }
    var serverRoles by remember { mutableStateOf(cloudClient.defaultVortexRoles()) }
    var lastPingMs by remember { mutableStateOf(24L) }
    var isRtdbConnected by remember { mutableStateOf(true) }
    var lastSyncedTimeStr by remember { mutableStateOf("Только что") }

    val activeCallSession by callManager.activeCall.collectAsState()
    val activeCallDurationSec by callManager.callDurationSec.collectAsState()
    var pendingCallTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val pending = pendingCallTarget
        pendingCallTarget = null
        if (pending != null) {
            val (peerNick, isVideo) = pending
            val micOk = perms[Manifest.permission.RECORD_AUDIO] == true || callManager.hasAudioPermission()
            val camOk = !isVideo || perms[Manifest.permission.CAMERA] == true || callManager.hasCameraPermission()
            if (!micOk) {
                toastBanner = "⚠️ Для звонка необходимо разрешить доступ к микрофону"
            } else if (!camOk) {
                toastBanner = "⚠️ Для видеозвонка необходимо разрешить доступ к камере"
            } else {
                val res = callManager.startOutgoingCall(user, peerNick, isVideo)
                res.onFailure { toastBanner = "⚠️ ${it.message}" }
            }
        }
    }

    val startCallWithPermissions: (String, Boolean) -> Unit = { peerNick, isVideo ->
        val micOk = callManager.hasAudioPermission()
        val camOk = !isVideo || callManager.hasCameraPermission()
        if (micOk && camOk) {
            val res = callManager.startOutgoingCall(user, peerNick, isVideo)
            res.onFailure { toastBanner = "⚠️ ${it.message}" }
        } else {
            pendingCallTarget = peerNick to isVideo
            val permsToRequest = if (isVideo) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            callPermissionLauncher.launch(permsToRequest)
        }
    }

    // Verify active session against remote Firebase `/accounts/{login}` on startup
    LaunchedEffect(user.name, user.securityToken) {
        val verifyRes = cloudClient.verifySessionWithServer(user)
        verifyRes.onSuccess { verifiedUser ->
            if (verifiedUser != currentUser) {
                currentUser = verifiedUser
            }
        }.onFailure { err ->
            if (err is SecurityException) {
                currentUser = null
            }
        }
    }

    // One-time channel preview preload per user login so list previews appear quickly without continuous CPU work
    LaunchedEffect(user.name) {
        val updated = cloudClient.preloadKeyChannels(user.name, friends, serverGroups)
        if (updated) {
            previewRefreshTick++
        }
    }

    // Lifecycle-aware OTA Auto-Update listener: pauses automatically when app is in background
    LaunchedEffect(user.name, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val remoteOta = cloudClient.checkCloudOta()
                if (remoteOta != null && cloudClient.isNewerOtaAvailable(remoteOta)) {
                    if (remoteOta.autoForce && remoteOta.apkUrl == null) {
                        cloudClient.applyOtaRelease(remoteOta)
                        installedOtaVer = remoteOta.version
                        val refreshedGroups = cloudClient.fetchServerGroups()
                        if (refreshedGroups.isNotEmpty()) serverGroups = refreshedGroups
                        cloudClient.preloadKeyChannels(user.name, friends, serverGroups)
                        previewRefreshTick++
                        MarkensSoundEngine.playWelcomeChime()
                        activeOtaPrompt = remoteOta
                    } else {
                        activeOtaPrompt = remoteOta
                    }
                }
                delay(10_000L)
            }
        }
    }

    // Lifecycle-aware smart diffing background loop + Incoming Call listener + Voice Rooms & Roles sync
    LaunchedEffect(user.name, user.status, user.avatarUrl, activeVoiceRoomId, voiceMicEnabled, voiceDeafened, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                coroutineScope {
                    val friendsDeferred = async { cloudClient.syncFriendsAndRequests(user.name) }
                    val groupsDeferred = async { cloudClient.fetchServerGroups() }
                    val hbDeferred = async { cloudClient.heartbeatAndFetchPresence(user = user) }
                    val callDeferred = async { cloudClient.pollIncomingCallForUser(user.name) }
                    val voiceDeferred = async {
                        val currentRoom = activeVoiceRoomId
                        if (currentRoom != null) {
                            cloudClient.joinOrUpdateVoiceRoom(
                                user = user,
                                roomId = currentRoom,
                                roomName = activeVoiceRoomName,
                                micEnabled = voiceMicEnabled,
                                deafened = voiceDeafened
                            )
                        } else {
                            cloudClient.fetchVoiceRooms()
                        }
                    }
                    val rolesDeferred = async { cloudClient.fetchServerRoles() }

                    val (fList, reqList) = friendsDeferred.await()
                    if (fList != friends) friends = fList
                    if (reqList != friendRequests) friendRequests = reqList

                    val fetchedGroups = groupsDeferred.await()
                    if (fetchedGroups.isNotEmpty() && fetchedGroups != serverGroups) {
                        serverGroups = fetchedGroups
                    }

                    val hb = hbDeferred.await()
                    if (hb.presenceMap != presenceMap) {
                        presenceMap = hb.presenceMap
                    }
                    lastPingMs = hb.pingMs
                    isRtdbConnected = hb.isConnected
                    lastSyncedTimeStr = hb.syncedTimeFormatted

                    val fetchedVoice = voiceDeferred.await()
                    if (fetchedVoice != voiceRoomMembers) {
                        voiceRoomMembers = fetchedVoice
                    }

                    val fetchedRoles = rolesDeferred.await()
                    if (fetchedRoles.isNotEmpty() && fetchedRoles != serverRoles) {
                        serverRoles = fetchedRoles
                    }

                    val incomingCall = callDeferred.await()
                    if (incomingCall != null) {
                        callManager.handleIncomingCallFromCloud(incomingCall, user.name)
                    }
                }
                delay(3600L)
            }
        }
    }

    LaunchedEffect(activeChat?.channelKey, lifecycleOwner) {
        val chat = activeChat ?: return@LaunchedEffect
        val cached = cloudClient.getCachedChannelMessages(chat.channelKey)
        chatMessages = cached
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val remoteInitial = cloudClient.fetchChannelMessages(chat.channelKey)
            val initialMarked = cloudClient.markChannelMessagesAsRead(chat.channelKey, user.name, remoteInitial)
            if (initialMarked != chatMessages && (initialMarked.isNotEmpty() || cached.isEmpty())) {
                chatMessages = initialMarked
                previewRefreshTick++
            }
            while (isActive) {
                delay(2000L)
                val remote = cloudClient.fetchChannelMessages(chat.channelKey)
                val marked = cloudClient.markChannelMessagesAsRead(chat.channelKey, user.name, remote)
                if (marked != chatMessages && (marked.isNotEmpty() || chatMessages.isEmpty())) {
                    chatMessages = marked
                    previewRefreshTick++
                }
            }
        }
    }

    LaunchedEffect(selectedTab, lifecycleOwner) {
        if (selectedTab == MarkensBottomTab.IDEAS) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val initial = cloudClient.fetchFeedbackBoard()
                if (initial != feedbackItems) feedbackItems = initial
                while (isActive) {
                    delay(4000L)
                    val updated = cloudClient.fetchFeedbackBoard()
                    if (updated != feedbackItems) feedbackItems = updated
                }
            }
        }
    }

    @Composable
    fun RenderChatRoomPane(session: ActiveChatSession, showBackButton: Boolean) {
        MarkensChatRoomView(
            session = session,
            currentUser = user,
            messages = chatMessages,
            presenceMap = presenceMap,
            friends = friends,
            bgPrimary = palette.bgPrimary,
            bgSecondary = palette.bgSecondary,
            bgTertiary = palette.bgTertiary,
            brandColor = palette.brandColor,
            greenColor = palette.greenColor,
            showBackButton = showBackButton,
            onBack = { activeChat = null },
            onStartVoiceCall = {
                startCallWithPermissions(session.title, false)
            },
            onStartVideoCall = {
                startCallWithPermissions(session.title, true)
            },
            onSendMessage = { text, replyTo, mediaDataUrl ->
                val optimistic = cloudClient.createOptimisticMessage(
                    channelKey = session.channelKey,
                    user = user,
                    text = text,
                    isDm = session.isDm,
                    replyTo = replyTo,
                    mediaDataUrl = mediaDataUrl
                )
                if (optimistic != null) {
                    chatMessages = chatMessages + optimistic
                    previewRefreshTick++
                    scope.launch {
                        MarkensSoundEngine.playMessageSendPop(startupSoundEnabled)
                        val delivered = cloudClient.pushMessageToCloud(
                            channelKey = session.channelKey,
                            user = user,
                            isDm = session.isDm,
                            msg = optimistic
                        )
                        chatMessages = chatMessages.map {
                            if (it.id == delivered.id) delivered else it
                        }
                        previewRefreshTick++
                        if (delivered.deliveryStatus == MessageDeliveryStatus.SENT && !session.isDm && !user.isOwner) {
                            val newXp = user.xp + 15
                            val updatedUser = if (newXp >= 100) {
                                user.copy(level = user.level + 1, xp = newXp - 100)
                            } else {
                                user.copy(xp = newXp)
                            }
                            currentUser = updatedUser
                            cloudClient.updateProfileInCloud(updatedUser)
                        }
                    }
                }
            },
            onRetryMessage = { failedMsg ->
                chatMessages = chatMessages.map {
                    if (it.id == failedMsg.id) it.copy(deliveryStatus = MessageDeliveryStatus.SENDING) else it
                }
                scope.launch {
                    val retried = cloudClient.retryFailedMessage(
                        channelKey = session.channelKey,
                        user = user,
                        isDm = session.isDm,
                        messageId = failedMsg.id
                    )
                    if (retried != null) {
                        chatMessages = chatMessages.map {
                            if (it.id == retried.id) retried else it
                        }
                        previewRefreshTick++
                    }
                }
            },
            onEditMessage = { msg, newText ->
                chatMessages = chatMessages.map {
                    if (it.id == msg.id) it.copy(text = newText, edited = true) else it
                }
                previewRefreshTick++
                scope.launch {
                    cloudClient.editMessage(session.channelKey, msg, newText)
                }
            },
            onToggleReaction = { msg, emoji ->
                val updatedMsg = cloudClient.toggleReactionOptimistic(
                    channelKey = session.channelKey,
                    message = msg,
                    emoji = emoji,
                    userName = user.name
                )
                chatMessages = chatMessages.map {
                    if (it.id == msg.id) updatedMsg else it
                }
                scope.launch {
                    cloudClient.toggleReaction(session.channelKey, msg, emoji, user.name)
                }
            },
            onTogglePin = { msg ->
                chatMessages = chatMessages.map {
                    if (it.id == msg.id) it.copy(pinned = !it.pinned) else it
                }
                scope.launch {
                    cloudClient.togglePinMessage(session.channelKey, msg)
                }
            },
            onDeleteMessage = { msg ->
                chatMessages = chatMessages.filterNot { it.id == msg.id }
                previewRefreshTick++
                scope.launch {
                    cloudClient.deleteMessage(session.channelKey, msg.id)
                }
            }
        )
    }

    @Composable
    fun RenderPrimaryTabContent() {
        when (selectedTab) {
            MarkensBottomTab.CHATS -> {
                ChatsAndFriendsTab(
                    currentUser = user,
                    cloudClient = cloudClient,
                    friends = friends,
                    friendRequests = friendRequests,
                    presenceMap = presenceMap,
                    previewRefreshTick = previewRefreshTick,
                    vortexJoined = vortexJoined,
                    vortexInviteDismissed = vortexInviteDismissed,
                    palette = palette,
                    onOpenDm = { friendNick ->
                        val pair = listOf(user.name.lowercase(), friendNick.lowercase()).sorted().joinToString("_")
                        activeChat = ActiveChatSession(
                            channelKey = "dm_$pair",
                            title = friendNick,
                            subtitle = "Личная переписка",
                            isDm = true
                        )
                    },
                    onOpenFavorites = {
                        activeChat = ActiveChatSession(
                            channelKey = "fav_${user.name.lowercase()}",
                            title = "Избранное",
                            subtitle = "Личные заметки и сохранённые сообщения",
                            isDm = true
                        )
                    },
                    onAcceptVortexFromBanner = {
                        vortexJoined = true
                        vortexInviteDismissed = true
                        cloudClient.setVortexJoined(user.name, true)
                        cloudClient.setVortexInviteDismissed(user.name, true)
                        scope.launch { cloudClient.syncVortexMembershipToCloud(user.name, true) }
                        selectedGroupId = "vortex"
                        selectedTab = MarkensBottomTab.GROUPS
                        toastBanner = "🎉 Вы вступили в сообщество VORTEX!"
                    },
                    onDismissVortexBanner = {
                        vortexInviteDismissed = true
                        cloudClient.setVortexInviteDismissed(user.name, true)
                    },
                    onAddFriend = { nick ->
                        val res = cloudClient.sendFriendRequest(user.name, nick)
                        val (fList, rList) = cloudClient.syncFriendsAndRequests(user.name)
                        friends = fList
                        friendRequests = rList
                        res
                    },
                    onAcceptRequest = { fromNick ->
                        scope.launch {
                            cloudClient.acceptFriendRequest(user.name, fromNick)
                            val (fList, rList) = cloudClient.syncFriendsAndRequests(user.name)
                            friends = fList
                            friendRequests = rList
                        }
                    },
                    onDeclineRequest = { fromNick ->
                        scope.launch {
                            cloudClient.declineFriendRequest(user.name, fromNick)
                            val (fList, rList) = cloudClient.syncFriendsAndRequests(user.name)
                            friends = fList
                            friendRequests = rList
                        }
                    }
                )
            }

            MarkensBottomTab.GROUPS -> {
                val currentGroup = serverGroups.find { it.id == selectedGroupId } ?: serverGroups.first()
                DiscordGroupsTab(
                    currentUser = user,
                    cloudClient = cloudClient,
                    groups = serverGroups,
                    selectedGroup = currentGroup,
                    vortexJoined = vortexJoined,
                    friends = friends,
                    presenceMap = presenceMap,
                    voiceRoomMembers = voiceRoomMembers,
                    activeVoiceRoomId = activeVoiceRoomId,
                    voiceMicEnabled = voiceMicEnabled,
                    voiceDeafened = voiceDeafened,
                    serverRoles = serverRoles,
                    previewRefreshTick = previewRefreshTick,
                    palette = palette,
                    onSelectGroup = { grp -> selectedGroupId = grp.id },
                    onJoinVortex = {
                        vortexJoined = true
                        vortexInviteDismissed = true
                        cloudClient.setVortexJoined(user.name, true)
                        cloudClient.setVortexInviteDismissed(user.name, true)
                        scope.launch { cloudClient.syncVortexMembershipToCloud(user.name, true) }
                        toastBanner = "🎉 Добро пожаловать в VORTEX!"
                    },
                    onDeclineVortex = {
                        vortexJoined = false
                        vortexInviteDismissed = true
                        cloudClient.setVortexJoined(user.name, false)
                        cloudClient.setVortexInviteDismissed(user.name, true)
                        scope.launch { cloudClient.syncVortexMembershipToCloud(user.name, false) }
                    },
                    onCreateGroup = { name, desc ->
                        scope.launch {
                            val res = cloudClient.createServerGroup(user, name, desc)
                            res.onSuccess { created ->
                                serverGroups = cloudClient.fetchServerGroups()
                                selectedGroupId = created.id
                            }
                        }
                    },
                    onAddTextChannel = { title ->
                        scope.launch {
                            cloudClient.addTextChannelToGroup(currentGroup, title)
                            serverGroups = cloudClient.fetchServerGroups()
                        }
                    },
                    onJoinVoiceRoom = { roomId, roomName ->
                        activeVoiceRoomId = roomId
                        activeVoiceRoomName = roomName
                        scope.launch {
                            voiceRoomMembers = cloudClient.joinOrUpdateVoiceRoom(
                                user = user,
                                roomId = roomId,
                                roomName = roomName,
                                micEnabled = voiceMicEnabled,
                                deafened = voiceDeafened
                            )
                        }
                    },
                    onLeaveVoiceRoom = {
                        val oldRoom = activeVoiceRoomId
                        activeVoiceRoomId = null
                        scope.launch {
                            voiceRoomMembers = cloudClient.leaveVoiceRoom(user.name, oldRoom)
                        }
                    },
                    onToggleVoiceMic = {
                        voiceMicEnabled = !voiceMicEnabled
                        val roomId = activeVoiceRoomId
                        if (roomId != null) {
                            scope.launch {
                                voiceRoomMembers = cloudClient.joinOrUpdateVoiceRoom(
                                    user = user,
                                    roomId = roomId,
                                    roomName = activeVoiceRoomName,
                                    micEnabled = voiceMicEnabled,
                                    deafened = voiceDeafened
                                )
                            }
                        }
                    },
                    onToggleVoiceDeaf = {
                        voiceDeafened = !voiceDeafened
                        val roomId = activeVoiceRoomId
                        if (roomId != null) {
                            scope.launch {
                                voiceRoomMembers = cloudClient.joinOrUpdateVoiceRoom(
                                    user = user,
                                    roomId = roomId,
                                    roomName = activeVoiceRoomName,
                                    micEnabled = voiceMicEnabled,
                                    deafened = voiceDeafened
                                )
                            }
                        }
                    },
                    onOpenChannel = { ch ->
                        activeChat = ActiveChatSession(
                            channelKey = ch.key,
                            title = ch.title,
                            subtitle = "${currentGroup.name} • ${ch.subtitle}",
                            isDm = false
                        )
                    },
                    onOpenMemberDm = { memberNick ->
                        if (!memberNick.equals(user.name, ignoreCase = true)) {
                            val pair = listOf(user.name.lowercase(), memberNick.lowercase()).sorted().joinToString("_")
                            activeChat = ActiveChatSession(
                                channelKey = "dm_$pair",
                                title = memberNick,
                                subtitle = "Личная переписка",
                                isDm = true
                            )
                        }
                    }
                )
            }

            MarkensBottomTab.IDEAS -> {
                IdeasFeedbackTab(
                    currentUser = user,
                    items = feedbackItems,
                    palette = palette,
                    onSubmitIdea = { category, text ->
                        scope.launch {
                            cloudClient.submitFeedback(user, category, text)
                            feedbackItems = cloudClient.fetchFeedbackBoard()
                        }
                    },
                    onVote = { item ->
                        scope.launch {
                            cloudClient.voteFeedback(item, user.name)
                            feedbackItems = cloudClient.fetchFeedbackBoard()
                        }
                    },
                    onUpdateItem = { item, newStatus, creatorReply, newText ->
                        scope.launch {
                            cloudClient.updateFeedbackItem(
                                id = item.id,
                                newStatus = newStatus,
                                creatorReply = creatorReply,
                                newText = newText
                            )
                            feedbackItems = cloudClient.fetchFeedbackBoard()
                        }
                    },
                    onDelete = { item ->
                        scope.launch {
                            cloudClient.deleteFeedback(item.id)
                            feedbackItems = cloudClient.fetchFeedbackBoard()
                        }
                    }
                )
            }

            MarkensBottomTab.PROFILE -> {
                val syncDiag = remember(user, activeOtaPrompt, isRtdbConnected, lastPingMs, lastSyncedTimeStr) {
                    cloudClient.buildSyncDiagnostics(
                        user = user,
                        otaRelease = activeOtaPrompt,
                        isConnected = isRtdbConnected,
                        pingMs = lastPingMs,
                        lastSyncedTime = lastSyncedTimeStr
                    )
                }
                ProfileSettingsTab(
                    currentUser = user,
                    activeTheme = activeTheme,
                    startupSoundEnabled = startupSoundEnabled,
                    syncDiagnostics = syncDiag,
                    palette = palette,
                    onForceSyncCheck = {
                        scope.launch {
                            val ota = cloudClient.checkCloudOta()
                            val hb = cloudClient.heartbeatAndFetchPresence(user)
                            presenceMap = hb.presenceMap
                            lastPingMs = hb.pingMs
                            isRtdbConnected = hb.isConnected
                            lastSyncedTimeStr = hb.syncedTimeFormatted
                            if (ota != null && cloudClient.isNewerOtaAvailable(ota)) {
                                activeOtaPrompt = ota
                                toastBanner = "🚀 Найдена новая версия v${ota.version}"
                            } else {
                                toastBanner = "✓ Синхронизировано с ПК v31.2.0 (${hb.pingMs} мс)"
                            }
                        }
                    },
                    onUpdateUser = { updated ->
                        currentUser = updated
                        savedAccounts = cloudClient.getSavedAccounts()
                        scope.launch {
                            cloudClient.updateProfileInCloud(updated)
                            val hb = cloudClient.heartbeatAndFetchPresence(updated)
                            presenceMap = hb.presenceMap
                        }
                    },
                    onChangePassword = { newPass ->
                        val res = cloudClient.changeAccountPassword(user, newPass)
                        res.onSuccess { updatedUser ->
                            currentUser = updatedUser
                            savedAccounts = cloudClient.getSavedAccounts()
                            rememberedCreds = cloudClient.getLastRememberedCredentials()
                            toastBanner = "✓ Пароль обновлён и сохранён (PBKDF2 v31)"
                        }
                        res
                    },
                    onChangeTheme = { newTheme ->
                        activeTheme = newTheme
                        cloudClient.saveTheme(newTheme)
                    },
                    onToggleStartupSound = { enabled ->
                        startupSoundEnabled = enabled
                        cloudClient.setStartupSoundEnabled(enabled)
                        if (enabled) {
                            scope.launch { MarkensSoundEngine.playWelcomeChime() }
                        }
                    },
                    onPreviewStartupSound = {
                        scope.launch { MarkensSoundEngine.playWelcomeChime() }
                    },
                    onLogout = {
                        scope.launch {
                            if (activeVoiceRoomId != null) {
                                cloudClient.leaveVoiceRoom(user.name, activeVoiceRoomId)
                                activeVoiceRoomId = null
                            }
                            callManager.declineOrEndCall(user.name)
                            cloudClient.setOfflineInCloud(user.name)
                            cloudClient.clearSession()
                            savedAccounts = cloudClient.getSavedAccounts()
                            rememberedCreds = cloudClient.getLastRememberedCredentials()
                            currentUser = null
                        }
                    }
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgPrimary)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("markens_mobile_root")
    ) {
        val bannerText = toastBanner
        if (bannerText != null) {
            Surface(
                color = palette.brandColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { toastBanner = null }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bannerText,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Скрыть",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Top-level Voice & Video Call Overlay (visible across all tabs and chats)
        val callSess = activeCallSession
        if (callSess != null) {
            val peerAv = presenceMap[callSess.peerName.lowercase()]?.avatarUrl
                ?: cloudClient.getUserAvatar(callSess.peerName)
            MarkensCallOverlay(
                session = callSess,
                durationSec = activeCallDurationSec,
                currentUser = user,
                peerAvatarUrl = peerAv,
                callManager = callManager,
                palette = palette,
                onPermissionError = { msg -> toastBanner = "⚠️ $msg" }
            )
        }

        val otaRelease = activeOtaPrompt
        if (otaRelease != null) {
            val isAlreadyApplied = installedOtaVer == otaRelease.version && otaRelease.apkUrl == null
            Surface(
                color = palette.bgSecondary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .border(1.dp, palette.brandColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .testTag("ota_auto_update_banner")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = palette.brandColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isAlreadyApplied) "⚡ АВТООБНОВЛЕНИЕ УСТАНОВЛЕНО" else "🚀 НОВОЕ ОБНОВЛЕНИЕ",
                                color = palette.brandColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "v${otaRelease.version}",
                            color = palette.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { activeOtaPrompt = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = palette.textMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = otaRelease.changelog,
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (otaRelease.apkUrl != null) {
                            Button(
                                onClick = {
                                    val url = otaRelease.apkUrl
                                    if (!url.startsWith("https://", ignoreCase = true) || otaRelease.signature.isBlank()) {
                                        toastBanner = "⚠️ Небезопасная ссылка обновления отклонена (требуется HTTPS и подпись)"
                                        activeOtaPrompt = null
                                        return@Button
                                    }
                                    scope.launch {
                                        cloudClient.applyOtaRelease(otaRelease)
                                        installedOtaVer = otaRelease.version
                                        activeOtaPrompt = null
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        } catch (_: Exception) {
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.greenColor),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("📥 Скачать APK v${otaRelease.version}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else if (!isAlreadyApplied) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        cloudClient.applyOtaRelease(otaRelease)
                                        installedOtaVer = otaRelease.version
                                        val refreshedGroups = cloudClient.fetchServerGroups()
                                        if (refreshedGroups.isNotEmpty()) serverGroups = refreshedGroups
                                        cloudClient.preloadKeyChannels(user.name, friends, serverGroups)
                                        previewRefreshTick++
                                        activeOtaPrompt = null
                                        toastBanner = "✅ Обновление v${otaRelease.version} применено!"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.brandColor),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("⚡ Применить сейчас", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Surface(
                                onClick = {
                                    activeOtaPrompt = null
                                    activeChat = ActiveChatSession(
                                        channelKey = "updates",
                                        title = "обновления",
                                        subtitle = "Автообновления и чейнджлог",
                                        isDm = false
                                    )
                                },
                                color = palette.bgTertiary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Открыть #обновления →",
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ADAPTIVE MAIN CONTENT AREA:
        // - Compact (< 600.dp): Smartphone full-screen Chat when open, otherwise active Tab + Bottom Bar
        // - Medium Tablet / Foldable (600.dp..839.dp): Canonical List-Detail Split View + Bottom Bar
        // - Expanded Tablet / Pixel Tablet (>= 840.dp): Side Navigation Rail + List-Detail Split View
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWideScreen = maxWidth >= 600.dp
            val useNavigationRail = maxWidth >= 840.dp
            val listPaneWidth = if (maxWidth >= 960.dp) 360.dp else 310.dp
            val openSession = activeChat

            @Composable
            fun RenderSplitOrCenteredWorkspace() {
                if (selectedTab == MarkensBottomTab.CHATS || selectedTab == MarkensBottomTab.GROUPS) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .width(listPaneWidth)
                                .fillMaxHeight()
                        ) {
                            RenderPrimaryTabContent()
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(palette.dividerColor)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            if (openSession != null) {
                                RenderChatRoomPane(session = openSession, showBackButton = false)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(palette.bgPrimary)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Выберите переписку или канал слева",
                                            color = palette.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Адаптивный режим для планшетов (Pixel Tablet / Fold) и больших экранов",
                                            color = palette.textMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(palette.bgPrimary),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 820.dp)
                                .fillMaxWidth()
                        ) {
                            RenderPrimaryTabContent()
                        }
                    }
                }
            }

            if (useNavigationRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    TabletNavigationRail(
                        selectedTab = selectedTab,
                        friendRequestsCount = friendRequests.size,
                        palette = palette,
                        onSelectTab = { selectedTab = it }
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(palette.dividerColor)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        RenderSplitOrCenteredWorkspace()
                    }
                }
            } else if (isWideScreen) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        RenderSplitOrCenteredWorkspace()
                    }
                    CompactMobileBottomBar(
                        selectedTab = selectedTab,
                        friendRequestsCount = friendRequests.size,
                        palette = palette,
                        onSelectTab = { selectedTab = it }
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (openSession != null) {
                        Box(modifier = Modifier.weight(1f)) {
                            RenderChatRoomPane(session = openSession, showBackButton = true)
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            RenderPrimaryTabContent()
                        }
                        CompactMobileBottomBar(
                            selectedTab = selectedTab,
                            friendRequestsCount = friendRequests.size,
                            palette = palette,
                            onSelectTab = { selectedTab = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletNavigationRail(
    selectedTab: MarkensBottomTab,
    friendRequestsCount: Int,
    palette: MarkensPalette,
    onSelectTab: (MarkensBottomTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(78.dp)
            .fillMaxHeight()
            .background(palette.bgTertiary)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.brandColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "M",
                color = palette.greenColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        BottomTabItem(
            selected = selectedTab == MarkensBottomTab.CHATS,
            icon = Icons.AutoMirrored.Filled.Chat,
            label = "Чаты",
            badgeCount = friendRequestsCount,
            palette = palette,
            testTag = "nav_tab_chats",
            onClick = { onSelectTab(MarkensBottomTab.CHATS) },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        )
        BottomTabItem(
            selected = selectedTab == MarkensBottomTab.GROUPS,
            icon = Icons.Default.Forum,
            label = "Группы",
            badgeCount = 0,
            palette = palette,
            testTag = "nav_tab_groups",
            onClick = { onSelectTab(MarkensBottomTab.GROUPS) },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        )
        BottomTabItem(
            selected = selectedTab == MarkensBottomTab.IDEAS,
            icon = Icons.Default.Lightbulb,
            label = "Идеи",
            badgeCount = 0,
            palette = palette,
            testTag = "nav_tab_ideas",
            onClick = { onSelectTab(MarkensBottomTab.IDEAS) },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        )
        BottomTabItem(
            selected = selectedTab == MarkensBottomTab.PROFILE,
            icon = Icons.Default.Person,
            label = "Профиль",
            badgeCount = 0,
            palette = palette,
            testTag = "nav_tab_profile",
            onClick = { onSelectTab(MarkensBottomTab.PROFILE) },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        )
    }
}

@Composable
private fun CompactMobileBottomBar(
    selectedTab: MarkensBottomTab,
    friendRequestsCount: Int,
    palette: MarkensPalette,
    onSelectTab: (MarkensBottomTab) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = palette.dividerColor, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(palette.bgTertiary)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomTabItem(
                selected = selectedTab == MarkensBottomTab.CHATS,
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "Чаты",
                badgeCount = friendRequestsCount,
                palette = palette,
                testTag = "nav_tab_chats",
                onClick = { onSelectTab(MarkensBottomTab.CHATS) },
                modifier = Modifier.weight(1f)
            )
            BottomTabItem(
                selected = selectedTab == MarkensBottomTab.GROUPS,
                icon = Icons.Default.Forum,
                label = "Группы",
                badgeCount = 0,
                palette = palette,
                testTag = "nav_tab_groups",
                onClick = { onSelectTab(MarkensBottomTab.GROUPS) },
                modifier = Modifier.weight(1f)
            )
            BottomTabItem(
                selected = selectedTab == MarkensBottomTab.IDEAS,
                icon = Icons.Default.Lightbulb,
                label = "Идеи",
                badgeCount = 0,
                palette = palette,
                testTag = "nav_tab_ideas",
                onClick = { onSelectTab(MarkensBottomTab.IDEAS) },
                modifier = Modifier.weight(1f)
            )
            BottomTabItem(
                selected = selectedTab == MarkensBottomTab.PROFILE,
                icon = Icons.Default.Person,
                label = "Профиль",
                badgeCount = 0,
                palette = palette,
                testTag = "nav_tab_profile",
                onClick = { onSelectTab(MarkensBottomTab.PROFILE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    badgeCount: Int,
    palette: MarkensPalette,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = if (palette.isLight) palette.brandColor else Color.White
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(26.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(palette.brandColor)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) activeColor else palette.textMuted,
                    modifier = Modifier.size(20.dp)
                )
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-4).dp)
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(palette.greenColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = if (selected) activeColor else palette.textMuted,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MarkensNativeAuthScreen(
    initialLogin: String,
    initialPassword: String,
    savedAccounts: List<MarkensSavedAccount>,
    palette: MarkensPalette,
    onRemoveSavedAccount: (String) -> Unit,
    onLoginWithSavedAccount: suspend (MarkensSavedAccount) -> Result<MarkensUser>,
    onAuthenticate: suspend (login: String, pass: String, isRegister: Boolean) -> Result<MarkensUser>
) {
    val scope = rememberCoroutineScope()
    var isRegisterMode by remember { mutableStateOf(false) }
    var loginInput by remember(initialLogin) { mutableStateOf(initialLogin) }
    var passwordInput by remember(initialPassword) { mutableStateOf(initialPassword) }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgTertiary)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = palette.bgSecondary,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .border(1.dp, palette.borderColor, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.brandColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            color = palette.greenColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MARKENS",
                            color = palette.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Мессенджер и сообщества",
                            color = palette.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                // 1-Tap Saved Accounts section (uses non-reversible sessionVerifier verified on server)
                if (savedAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.bgTertiary)
                            .border(1.dp, palette.borderColor, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "СОХРАНЁННЫЕ АККАУНТЫ (ВХОД В 1 КАСАНИЕ)",
                            color = palette.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        savedAccounts.forEach { acc ->
                            Surface(
                                onClick = {
                                    if (!isSubmitting) {
                                        loginInput = acc.login
                                        isRegisterMode = false
                                        errorMessage = null
                                        isSubmitting = true
                                        scope.launch {
                                            val res = onLoginWithSavedAccount(acc)
                                            res.onFailure { errorMessage = it.message }
                                            isSubmitting = false
                                        }
                                    }
                                },
                                color = palette.bgSecondary,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .testTag("saved_account_${acc.login}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MarkensAvatarBox(
                                        name = acc.login,
                                        avatarUrl = acc.avatarUrl,
                                        size = 32.dp,
                                        bgColor = palette.brandColor,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = acc.login,
                                                color = palette.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = acc.badge, fontSize = 12.sp)
                                        }
                                        Text(
                                            text = "Нажмите для защищённого входа",
                                            color = palette.greenColor,
                                            fontSize = 11.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveSavedAccount(acc.login) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Удалить из списка",
                                            tint = palette.textMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Switcher: Вход / Регистрация
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.bgTertiary)
                        .padding(4.dp)
                ) {
                    Surface(
                        onClick = {
                            isRegisterMode = false
                            errorMessage = null
                        },
                        color = if (!isRegisterMode) palette.brandColor else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auth_mode_login")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Вход",
                                color = if (!isRegisterMode) Color.White else palette.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            isRegisterMode = true
                            errorMessage = null
                        },
                        color = if (isRegisterMode) palette.brandColor else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auth_mode_register")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Регистрация",
                                color = if (isRegisterMode) Color.White else palette.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = loginInput,
                    onValueChange = {
                        loginInput = it
                        errorMessage = null
                    },
                    label = { Text("Никнейм") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_login_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.brandColor,
                        unfocusedBorderColor = palette.borderColor,
                        focusedContainerColor = palette.bgTertiary,
                        unfocusedContainerColor = palette.bgTertiary
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        errorMessage = null
                    },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Показать пароль",
                                tint = palette.textMuted
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.brandColor,
                        unfocusedBorderColor = palette.borderColor,
                        focusedContainerColor = palette.bgTertiary,
                        unfocusedContainerColor = palette.bgTertiary
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                scope.launch {
                                    val res = onAuthenticate(loginInput, passwordInput, isRegisterMode)
                                    res.onFailure { errorMessage = it.message }
                                    isSubmitting = false
                                }
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "✓ Данные автоматически запоминаются при регистрации и входе",
                    color = palette.greenColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("auth_error_text")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (!isSubmitting) {
                            isSubmitting = true
                            scope.launch {
                                val res = onAuthenticate(loginInput, passwordInput, isRegisterMode)
                                res.onFailure { errorMessage = it.message }
                                isSubmitting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.brandColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("auth_submit_button")
                ) {
                    Text(
                        text = if (isSubmitting) {
                            "Подождите..."
                        } else if (isRegisterMode) {
                            "Зарегистрироваться"
                        } else {
                            "Войти"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatsAndFriendsTab(
    currentUser: MarkensUser,
    cloudClient: MarkensCloudClient,
    friends: List<String>,
    friendRequests: List<MarkensFriendRequest>,
    presenceMap: Map<String, MarkensPresence>,
    previewRefreshTick: Int,
    vortexJoined: Boolean,
    vortexInviteDismissed: Boolean,
    palette: MarkensPalette,
    onOpenDm: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onAcceptVortexFromBanner: () -> Unit,
    onDismissVortexBanner: () -> Unit,
    onAddFriend: suspend (String) -> Result<Unit>,
    onAcceptRequest: (String) -> Unit,
    onDeclineRequest: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PersonalChatFilter.ALL) }
    var showAddFriendInput by remember { mutableStateOf(false) }
    var addFriendNick by remember { mutableStateOf("") }
    var addFriendFeedback by remember { mutableStateOf<String?>(null) }
    var addFriendSuccess by remember { mutableStateOf(false) }

    val filteredFriends = remember(friends, searchQuery, selectedFilter, presenceMap) {
        val deduped = LinkedHashMap<String, String>()
        friends.forEach { f -> deduped.putIfAbsent(f.lowercase(), f) }
        val base = if (searchQuery.isBlank()) deduped.values.toList()
        else deduped.values.filter { it.contains(searchQuery.trim(), ignoreCase = true) }

        when (selectedFilter) {
            PersonalChatFilter.ONLINE -> base.filter { presenceMap[it.lowercase()]?.isOnlineNow == true }
            PersonalChatFilter.ALL -> base
        }
    }

    val onlinePeers = remember(friends, presenceMap, currentUser.name) {
        val mapByLower = LinkedHashMap<String, String>()
        friends.forEach { f ->
            if (presenceMap[f.lowercase()]?.isOnlineNow == true && !f.equals(currentUser.name, ignoreCase = true)) {
                mapByLower.putIfAbsent(f.lowercase(), f)
            }
        }
        presenceMap.values.forEach { p ->
            if (p.isOnlineNow && !p.user.equals(currentUser.name, ignoreCase = true)) {
                mapByLower.putIfAbsent(p.user.lowercase(), p.user)
            }
        }
        mapByLower.values.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgPrimary)
    ) {
        // 1. Sleek Top Header (Personal DMs + Global Ideas)
        Surface(
            color = palette.bgSecondary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(palette.brandColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            color = palette.greenColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Личные переписки",
                            color = palette.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Друзей: ${friends.size} • В сети: ${onlinePeers.size}",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    }

                    Surface(
                        onClick = {
                            showAddFriendInput = !showAddFriendInput
                            addFriendFeedback = null
                        },
                        color = if (showAddFriendInput) palette.brandColor else palette.bgTertiary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("toggle_add_friend_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Добавить друга",
                                tint = if (showAddFriendInput) Color.White else palette.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Добавить",
                                color = if (showAddFriendInput) Color.White else palette.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Search bar inside header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Поиск личных переписок и друзей...",
                                color = palette.textMuted,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = palette.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Очистить",
                                        tint = palette.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .testTag("chats_search_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = palette.bgTertiary,
                            unfocusedContainerColor = palette.bgTertiary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        )
                    )
                }

                // Filter Tabs (Все личные / В сети)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PersonalChatFilter.values().forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            onClick = { selectedFilter = filter },
                            color = if (isSelected) palette.brandColor else palette.bgTertiary,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("folder_tab_${filter.name.lowercase()}")
                        ) {
                            Text(
                                text = filter.title,
                                color = if (isSelected) Color.White else palette.textMuted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Collapsible Add Friend Drawer
                AnimatedVisibility(visible = showAddFriendInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.bgTertiary)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "ДОБАВИТЬ ДРУГА ПО НИКУ",
                            color = palette.textMuted,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = addFriendNick,
                                onValueChange = {
                                    addFriendNick = it
                                    addFriendFeedback = null
                                },
                                placeholder = { Text("Введите никнейм...", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_friend_nick_input"),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary,
                                    focusedBorderColor = palette.brandColor,
                                    unfocusedBorderColor = palette.borderColor,
                                    focusedContainerColor = palette.bgSecondary,
                                    unfocusedContainerColor = palette.bgSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val target = addFriendNick.trim()
                                    if (target.isNotEmpty()) {
                                        scope.launch {
                                            val res = onAddFriend(target)
                                            res.onSuccess {
                                                addFriendSuccess = true
                                                addFriendFeedback = "✓ Заявка отправлена пользователю @$target"
                                                addFriendNick = ""
                                            }.onFailure { err ->
                                                addFriendSuccess = false
                                                addFriendFeedback = err.message
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.brandColor),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("send_friend_request_button")
                            ) {
                                Text("Отправить", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }
                        }
                        if (addFriendFeedback != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = addFriendFeedback ?: "",
                                color = if (addFriendSuccess) palette.greenColor else Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                HorizontalDivider(color = palette.dividerColor, thickness = 1.dp)
            }
        }

        // 2. Personal Chats List + Top Global "Идеи и фидбек" + Optional VORTEX Invite
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // VORTEX Community Invitation Banner (if user hasn't joined VORTEX yet and hasn't dismissed it)
            if (!vortexJoined && !vortexInviteDismissed) {
                item(key = "vortex_invite_banner_top") {
                    Surface(
                        color = palette.bgSecondary,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .border(1.dp, palette.brandColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .testTag("chats_vortex_invite_banner")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.brandColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "VX",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Приглашение в группу VORTEX",
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Хотите вступить в сообщество VORTEX?",
                                        color = palette.textMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onAcceptVortexFromBanner,
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.brandColor),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("banner_join_vortex_button")
                                ) {
                                    Text("Хочу вступить", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = onDismissVortexBanner,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("banner_decline_vortex_button")
                                ) {
                                    Text("Не сейчас", color = palette.textMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Horizontal "В сети сейчас" strip
            if (onlinePeers.isNotEmpty()) {
                item(key = "online_peers_strip") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.bgSecondary.copy(alpha = 0.55f))
                            .padding(vertical = 8.dp)
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(onlinePeers, key = { "online_strip_$it" }) { peerNick ->
                                val isCreator = peerNick.equals("markens", ignoreCase = true)
                                val peerAv = presenceMap[peerNick.lowercase()]?.avatarUrl
                                    ?: cloudClient.getUserAvatar(peerNick)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(56.dp)
                                        .clickable { onOpenDm(peerNick) }
                                ) {
                                    Box(modifier = Modifier.size(44.dp)) {
                                        MarkensAvatarBox(
                                            name = peerNick,
                                            avatarUrl = peerAv,
                                            size = 44.dp,
                                            bgColor = if (isCreator) palette.brandColor else Color(0xFF475569),
                                            fontSize = 15.sp,
                                            modifier = Modifier.border(1.5.dp, palette.greenColor, CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .align(Alignment.BottomEnd)
                                                .clip(CircleShape)
                                                .background(palette.greenColor)
                                                .border(2.dp, palette.bgPrimary, CircleShape)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = peerNick,
                                        color = palette.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = palette.dividerColor, thickness = 1.dp)
                }
            }

            // Incoming Friend Requests
            if (friendRequests.isNotEmpty()) {
                item(key = "incoming_friend_requests") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.bgSecondary)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "ВХОДЯЩИЕ ЗАЯВКИ В ДРУЗЬЯ (${friendRequests.size})",
                            color = palette.greenColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        friendRequests.forEach { req ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MarkensAvatarBox(
                                    name = req.from,
                                    avatarUrl = presenceMap[req.from.lowercase()]?.avatarUrl,
                                    size = 36.dp,
                                    bgColor = palette.brandColor,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.from,
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Хочет добавить вас в друзья",
                                        color = palette.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onAcceptRequest(req.from) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Принять", tint = palette.greenColor)
                                }
                                IconButton(
                                    onClick = { onDeclineRequest(req.from) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Отклонить", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = palette.dividerColor, thickness = 1.dp)
                }
            }

            // Saved Messages ("Избранное") row
            if (selectedFilter == PersonalChatFilter.ALL) {
                item(key = "favorites_saved_messages_row") {
                    val lastFav = remember(currentUser.name, previewRefreshTick) {
                        cloudClient.getChannelLastPreview("fav_${currentUser.name.lowercase()}")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFavorites() }
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                            .testTag("favorites_chat_card"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(palette.brandColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Избранное",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Избранное",
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (lastFav != null) {
                                    Text(
                                        text = lastFav.time,
                                        color = palette.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = lastFav?.text ?: "Личные заметки и сохранённые сообщения",
                                color = palette.textMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(
                        color = palette.dividerColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                }
            }

            // Friends & Direct Messages Rows
            if (filteredFriends.isEmpty()) {
                item(key = "empty_friends_placeholder") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (selectedFilter == PersonalChatFilter.ONLINE) {
                                    "Сейчас никто из друзей не в сети"
                                } else {
                                    "У вас пока нет личных переписок"
                                },
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Нажмите «Добавить» сверху, чтобы найти друга по нику и начать общение",
                                color = palette.textMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredFriends,
                    key = { "dm_friend_$it" },
                    contentType = { "dm_row" }
                ) { friendNick ->
                    val pres = presenceMap[friendNick.lowercase()]
                    val isOnline = pres?.isOnlineNow == true
                    val isDnd = pres?.isDnd == true
                    val dotColor = when {
                        isOnline && isDnd -> Color(0xFFF59E0B)
                        isOnline -> palette.greenColor
                        else -> Color(0xFF6B7280)
                    }
                    val isCreator = friendNick.equals("markens", ignoreCase = true)
                    val friendAv = pres?.avatarUrl ?: cloudClient.getUserAvatar(friendNick)
                    val lastPreview = remember(currentUser.name, friendNick, previewRefreshTick) {
                        cloudClient.getDmLastPreview(currentUser.name, friendNick)
                    }
                    val unreadCount = remember(currentUser.name, friendNick, previewRefreshTick) {
                        cloudClient.getDmUnreadCount(currentUser.name, friendNick)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDm(friendNick) }
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                            .testTag("dm_friend_row_$friendNick"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(46.dp)) {
                            MarkensAvatarBox(
                                name = friendNick,
                                avatarUrl = friendAv,
                                size = 46.dp,
                                bgColor = if (isCreator) palette.brandColor else Color(0xFF475569),
                                fontSize = 17.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .border(2.dp, palette.bgPrimary, CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = friendNick,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isCreator) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "👑 СОЗДАТЕЛЬ",
                                            color = Color(0xFFD97706),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                if (lastPreview != null && lastPreview.sender.equals(currentUser.name, ignoreCase = true)) {
                                    val isRead = lastPreview.isReadByOthers()
                                    val statusIcon = when {
                                        lastPreview.deliveryStatus == MessageDeliveryStatus.FAILED -> Icons.Default.ErrorOutline
                                        lastPreview.deliveryStatus == MessageDeliveryStatus.SENDING -> Icons.Default.Schedule
                                        isRead || lastPreview.deliveryStatus == MessageDeliveryStatus.READ -> Icons.Default.DoneAll
                                        else -> Icons.Default.Check
                                    }
                                    val statusTint = when {
                                        lastPreview.deliveryStatus == MessageDeliveryStatus.FAILED -> Color(0xFFEF4444)
                                        isRead || lastPreview.deliveryStatus == MessageDeliveryStatus.READ -> palette.greenColor
                                        else -> palette.textMuted
                                    }
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = when {
                                            lastPreview.deliveryStatus == MessageDeliveryStatus.FAILED -> "Не доставлено"
                                            lastPreview.deliveryStatus == MessageDeliveryStatus.SENDING -> "Отправляется"
                                            isRead -> "Прочитано"
                                            else -> "Отправлено"
                                        },
                                        tint = statusTint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = lastPreview?.time ?: (pres?.formatPresenceSubtitle() ?: ""),
                                    color = if (lastPreview == null && isOnline) dotColor else palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val subtitleLine = when {
                                    lastPreview != null -> {
                                        val prefix = if (lastPreview.sender.equals(currentUser.name, ignoreCase = true)) {
                                            "Вы: "
                                        } else ""
                                        "$prefix${lastPreview.text}"
                                    }
                                    pres != null -> pres.formatPresenceSubtitle()
                                    else -> "Нажмите, чтобы открыть переписку"
                                }
                                Text(
                                    text = subtitleLine,
                                    color = when {
                                        unreadCount > 0 -> palette.textPrimary
                                        lastPreview != null -> palette.textSecondary
                                        else -> palette.textMuted
                                    },
                                    fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = palette.brandColor,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = unreadCount.toString(),
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = palette.dividerColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscordGroupsTab(
    currentUser: MarkensUser,
    cloudClient: MarkensCloudClient,
    groups: List<MarkensServerGroup>,
    selectedGroup: MarkensServerGroup,
    vortexJoined: Boolean,
    friends: List<String>,
    presenceMap: Map<String, MarkensPresence>,
    voiceRoomMembers: List<MarkensVoiceRoomMember>,
    activeVoiceRoomId: String?,
    voiceMicEnabled: Boolean,
    voiceDeafened: Boolean,
    serverRoles: List<MarkensServerRole>,
    previewRefreshTick: Int,
    palette: MarkensPalette,
    onSelectGroup: (MarkensServerGroup) -> Unit,
    onJoinVortex: () -> Unit,
    onDeclineVortex: () -> Unit,
    onCreateGroup: (name: String, description: String) -> Unit,
    onAddTextChannel: (title: String) -> Unit,
    onJoinVoiceRoom: (roomId: String, roomName: String) -> Unit,
    onLeaveVoiceRoom: () -> Unit,
    onToggleVoiceMic: () -> Unit,
    onToggleVoiceDeaf: () -> Unit,
    onOpenChannel: (MarkensChannelItem) -> Unit,
    onOpenMemberDm: (String) -> Unit
) {
    var showCreateGroupForm by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupDesc by remember { mutableStateOf("") }

    var showAddChannelForm by remember { mutableStateOf(false) }
    var newChannelTitle by remember { mutableStateOf("") }

    var channelsCategoryExpanded by remember { mutableStateOf(true) }
    var voiceCategoryExpanded by remember { mutableStateOf(true) }
    var membersCategoryExpanded by remember { mutableStateOf(true) }

    val onlineTotal = remember(presenceMap, currentUser.name, currentUser.status) {
        val othersOnline = presenceMap.values.count {
            !it.user.equals(currentUser.name, ignoreCase = true) && it.isOnlineNow
        }
        val selfOnline = if (currentUser.status.contains("Невидимка", ignoreCase = true)) 0 else 1
        othersOnline + selfOnline
    }

    val serverMembers = remember(presenceMap, friends, currentUser.name, currentUser.status) {
        val mapByLower = LinkedHashMap<String, String>()
        mapByLower[currentUser.name.lowercase()] = currentUser.name
        friends.forEach { f ->
            val low = f.trim().lowercase()
            if (low.isNotEmpty() && low != "гость" && low != "guest") {
                mapByLower.putIfAbsent(low, f.trim())
            }
        }
        presenceMap.values.forEach { p ->
            val low = p.user.trim().lowercase()
            if (low.isNotEmpty() && low != "гость" && low != "guest") {
                mapByLower.putIfAbsent(low, p.user.trim())
            }
        }
        mapByLower.values.sortedWith(
            compareByDescending<String> { name ->
                val isMe = name.equals(currentUser.name, ignoreCase = true)
                if (isMe) !currentUser.status.contains("Невидимка", ignoreCase = true)
                else presenceMap[name.lowercase()]?.isOnlineNow == true
            }.thenBy { it.lowercase() }
        )
    }

    val isVortexInviteRequired = selectedGroup.isOfficialVortex && !vortexJoined

    // Authentic Discord Mobile Split View: Left Vertical Server Rail (68.dp) + Right Channel Drawer
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgSecondary)
    ) {
        // 1. LEFT VERTICAL SERVER RAIL
        Column(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight()
                .background(palette.bgTertiary)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            groups.forEach { grp ->
                val isSelected = grp.id == selectedGroup.id
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(4.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .background(palette.brandColor)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(3.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                                .background(palette.textMuted)
                        )
                    }

                    Surface(
                        onClick = {
                            onSelectGroup(grp)
                            showCreateGroupForm = false
                        },
                        color = if (isSelected) palette.brandColor else palette.bgSecondary,
                        shape = RoundedCornerShape(if (isSelected) 15.dp else 24.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                width = if (isSelected) 0.dp else 1.dp,
                                color = if (isSelected) Color.Transparent else palette.borderColor,
                                shape = RoundedCornerShape(if (isSelected) 15.dp else 24.dp)
                            )
                            .testTag("server_rail_${grp.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = grp.shortTag.take(2).uppercase(),
                                color = if (isSelected) Color.White else palette.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = palette.borderColor,
                thickness = 1.dp,
                modifier = Modifier.width(32.dp)
            )

            // "+" Create Server Button in Rail
            Surface(
                onClick = { showCreateGroupForm = !showCreateGroupForm },
                color = if (showCreateGroupForm) palette.greenColor else palette.bgSecondary,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, palette.borderColor, RoundedCornerShape(24.dp))
                    .testTag("create_group_pill_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Создать сервер",
                        tint = if (showCreateGroupForm) Color.White else palette.greenColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 2. RIGHT SERVER & CHANNELS PANE
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(palette.bgSecondary)
        ) {
            // Server Header Banner
            Surface(
                color = palette.bgSecondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedGroup.name,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(palette.greenColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$onlineTotal в сети",
                                    color = palette.greenColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = selectedGroup.description,
                                color = palette.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!isVortexInviteRequired) {
                            IconButton(
                                onClick = { showAddChannelForm = !showAddChannelForm },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("add_text_channel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Добавить канал",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = palette.dividerColor, thickness = 1.dp)
                }
            }

            // Create Server Drawer
            AnimatedVisibility(visible = showCreateGroupForm) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.bgPrimary)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "СОЗДАТЬ НОВУЮ ГРУППУ",
                        color = palette.greenColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        placeholder = { Text("Название группы...", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_group_name_input"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.greenColor,
                            unfocusedBorderColor = palette.borderColor,
                            focusedContainerColor = palette.bgTertiary,
                            unfocusedContainerColor = palette.bgTertiary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newGroupDesc,
                        onValueChange = { newGroupDesc = it },
                        placeholder = { Text("Описание группы...", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.greenColor,
                            unfocusedBorderColor = palette.borderColor,
                            focusedContainerColor = palette.bgTertiary,
                            unfocusedContainerColor = palette.bgTertiary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { showCreateGroupForm = false }) {
                            Text("Отмена", color = palette.textMuted, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newGroupName.isNotBlank()) {
                                    onCreateGroup(newGroupName.trim(), newGroupDesc.trim())
                                    newGroupName = ""
                                    newGroupDesc = ""
                                    showCreateGroupForm = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.greenColor),
                            modifier = Modifier.testTag("confirm_create_group_button")
                        ) {
                            Text("Создать", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = palette.dividerColor, thickness = 1.dp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // Create Text Channel Drawer
            AnimatedVisibility(visible = showAddChannelForm && !isVortexInviteRequired) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.bgPrimary)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "НОВЫЙ ТЕКСТОВЫЙ КАНАЛ В ${selectedGroup.name.uppercase()}",
                        color = palette.brandColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newChannelTitle,
                            onValueChange = { newChannelTitle = it },
                            placeholder = { Text("название-канала", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_channel_title_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = palette.textPrimary,
                                unfocusedTextColor = palette.textPrimary,
                                focusedBorderColor = palette.brandColor,
                                unfocusedBorderColor = palette.borderColor,
                                focusedContainerColor = palette.bgTertiary,
                                unfocusedContainerColor = palette.bgTertiary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newChannelTitle.isNotBlank()) {
                                    onAddTextChannel(newChannelTitle.trim())
                                    newChannelTitle = ""
                                    showAddChannelForm = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.brandColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Создать", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }

            // VORTEX Invitation Gate OR Channels & Members List
            if (isVortexInviteRequired) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = palette.bgPrimary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.brandColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .testTag("vortex_invite_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.brandColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "VX",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Приглашение в VORTEX",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Хотите вступить в официальную группу VORTEX? Внутри доступны каналы #основной-чат и #обновления-markens.",
                                color = palette.textMuted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onJoinVortex,
                                colors = ButtonDefaults.buttonColors(containerColor = palette.brandColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("accept_vortex_invite_button")
                            ) {
                                Text(
                                    text = "✓ Хочу вступить в VORTEX",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onDeclineVortex,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("decline_vortex_invite_button")
                            ) {
                                Text(
                                    text = "Не сейчас",
                                    color = palette.textMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item(key = "channels_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { channelsCategoryExpanded = !channelsCategoryExpanded }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (channelsCategoryExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = null,
                                tint = palette.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ТЕКСТОВЫЕ КАНАЛЫ — ${selectedGroup.textChannels.size}",
                                color = palette.textMuted,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (channelsCategoryExpanded) {
                        items(
                            items = selectedGroup.textChannels,
                            key = { it.key },
                            contentType = { "channel_row" }
                        ) { ch ->
                            val preview = remember(ch.key, previewRefreshTick) {
                                cloudClient.getChannelLastPreview(ch.key)
                            }
                            Surface(
                                onClick = { onOpenChannel(ch) },
                                color = palette.bgPrimary.copy(alpha = if (palette.isLight) 0.85f else 0.45f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .testTag("channel_row_${ch.key}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#",
                                        color = if (preview != null) palette.brandColor else palette.textMuted,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 19.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = ch.title,
                                                color = palette.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (preview != null) {
                                                Text(
                                                    text = preview.time,
                                                    color = palette.textMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        val subLine = if (preview != null) {
                                            "${preview.sender}: ${preview.text}"
                                        } else {
                                            ch.subtitle
                                        }
                                        if (subLine.isNotBlank()) {
                                            Text(
                                                text = subLine,
                                                color = if (preview != null) palette.textSecondary else palette.textMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = palette.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Collapsible Server Members Category (Discord style)
                    item(key = "members_header") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { membersCategoryExpanded = !membersCategoryExpanded }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (membersCategoryExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = null,
                                tint = palette.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "УЧАСТНИКИ СЕРВЕРА — ${serverMembers.size}",
                                color = palette.textMuted,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (membersCategoryExpanded) {
                        items(
                            items = serverMembers,
                            key = { "srv_member_$it" },
                            contentType = { "srv_member" }
                        ) { memberNick ->
                            val pres = presenceMap[memberNick.lowercase()]
                            val isMe = memberNick.equals(currentUser.name, ignoreCase = true)
                            val isOnline = if (isMe) {
                                !currentUser.status.contains("Невидимка", ignoreCase = true)
                            } else {
                                pres?.isOnlineNow == true
                            }
                            val isDnd = if (isMe) {
                                currentUser.status.contains("беспокоить", ignoreCase = true)
                            } else {
                                pres?.isDnd == true
                            }
                            val dotColor = when {
                                isOnline && isDnd -> Color(0xFFF59E0B)
                                isOnline -> palette.greenColor
                                else -> Color(0xFF6B7280)
                            }
                            val isCreator = memberNick.equals("markens", ignoreCase = true)
                            val avUrl = if (isMe) currentUser.avatarUrl else (pres?.avatarUrl ?: cloudClient.getUserAvatar(memberNick))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenMemberDm(memberNick) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(30.dp)) {
                                    MarkensAvatarBox(
                                        name = memberNick,
                                        avatarUrl = avUrl,
                                        size = 30.dp,
                                        bgColor = if (isCreator) palette.brandColor else Color(0xFF475569),
                                        fontSize = 12.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                            .border(1.5.dp, palette.bgSecondary, CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isMe) "$memberNick (Вы)" else memberNick,
                                            color = if (isCreator) Color(0xFFD97706) else palette.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        if (isCreator) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "👑", fontSize = 11.sp)
                                        }
                                    }
                                    val statusSubtitle = when {
                                        isMe && !isOnline -> "👻 Невидимка (скрыт для всех)"
                                        isMe && isDnd -> "🌙 Не беспокоить • 📱 Android"
                                        isMe -> "В сети • 📱 Android"
                                        pres != null -> pres.formatPresenceSubtitle()
                                        else -> "не в сети"
                                    }
                                    Text(
                                        text = statusSubtitle,
                                        color = when {
                                            isOnline && isDnd -> Color(0xFFF59E0B)
                                            isOnline -> palette.greenColor
                                            else -> palette.textMuted
                                        },
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    if (selectedGroup.isOfficialVortex && !currentUser.isOwner) {
                        item(key = "leave_vortex_footer") {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onDeclineVortex,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .testTag("leave_vortex_button")
                            ) {
                                Text(
                                    text = "Покинуть группу VORTEX",
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Current User Mini-Bar (Discord user panel)
            Surface(
                color = palette.bgTertiary,
                modifier = Modifier.fillMaxWidth()
            ) {
                val selfOnline = !currentUser.status.contains("Невидимка", ignoreCase = true)
                val selfDnd = currentUser.status.contains("беспокоить", ignoreCase = true)
                val selfDotColor = when {
                    selfOnline && selfDnd -> Color(0xFFF59E0B)
                    selfOnline -> palette.greenColor
                    else -> Color(0xFF6B7280)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(32.dp)) {
                        MarkensAvatarBox(
                            name = currentUser.name,
                            avatarUrl = currentUser.avatarUrl,
                            size = 32.dp,
                            bgColor = palette.brandColor,
                            fontSize = 13.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(selfDotColor)
                                .border(1.5.dp, palette.bgTertiary, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentUser.name,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (currentUser.badge.isNotBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = currentUser.badge, fontSize = 11.sp)
                            }
                        }
                        Text(
                            text = currentUser.status,
                            color = selfDotColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdeasFeedbackTab(
    currentUser: MarkensUser,
    items: List<MarkensFeedbackItem>,
    palette: MarkensPalette,
    onSubmitIdea: (category: String, text: String) -> Unit,
    onVote: (MarkensFeedbackItem) -> Unit,
    onUpdateItem: (item: MarkensFeedbackItem, newStatus: String?, creatorReply: String?, newText: String?) -> Unit,
    onDelete: (MarkensFeedbackItem) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("💡 Идея") }
    var statusFilter by remember { mutableStateOf("Все") }
    var ideaText by remember { mutableStateOf("") }

    var editingItemId by remember { mutableStateOf<String?>(null) }
    var editingIdeaText by remember { mutableStateOf("") }
    var editingReplyText by remember { mutableStateOf("") }

    val filteredItems = remember(items, statusFilter) {
        when (statusFilter) {
            "На рассмотрении" -> items.filter { it.status.contains("рассмотрении", ignoreCase = true) || it.status == "Новое" }
            "В работе 🛠" -> items.filter { it.status.contains("работе", ignoreCase = true) }
            "Сделано ✅" -> items.filter { it.status.contains("Сделано", ignoreCase = true) || it.status.contains("Готово", ignoreCase = true) }
            else -> items
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgPrimary)
    ) {
        Surface(
            color = palette.bgSecondary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Идеи и фидбек",
                            color = palette.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (currentUser.isOwner) {
                                "👑 Режим создателя: вы можете менять статус и отвечать на идеи"
                            } else {
                                "Пишите, что хотите видеть в MARKENS — создатель читает и внедряет"
                            },
                            color = if (currentUser.isOwner) Color(0xFFD97706) else palette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Status Filter Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Все", "На рассмотрении", "В работе 🛠", "Сделано ✅").forEach { st ->
                        val selected = statusFilter == st
                        Surface(
                            onClick = { statusFilter = st },
                            color = if (selected) palette.brandColor else palette.bgTertiary,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = st,
                                color = if (selected) Color.White else palette.textSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = palette.dividerColor, thickness = 1.dp)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "idea_composer_card") {
                Surface(
                    color = palette.bgSecondary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.borderColor, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("💡 Идея", "🐛 Баг", "🎨 Дизайн", "⚡ Фича").forEach { cat ->
                                val selected = selectedCategory == cat
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.brandColor,
                                        selectedLabelColor = Color.White,
                                        containerColor = palette.bgTertiary,
                                        labelColor = palette.textSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = ideaText,
                                onValueChange = { ideaText = it },
                                placeholder = { Text("Напишите, что добавить или улучшить...", fontSize = 13.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("idea_input_field"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary,
                                    focusedBorderColor = palette.brandColor,
                                    unfocusedBorderColor = palette.borderColor,
                                    focusedContainerColor = palette.bgTertiary,
                                    unfocusedContainerColor = palette.bgTertiary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                onClick = {
                                    if (ideaText.isNotBlank()) {
                                        onSubmitIdea(selectedCategory, ideaText)
                                        ideaText = ""
                                    }
                                },
                                color = palette.greenColor,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("submit_idea_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Отправить",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(filteredItems, key = { it.id }, contentType = { "feedback_card" }) { item ->
                val isAuthor = item.author.equals(currentUser.name, ignoreCase = true)
                val canManage = currentUser.isOwner || isAuthor
                val hasVoted = item.votedBy.any { it.equals(currentUser.name, ignoreCase = true) }
                val isEditingThis = editingItemId == item.id

                val statusColor = when {
                    item.status.contains("Сделано", ignoreCase = true) || item.status.contains("Готово", ignoreCase = true) -> palette.greenColor
                    item.status.contains("работе", ignoreCase = true) -> Color(0xFFF59E0B)
                    item.status.contains("Отклонено", ignoreCase = true) -> Color(0xFFEF4444)
                    else -> palette.brandColor
                }

                Surface(
                    color = palette.bgSecondary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.borderColor, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = palette.brandColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = item.category,
                                    color = palette.brandColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = statusColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = item.status,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "@${item.author}",
                                color = if (item.isOwner) Color(0xFFD97706) else palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = item.time,
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = item.text,
                            color = palette.textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        )

                        // Creator Official Reply Banner (if present)
                        if (!item.creatorReply.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.bgTertiary)
                                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "👑 Ответ создателя (markens):",
                                        color = Color(0xFFD97706),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.creatorReply,
                                        color = palette.textPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Creator 1-Tap Status Switcher
                        if (currentUser.isOwner) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("На рассмотрении", "В работе 🛠", "Сделано ✅", "Отклонено").forEach { stOption ->
                                    val isCurrent = item.status == stOption
                                    Surface(
                                        onClick = { onUpdateItem(item, stOption, null, null) },
                                        color = if (isCurrent) palette.brandColor else palette.bgTertiary,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.border(
                                            1.dp,
                                            if (isCurrent) palette.brandColor else palette.borderColor,
                                            RoundedCornerShape(6.dp)
                                        )
                                    ) {
                                        Text(
                                            text = stOption,
                                            color = if (isCurrent) Color.White else palette.textMuted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Inline Editor for Creator Reply or Idea Text
                        AnimatedVisibility(visible = isEditingThis) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.bgTertiary)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = editingIdeaText,
                                    onValueChange = { editingIdeaText = it },
                                    label = { Text("Текст идеи / предложения", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                if (currentUser.isOwner) {
                                    OutlinedTextField(
                                        value = editingReplyText,
                                        onValueChange = { editingReplyText = it },
                                        label = { Text("👑 Ответ создателя (видят все)", fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            onUpdateItem(
                                                item,
                                                null,
                                                if (currentUser.isOwner) editingReplyText.trim() else null,
                                                editingIdeaText.trim()
                                            )
                                            editingItemId = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.greenColor),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Сохранить", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { editingItemId = null },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Отмена", color = palette.textMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                onClick = { onVote(item) },
                                color = if (hasVoted) palette.brandColor.copy(alpha = 0.22f) else palette.bgTertiary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.border(
                                    1.dp,
                                    if (hasVoted) palette.brandColor else palette.brandColor.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                            ) {
                                Text(
                                    text = "👍 Поддержать (${item.votes})",
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            if (canManage) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        onClick = {
                                            if (isEditingThis) {
                                                editingItemId = null
                                            } else {
                                                editingItemId = item.id
                                                editingIdeaText = item.text
                                                editingReplyText = item.creatorReply ?: ""
                                            }
                                        },
                                        color = palette.bgTertiary,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = if (currentUser.isOwner) "✏️ Изменить / Ответить" else "✏️ Изменить",
                                            color = palette.textSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDelete(item) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSettingsTab(
    currentUser: MarkensUser,
    activeTheme: String,
    startupSoundEnabled: Boolean,
    syncDiagnostics: MarkensSyncDiagnostics,
    palette: MarkensPalette,
    onForceSyncCheck: () -> Unit,
    onUpdateUser: (MarkensUser) -> Unit,
    onChangePassword: suspend (String) -> Result<MarkensUser>,
    onChangeTheme: (String) -> Unit,
    onToggleStartupSound: (Boolean) -> Unit,
    onPreviewStartupSound: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bioInput by remember(currentUser.bio) { mutableStateOf(currentUser.bio) }
    var saveBanner by remember { mutableStateOf<String?>(null) }

    var showPasswordEditor by remember { mutableStateOf(false) }
    var newPasswordInput by remember { mutableStateOf("") }
    var passwordFeedback by remember { mutableStateOf<String?>(null) }
    var passwordFeedbackSuccess by remember { mutableStateOf(false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    Base64ImageDecoder.encodeAvatarUriToDataUrl(context, uri, targetSizePx = 180)
                }
                if (encoded != null) {
                    onUpdateUser(currentUser.copy(avatarUrl = encoded))
                    saveBanner = "✓ Фотография аватарки обновлена"
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgPrimary),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. DISCORD-STYLE PROFILE BANNER + OVERLAPPING PHOTO AVATAR
        item(key = "profile_header_card") {
            Surface(
                color = palette.bgSecondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            palette.brandColor,
                                            Color(0xFF312E81),
                                            if (palette.isLight) Color(0xFF6366F1) else palette.bgTertiary
                                        )
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .offset(y = 44.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clickable {
                                        avatarPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .testTag("profile_avatar_box")
                            ) {
                                MarkensAvatarBox(
                                    name = currentUser.name,
                                    avatarUrl = currentUser.avatarUrl,
                                    size = 76.dp,
                                    bgColor = palette.brandColor,
                                    fontSize = 28.sp,
                                    modifier = Modifier.border(4.dp, palette.bgSecondary, CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(palette.brandColor)
                                        .border(2.dp, palette.bgSecondary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Сменить аватарку",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            Surface(
                                color = palette.bgTertiary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.border(1.dp, palette.borderColor, RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = if (currentUser.isOwner) "Ур. ∞ MAX" else "Уровень ${currentUser.level} • ${currentUser.xp}/100 XP",
                                    color = palette.greenColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(52.dp))

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentUser.name,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                            if (currentUser.badge.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = currentUser.badge, fontSize = 16.sp)
                            }
                            if (currentUser.isOwner) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "👑 СОЗДАТЕЛЬ",
                                        color = Color(0xFFD97706),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom Photo Avatar Actions Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    avatarPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                color = palette.bgTertiary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .border(1.dp, palette.brandColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .testTag("upload_avatar_photo_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = palette.brandColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (currentUser.avatarUrl != null) "Изменить фото аватарки" else "Поставить фото на аватарку",
                                        color = palette.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (currentUser.avatarUrl != null) {
                                Surface(
                                    onClick = {
                                        onUpdateUser(currentUser.copy(avatarUrl = null))
                                        saveBanner = "✓ Фото аватарки сброшено"
                                    },
                                    color = Color(0xFFEF4444).copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("reset_avatar_photo_button")
                                ) {
                                    Text(
                                        text = "Сбросить",
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        if (!currentUser.isOwner) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (currentUser.xp.coerceIn(0, 100)) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = palette.brandColor,
                                trackColor = palette.bgTertiary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Network Status Selector
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "В сети" to "🟢 В сети",
                                "Не беспокоить" to "🌙 Не беспокоить",
                                "Невидимка" to "👻 Невидимка"
                            ).forEach { (code, label) ->
                                val selected = currentUser.status == code
                                Surface(
                                    onClick = { onUpdateUser(currentUser.copy(status = code)) },
                                    color = if (selected) palette.brandColor else palette.bgTertiary,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else palette.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = palette.dividerColor, thickness = 1.dp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // 2. APPEARANCE THEME SWITCHER (Светлая / Тёмная / Системная / AMOLED)
        item(key = "profile_theme_card") {
            Surface(
                color = palette.bgSecondary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .border(1.dp, palette.borderColor, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "КАСТОМИЗАЦИЯ И ТЕМА ОФОРМЛЕНИЯ",
                        color = palette.textMuted,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val themeOptions = listOf(
                        Triple("light", "☀️ Светлая", "theme_btn_light"),
                        Triple("dark", "🌙 Тёмная", "theme_btn_dark"),
                        Triple("system", "📱 Системная", "theme_btn_system"),
                        Triple("amoled", "🖤 AMOLED", "theme_btn_amoled")
                    )
                    val normalizedActive = if (activeTheme == "default" || activeTheme == "midnight") "dark" else activeTheme
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        themeOptions.forEach { (themeKey, label, tag) ->
                            val selected = normalizedActive == themeKey
                            Surface(
                                onClick = { onChangeTheme(themeKey) },
                                color = if (selected) palette.brandColor else palette.bgTertiary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) palette.brandColor else palette.borderColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .testTag(tag)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else palette.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = palette.dividerColor, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔊 Звук при входе в приложение",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Короткая фирменная мелодия при открытии MARKENS",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = onPreviewStartupSound,
                                color = palette.bgTertiary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .border(1.dp, palette.borderColor, RoundedCornerShape(8.dp))
                                    .testTag("preview_startup_sound_button")
                            ) {
                                Text(
                                    text = "▶ Тест",
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                            Surface(
                                onClick = { onToggleStartupSound(!startupSoundEnabled) },
                                color = if (startupSoundEnabled) palette.greenColor else palette.bgTertiary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (startupSoundEnabled) palette.greenColor else palette.borderColor,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .testTag("toggle_startup_sound_button")
                            ) {
                                Text(
                                    text = if (startupSoundEnabled) "Вкл" else "Выкл",
                                    color = if (startupSoundEnabled) Color.White else palette.textMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. BIO & BADGE SECTION
        item(key = "profile_bio_card") {
            Surface(
                color = palette.bgSecondary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .border(1.dp, palette.borderColor, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "О СЕБЕ И ЗНАЧОК ПРОФИЛЯ",
                        color = palette.textMuted,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = {
                            bioInput = it
                            saveBanner = null
                        },
                        label = { Text("О себе / статус профиля") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_bio_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.brandColor,
                            unfocusedBorderColor = palette.borderColor,
                            focusedContainerColor = palette.bgTertiary,
                            unfocusedContainerColor = palette.bgTertiary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VORTEX_BADGES.forEach { (emoji, title) ->
                            val isSelected = currentUser.badge == emoji
                            Surface(
                                onClick = { onUpdateUser(currentUser.copy(badge = emoji)) },
                                color = if (isSelected) palette.brandColor else palette.bgTertiary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$emoji $title",
                                    color = if (isSelected) Color.White else palette.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onUpdateUser(currentUser.copy(bio = bioInput.trim()))
                            saveBanner = "✓ Профиль сохранён"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.brandColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_profile_button")
                    ) {
                        Text("Сохранить профиль", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    if (saveBanner != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = saveBanner ?: "",
                            color = palette.greenColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 4. ACCOUNT & PASSWORD
        item(key = "profile_password_card") {
            Surface(
                color = palette.bgSecondary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .border(1.dp, palette.borderColor, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = palette.brandColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ПАРОЛЬ АККАУНТА",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Данные входа сохранены на устройстве",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                showPasswordEditor = !showPasswordEditor
                                passwordFeedback = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("toggle_change_password_button")
                        ) {
                            Text("Изменить", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }

                    AnimatedVisibility(visible = showPasswordEditor) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.bgTertiary)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newPasswordInput,
                                    onValueChange = {
                                        newPasswordInput = it
                                        passwordFeedback = null
                                    },
                                    placeholder = { Text("Новый пароль (мин. 3 симв.)", fontSize = 12.sp) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("new_password_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = palette.textPrimary,
                                        unfocusedTextColor = palette.textPrimary,
                                        focusedBorderColor = palette.brandColor,
                                        unfocusedBorderColor = palette.borderColor,
                                        focusedContainerColor = palette.bgSecondary,
                                        unfocusedContainerColor = palette.bgSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val res = onChangePassword(newPasswordInput)
                                            res.onSuccess {
                                                passwordFeedbackSuccess = true
                                                passwordFeedback = "✓ Пароль обновлён"
                                                newPasswordInput = ""
                                                showPasswordEditor = false
                                            }.onFailure { err ->
                                                passwordFeedbackSuccess = false
                                                passwordFeedback = err.message
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.brandColor),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("ОК", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            if (passwordFeedback != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = passwordFeedback ?: "",
                                    color = if (passwordFeedbackSuccess) palette.greenColor else Color(0xFFEF4444),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4.5 PC <-> ANDROID v31.2.0 SYNC & CRYPTO DIAGNOSTICS CARD
        item(key = "profile_sync_diagnostics_card") {
            Surface(
                color = palette.bgSecondary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .border(1.dp, palette.brandColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .testTag("sync_diagnostics_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "СИНХРОНИЗАЦИЯ ПК ↔ ANDROID (v31.2.0)",
                            color = palette.brandColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.4.sp
                        )
                        Surface(
                            color = palette.greenColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = syncDiagnostics.activeVer,
                                color = palette.greenColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Ядро протокола: ${syncDiagnostics.originVer}",
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Статус облака: ${syncDiagnostics.firebaseVer} (проверка: ${syncDiagnostics.lastCheckTime})",
                        color = palette.textSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Авторизация: ${syncDiagnostics.downloadResult}",
                        color = palette.greenColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Защита каналов: ${syncDiagnostics.integrityResult}",
                        color = palette.textSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onForceSyncCheck,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("force_sync_check_button")
                    ) {
                        Text(
                            text = "🔄 Проверить синхронизацию с ПК и обновления",
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 5. LOGOUT BUTTON
        item(key = "profile_logout_btn") {
            OutlinedButton(
                onClick = onLogout,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .testTag("logout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Выйти из аккаунта",
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
