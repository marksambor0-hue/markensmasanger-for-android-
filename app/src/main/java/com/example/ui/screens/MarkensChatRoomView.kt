package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.data.markens.Base64ImageDecoder
import com.example.data.markens.MarkensMessage
import com.example.data.markens.MarkensPresence
import com.example.data.markens.MarkensReplyRef
import com.example.data.markens.MarkensUser
import com.example.data.markens.MarkensVoiceMessageEngine
import com.example.data.markens.MessageDeliveryStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ActiveChatSession(
    val channelKey: String,
    val title: String,
    val subtitle: String,
    val isDm: Boolean
)

private val QUICK_MOBILE_PHRASES = listOf(
    "🚗 Я скоро приеду!",
    "⏱ Буду через 10 минут",
    "💻 Скоро зайду с ПК",
    "📱 Я сейчас с телефона",
    "👍 Окей, договорились!",
    "🔥 Жду тебя в чате"
)

/**
 * Decodes Base64 `data:image/...` strings off the main UI thread (`Dispatchers.Default`)
 * to prevent frame drops during list scrolling.
 */
@Composable
fun rememberDecodedImageBitmap(dataUrl: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = dataUrl) {
        if (dataUrl.isNullOrBlank() || !dataUrl.startsWith("data:image")) {
            value = null
        } else {
            value = withContext(Dispatchers.Default) {
                Base64ImageDecoder.decodeDataUrl(dataUrl)
            }
        }
    }
    return bitmap
}

@Composable
fun MarkensAvatarBox(
    name: String,
    avatarUrl: String?,
    size: Dp,
    bgColor: Color,
    fontSize: TextUnit = 14.sp,
    fallbackText: String? = null,
    modifier: Modifier = Modifier
) {
    val bmp = rememberDecodedImageBitmap(avatarUrl)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = fallbackText ?: name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            )
        }
    }
}

private fun formatMessageDateHeader(ts: Long): String {
    if (ts <= 0L) return "Сегодня"
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = ts }
    val sameYear = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    val nowDay = nowCal.get(Calendar.DAY_OF_YEAR)
    val msgDay = msgCal.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && nowDay == msgDay -> "Сегодня"
        sameYear && nowDay - msgDay == 1 -> "Вчера"
        else -> SimpleDateFormat("d MMMM", Locale.forLanguageTag("ru")).format(Date(ts))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MarkensChatRoomView(
    session: ActiveChatSession,
    currentUser: MarkensUser,
    messages: List<MarkensMessage>,
    presenceMap: Map<String, MarkensPresence>,
    friends: List<String>,
    bgPrimary: Color,
    bgSecondary: Color,
    bgTertiary: Color,
    brandColor: Color,
    greenColor: Color,
    onBack: () -> Unit,
    onSendMessage: (text: String, replyTo: MarkensReplyRef?, mediaDataUrl: String?) -> Unit,
    onEditMessage: (MarkensMessage, String) -> Unit,
    onToggleReaction: (MarkensMessage, String) -> Unit,
    onTogglePin: (MarkensMessage) -> Unit,
    onDeleteMessage: (MarkensMessage) -> Unit,
    onRetryMessage: ((MarkensMessage) -> Unit)? = null,
    onStartVoiceCall: (() -> Unit)? = null,
    onStartVideoCall: (() -> Unit)? = null,
    showBackButton: Boolean = true
) {
    BackHandler(enabled = showBackButton, onBack = onBack)

    val isLight = remember(bgPrimary) { bgPrimary.luminance() > 0.5f }
    val textPrimary = if (isLight) Color(0xFF0F172A) else Color(0xFFF3F4F6)
    val textSecondary = if (isLight) Color(0xFF334155) else Color(0xFFDCDFE4)
    val textMuted = if (isLight) Color(0xFF64748B) else Color(0xFF8E95A2)
    val dividerColor = if (isLight) Color(0xFFE2E8F0) else Color(0xFF14161B)
    val borderColor = if (isLight) Color(0xFFCBD5E1) else Color(0xFF2B303B)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var inputText by remember { mutableStateOf("") }
    var attachedImageDataUrl by remember { mutableStateOf<String?>(null) }
    var activeReply by remember { mutableStateOf<MarkensReplyRef?>(null) }
    var editingMessage by remember { mutableStateOf<MarkensMessage?>(null) }
    var showEmojiBar by remember { mutableStateOf(false) }
    var showQuickPhrasesBar by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var inChatSearchQuery by remember { mutableStateOf("") }
    var showPinnedSheet by remember { mutableStateOf(false) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var selectedMessageForActions by remember { mutableStateOf<MarkensMessage?>(null) }
    var lightboxMessage by remember { mutableStateOf<MarkensMessage?>(null) }

    // Voice message recording state
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var voiceErrorText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(session.channelKey) {
        onDispose {
            if (MarkensVoiceMessageEngine.isCurrentlyRecording()) {
                MarkensVoiceMessageEngine.cancelRecording()
            }
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0
            while (isActive && isRecordingVoice) {
                delay(1000L)
                recordingSeconds += 1
            }
        }
    }

    val voicePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val res = MarkensVoiceMessageEngine.startRecording(context) { wavDataUrl, durSec ->
                isRecordingVoice = false
                if (!wavDataUrl.isNullOrBlank()) {
                    onSendMessage("🎤 Голосовое сообщение (${durSec} сек.)", activeReply, wavDataUrl)
                    activeReply = null
                }
            }
            if (res.isSuccess) {
                isRecordingVoice = true
                voiceErrorText = null
            } else {
                voiceErrorText = res.exceptionOrNull()?.message
            }
        } else {
            voiceErrorText = "Для записи голосового сообщения нужен доступ к микрофону"
        }
    }

    // Default to Telegram Bubbles for DMs and Discord Cozy Feed for Server #Channels, with 1-tap toggle!
    var useDiscordCozyStyle by remember(session.channelKey) { mutableStateOf(!session.isDm) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    Base64ImageDecoder.encodeUriToDataUrl(context, uri)
                }
                if (encoded != null) {
                    attachedImageDataUrl = encoded
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val pinnedMessages = remember(messages) { messages.filter { it.pinned } }

    val displayedMessages = remember(messages, inChatSearchQuery, showSearchBar) {
        if (!showSearchBar || inChatSearchQuery.isBlank()) {
            messages
        } else {
            val q = inChatSearchQuery.trim()
            messages.filter {
                it.text.contains(q, ignoreCase = true) || it.sender.contains(q, ignoreCase = true)
            }
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            displayedMessages.size > 6 && lastVisible < displayedMessages.size - 2
        }
    }

    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId, session.channelKey) {
        if (messages.isNotEmpty() && !showSearchBar) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    val dmPresence = if (session.isDm) presenceMap[session.title.lowercase()] else null
    val isFriendOnline = dmPresence?.isOnlineNow == true
    val isFriendDnd = dmPresence?.isDnd == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgPrimary)
            .imePadding()
            .testTag("chat_room_screen")
    ) {
        // 1. TOP APP BAR (Sleek Discord / Telegram Mobile header)
        Surface(
            color = bgSecondary,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = textPrimary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (session.isDm) {
                        val isFav = session.channelKey.startsWith("fav_")
                        val peerAvatar = if (isFav) null else dmPresence?.avatarUrl
                        val dotColor = when {
                            isFriendOnline && isFriendDnd -> Color(0xFFF59E0B)
                            isFriendOnline -> greenColor
                            else -> Color(0xFF6B7280)
                        }
                        Box(modifier = Modifier.size(36.dp)) {
                            MarkensAvatarBox(
                                name = session.title,
                                avatarUrl = peerAvatar,
                                size = 36.dp,
                                bgColor = if (session.title.equals("markens", ignoreCase = true) || isFav) brandColor else Color(0xFF475569),
                                fontSize = 15.sp,
                                fallbackText = if (isFav) "★" else null
                            )
                            if (!isFav) {
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .border(2.dp, bgSecondary, CircleShape)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bgTertiary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#",
                                color = textMuted,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showMembersSheet = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = session.title,
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (session.title.equals("markens", ignoreCase = true)) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.18f),
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
                            if (session.isDm && !session.channelKey.startsWith("fav_")) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = greenColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "🔒 E2EE v2",
                                        color = greenColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        val subText = if (session.isDm && !session.channelKey.startsWith("fav_")) {
                            dmPresence?.formatPresenceSubtitle() ?: "не в сети"
                        } else {
                            val onlineCount = presenceMap.values.count { it.isOnlineNow }
                            if (onlineCount > 0) "${session.subtitle} • 🟢 $onlineCount в сети" else session.subtitle
                        }
                        val subColor = when {
                            session.isDm && isFriendOnline && isFriendDnd -> Color(0xFFF59E0B)
                            session.isDm && isFriendOnline -> greenColor
                            else -> textMuted
                        }
                        Text(
                            text = subText,
                            color = subColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Voice & Video Call actions for direct messages
                    if (session.isDm && !session.channelKey.startsWith("fav_")) {
                        if (onStartVoiceCall != null) {
                            IconButton(
                                onClick = onStartVoiceCall,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("chat_voice_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Аудиозвонок",
                                    tint = greenColor,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        if (onStartVideoCall != null) {
                            IconButton(
                                onClick = onStartVideoCall,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("chat_video_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Видеозвонок",
                                    tint = brandColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 1-Tap Switcher between Telegram Bubbles & Discord Cozy Feed
                    IconButton(
                        onClick = { useDiscordCozyStyle = !useDiscordCozyStyle },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("chat_style_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (useDiscordCozyStyle) Icons.Default.Forum else Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Стиль сообщений (Telegram / Discord)",
                            tint = if (useDiscordCozyStyle) brandColor else textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) inChatSearchQuery = ""
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("chat_search_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск в чате",
                            tint = if (showSearchBar) brandColor else textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = { showPinnedSheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Закреплённые",
                            tint = if (pinnedMessages.isNotEmpty()) brandColor else textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = { showMembersSheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Участники",
                            tint = textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // In-Chat Search Bar
                AnimatedVisibility(visible = showSearchBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgTertiary)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = inChatSearchQuery,
                            onValueChange = { inChatSearchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Поиск сообщений...",
                                    color = textMuted,
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("in_chat_search_input"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            )
                        )
                        if (inChatSearchQuery.isNotEmpty()) {
                            Text(
                                text = "${displayedMessages.size}",
                                color = brandColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        IconButton(
                            onClick = {
                                inChatSearchQuery = ""
                                showSearchBar = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть поиск",
                                tint = textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Pinned message bar (Telegram-style compact strip)
        if (pinnedMessages.isNotEmpty()) {
            val lastPinned = pinnedMessages.last()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgSecondary.copy(alpha = 0.95f))
                    .clickable { showPinnedSheet = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(brandColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Закреплённое сообщение (${pinnedMessages.size})",
                        color = brandColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${lastPinned.sender}: ${lastPinned.text}",
                        color = textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = textMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
            HorizontalDivider(color = dividerColor, thickness = 1.dp)
        }

        // 2. MESSAGES LIST
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (displayedMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(bgSecondary)
                                .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (session.isDm) "@" else "#",
                                color = brandColor,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (showSearchBar && inChatSearchQuery.isNotBlank()) {
                                "Ничего не найдено"
                            } else if (session.isDm) {
                                "Начало переписки с ${session.title}"
                            } else {
                                "Добро пожаловать в #${session.title}"
                            },
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Свайпните сообщение влево для быстрого ответа",
                            color = textMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QUICK_MOBILE_PHRASES.take(4).forEach { phrase ->
                                Surface(
                                    onClick = { onSendMessage(phrase, null, null) },
                                    color = bgSecondary,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.border(
                                        width = 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                ) {
                                    Text(
                                        text = phrase,
                                        color = textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = displayedMessages,
                        key = { _, it -> it.id },
                        contentType = { _, _ -> "chat_msg" }
                    ) { index, msg ->
                        val prevMsg = if (index > 0) displayedMessages[index - 1] else null
                        val curDateLabel = remember(msg.ts) { formatMessageDateHeader(msg.ts) }
                        val prevDateLabel = remember(prevMsg?.ts) {
                            if (prevMsg != null) formatMessageDateHeader(prevMsg.ts) else null
                        }
                        val showDateSeparator = index == 0 || curDateLabel != prevDateLabel

                        val isGroupedWithPrev = !showDateSeparator &&
                            prevMsg != null &&
                            prevMsg.sender.equals(msg.sender, ignoreCase = true) &&
                            msg.replyTo == null &&
                            abs(msg.ts - prevMsg.ts) < 5 * 60 * 1000L

                        if (showDateSeparator) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = bgSecondary.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.border(
                                        1.dp,
                                        borderColor,
                                        RoundedCornerShape(12.dp)
                                    )
                                ) {
                                    Text(
                                        text = curDateLabel,
                                        color = textMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        val isMe = msg.sender.equals(currentUser.name, ignoreCase = true)
                        val resolvedAvatar = if (isMe) {
                            currentUser.avatarUrl ?: msg.senderAvatarUrl
                        } else {
                            msg.senderAvatarUrl ?: presenceMap[msg.sender.lowercase()]?.avatarUrl
                        }

                        SwipeableMessageItem(
                            onSwipeReply = {
                                editingMessage = null
                                activeReply = MarkensReplyRef(
                                    id = msg.id,
                                    sender = msg.sender,
                                    text = msg.text.ifBlank {
                                        if (msg.mediaType == "voice") "🎤 Голосовое сообщение" else "📷 Фотография"
                                    }
                                )
                            }
                        ) {
                            if (useDiscordCozyStyle) {
                                DiscordCozyMessageRow(
                                    msg = msg,
                                    senderAvatarUrl = resolvedAvatar,
                                    isMe = isMe,
                                    isGroupedWithPrev = isGroupedWithPrev,
                                    currentUser = currentUser,
                                    bgSecondary = bgSecondary,
                                    bgTertiary = bgTertiary,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    textMuted = textMuted,
                                    borderColor = borderColor,
                                    brandColor = brandColor,
                                    greenColor = greenColor,
                                    onOpenActions = { selectedMessageForActions = msg },
                                    onQuickReply = {
                                        editingMessage = null
                                        activeReply = MarkensReplyRef(
                                            id = msg.id,
                                            sender = msg.sender,
                                            text = msg.text.ifBlank {
                                                if (msg.mediaType == "voice") "🎤 Голосовое сообщение" else "📷 Фотография"
                                            }
                                        )
                                    },
                                    onOpenImageLightbox = { lightboxMessage = msg },
                                    onToggleReaction = { emoji -> onToggleReaction(msg, emoji) },
                                    onRetryMessage = { onRetryMessage?.invoke(msg) }
                                )
                            } else {
                                MessageBubbleRow(
                                    msg = msg,
                                    senderAvatarUrl = resolvedAvatar,
                                    isMe = isMe,
                                    isGroupedWithPrev = isGroupedWithPrev,
                                    currentUser = currentUser,
                                    bgSecondary = bgSecondary,
                                    bgTertiary = bgTertiary,
                                    textPrimary = textPrimary,
                                    textMuted = textMuted,
                                    borderColor = borderColor,
                                    brandColor = brandColor,
                                    greenColor = greenColor,
                                    onOpenActions = { selectedMessageForActions = msg },
                                    onOpenImageLightbox = { lightboxMessage = msg },
                                    onToggleReaction = { emoji -> onToggleReaction(msg, emoji) },
                                    onRetryMessage = { onRetryMessage?.invoke(msg) }
                                )
                            }
                        }
                    }
                }
            }

            // Floating "Scroll to bottom" button (Telegram & Discord style)
            if (showScrollToBottom) {
                Surface(
                    onClick = {
                        scope.launch {
                            if (displayedMessages.isNotEmpty()) {
                                listState.animateScrollToItem(displayedMessages.size - 1)
                            }
                        }
                    },
                    shape = CircleShape,
                    color = bgSecondary,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .size(40.dp)
                        .border(1.dp, brandColor.copy(alpha = 0.6f), CircleShape)
                        .testTag("scroll_to_bottom_fab")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Вниз",
                            tint = textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // 3. BOTTOM COMPOSER BAR (Discord/Telegram Mobile style + Photo Picker + Edit + Quick Phrases)
        Surface(
            color = bgSecondary,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // Edit message banner
                val editTarget = editingMessage
                if (editTarget != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgTertiary)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = greenColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Редактирование",
                                color = greenColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = editTarget.text,
                                color = textMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                editingMessage = null
                                inputText = ""
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Отменить редактирование",
                                tint = textMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Reply banner
                val reply = activeReply
                if (reply != null && editingMessage == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgTertiary)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = brandColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ответ @${reply.sender}",
                                color = brandColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = reply.text,
                                color = textMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { activeReply = null },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Отменить ответ",
                                tint = textMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Attached Image Preview before sending
                val pendingImage = attachedImageDataUrl
                if (pendingImage != null) {
                    val bitmap = rememberDecodedImageBitmap(pendingImage)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgTertiary)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Вложение",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, brandColor, RoundedCornerShape(8.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Фотография прикреплена",
                                color = textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Готово к отправке в чат",
                                color = textMuted,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(
                            onClick = { attachedImageDataUrl = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Убрать фото",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Voice Recording Banner / Permission Error Banner
                if (voiceErrorText != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = voiceErrorText.orEmpty(),
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { voiceErrorText = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (isRecordingVoice) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEF4444).copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Запись голосового... 0:%02d".format(recordingSeconds),
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = {
                                MarkensVoiceMessageEngine.cancelRecording()
                                isRecordingVoice = false
                            },
                            color = bgSecondary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Отмена",
                                color = textMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                MarkensVoiceMessageEngine.stopRecordingAndFinalize()
                                isRecordingVoice = false
                            },
                            color = greenColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("voice_record_finish_send_button")
                        ) {
                            Text(
                                text = "Отправить",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Quick Mobile Phrases Bar
                AnimatedVisibility(visible = showQuickPhrasesBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgTertiary)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QUICK_MOBILE_PHRASES.forEach { phrase ->
                            Surface(
                                onClick = {
                                    onSendMessage(phrase, activeReply, null)
                                    activeReply = null
                                    showQuickPhrasesBar = false
                                },
                                color = bgSecondary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.border(
                                    width = 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            ) {
                                Text(
                                    text = phrase,
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Quick Emoji Bar
                AnimatedVisibility(visible = showEmojiBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgTertiary)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("🔥", "❤️", "😂", "👍", "👑", "🚗", "🚀", "💎").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 20.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { inputText += emoji }
                                    .padding(6.dp)
                            )
                        }
                    }
                }

                // Main Composer Input Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("chat_attach_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Прикрепить фото",
                            tint = if (attachedImageDataUrl != null) greenColor else textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Voice Message Record / Stop button
                    IconButton(
                        onClick = {
                            if (isRecordingVoice) {
                                MarkensVoiceMessageEngine.stopRecordingAndFinalize()
                                isRecordingVoice = false
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    val res = MarkensVoiceMessageEngine.startRecording(context) { wavDataUrl, durSec ->
                                        isRecordingVoice = false
                                        if (!wavDataUrl.isNullOrBlank()) {
                                            onSendMessage(
                                                "🎤 Голосовое сообщение (${durSec} сек.)",
                                                activeReply,
                                                wavDataUrl
                                            )
                                            activeReply = null
                                        }
                                    }
                                    if (res.isSuccess) {
                                        isRecordingVoice = true
                                        voiceErrorText = null
                                    } else {
                                        voiceErrorText = res.exceptionOrNull()?.message
                                    }
                                } else {
                                    voicePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("chat_voice_record_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecordingVoice) "Остановить запись" else "Голосовое сообщение",
                            tint = if (isRecordingVoice) Color(0xFFEF4444) else textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            showEmojiBar = !showEmojiBar
                            if (showEmojiBar) showQuickPhrasesBar = false
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = "Эмодзи",
                            tint = if (showEmojiBar) brandColor else textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            showQuickPhrasesBar = !showQuickPhrasesBar
                            if (showQuickPhrasesBar) showEmojiBar = false
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Быстрые фразы",
                            tint = if (showQuickPhrasesBar) greenColor else textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    val canSend = inputText.isNotBlank() || attachedImageDataUrl != null
                    val triggerSend = {
                        val trimmed = inputText.trim()
                        val media = attachedImageDataUrl
                        if (trimmed.isNotEmpty() || media != null) {
                            val editMsg = editingMessage
                            if (editMsg != null && trimmed.isNotEmpty()) {
                                onEditMessage(editMsg, trimmed)
                                editingMessage = null
                            } else {
                                onSendMessage(trimmed.ifEmpty { "📷 Фотография" }, activeReply, media)
                                activeReply = null
                                attachedImageDataUrl = null
                            }
                            inputText = ""
                            showEmojiBar = false
                            showQuickPhrasesBar = false
                        }
                    }

                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = if (editingMessage != null) {
                                    "Редактировать сообщение..."
                                } else if (session.isDm) {
                                    "Написать @${session.title}..."
                                } else {
                                    "Сообщение в #${session.title}"
                                },
                                color = textMuted,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 110.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .testTag("chat_message_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = bgTertiary,
                            unfocusedContainerColor = bgTertiary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = brandColor
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { triggerSend() })
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        onClick = triggerSend,
                        shape = CircleShape,
                        color = if (canSend) {
                            if (editingMessage != null) greenColor else brandColor
                        } else bgTertiary,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("chat_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (editingMessage != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить",
                                tint = if (canSend) Color.White else textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Full-Screen Image Lightbox Dialog
    val viewedMsg = lightboxMessage
    if (viewedMsg != null && viewedMsg.mediaUrl != null) {
        val fullBitmap = rememberDecodedImageBitmap(viewedMsg.mediaUrl)
        if (fullBitmap != null) {
            Dialog(
                onDismissRequest = { lightboxMessage = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.94f))
                        .clickable { lightboxMessage = null }
                ) {
                    Image(
                        bitmap = fullBitmap,
                        contentDescription = "Просмотр фотографии",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = viewedMsg.sender,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = viewedMsg.time,
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { lightboxMessage = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Message Actions Bottom Sheet (with Read Receipts / Who Read section)
    val targetMsg = selectedMessageForActions
    if (targetMsg != null) {
        val readers = remember(targetMsg.readBy, targetMsg.sender) {
            targetMsg.readersExcludingSender()
        }
        ModalBottomSheet(
            onDismissRequest = { selectedMessageForActions = null },
            containerColor = bgSecondary,
            contentColor = textPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Who read this message card
                Surface(
                    color = bgTertiary,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (readers.isNotEmpty()) Icons.Default.DoneAll else Icons.Default.Check,
                                contentDescription = null,
                                tint = if (readers.isNotEmpty()) greenColor else textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (readers.isNotEmpty()) {
                                    "Прочитано (${readers.size})"
                                } else {
                                    "Отправлено • ещё не прочитано"
                                },
                                color = if (readers.isNotEmpty()) greenColor else textMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        if (readers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Кто прочитал: " + readers.joinToString(", ") { "@$it" },
                                color = textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgTertiary)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("❤️", "🔥", "👍", "😂", "🎉", "👑").forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onToggleReaction(targetMsg, emoji)
                                    selectedMessageForActions = null
                                }
                                .padding(6.dp)
                        )
                    }
                }

                Surface(
                    onClick = {
                        editingMessage = null
                        activeReply = MarkensReplyRef(
                            id = targetMsg.id,
                            sender = targetMsg.sender,
                            text = targetMsg.text
                        )
                        selectedMessageForActions = null
                    },
                    color = bgTertiary,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = textPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Ответить", color = textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }

                if (targetMsg.sender.equals(currentUser.name, ignoreCase = true)) {
                    Surface(
                        onClick = {
                            activeReply = null
                            editingMessage = targetMsg
                            inputText = targetMsg.text
                            selectedMessageForActions = null
                        },
                        color = bgTertiary,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = greenColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Изменить сообщение", color = textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }

                Surface(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(targetMsg.text))
                        selectedMessageForActions = null
                    },
                    color = bgTertiary,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Скопировать текст", color = textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }

                Surface(
                    onClick = {
                        onTogglePin(targetMsg)
                        selectedMessageForActions = null
                    },
                    color = bgTertiary,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, tint = brandColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (targetMsg.pinned) "Открепить сообщение" else "Закрепить сообщение",
                            color = textPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                if (targetMsg.sender.equals(currentUser.name, ignoreCase = true) || currentUser.isOwner) {
                    Surface(
                        onClick = {
                            onDeleteMessage(targetMsg)
                            selectedMessageForActions = null
                        },
                        color = Color(0xFFEF4444).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Удалить сообщение",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Pinned messages sheet
    if (showPinnedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPinnedSheet = false },
            containerColor = bgSecondary,
            contentColor = textPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "📌 Закреплённые сообщения (${pinnedMessages.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (pinnedMessages.isEmpty()) {
                    Text(
                        text = "В этом чате пока нет закреплённых сообщений.",
                        color = textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    pinnedMessages.forEach { p ->
                        Surface(
                            color = bgTertiary,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = p.sender,
                                        color = brandColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = p.time,
                                        color = textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = p.text,
                                    color = textPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Online Members Sheet
    if (showMembersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMembersSheet = false },
            containerColor = bgSecondary,
            contentColor = textPrimary
        ) {
            val allMembers = remember(presenceMap, friends, currentUser) {
                val mapByLower = LinkedHashMap<String, String>()
                mapByLower[currentUser.name.lowercase()] = currentUser.name
                friends.forEach { f ->
                    mapByLower.putIfAbsent(f.lowercase(), f)
                }
                presenceMap.values.forEach { p ->
                    mapByLower.putIfAbsent(p.user.lowercase(), p.user)
                }
                mapByLower.values.sortedWith(
                    compareByDescending<String> { name ->
                        val isMe = name.equals(currentUser.name, ignoreCase = true)
                        if (isMe) !currentUser.status.contains("Невидимка", ignoreCase = true)
                        else presenceMap[name.lowercase()]?.isOnlineNow == true
                    }.thenBy { it.lowercase() }
                )
            }
            val onlineCount = allMembers.count { name ->
                val isMe = name.equals(currentUser.name, ignoreCase = true)
                if (isMe) !currentUser.status.contains("Невидимка", ignoreCase = true)
                else presenceMap[name.lowercase()]?.isOnlineNow == true
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Участники (${allMembers.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = textPrimary
                    )
                    Surface(
                        color = greenColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "🟢 В сети: $onlineCount",
                            color = greenColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                allMembers.forEach { memberName ->
                    val pres = presenceMap[memberName.lowercase()]
                    val isMe = memberName.equals(currentUser.name, ignoreCase = true)
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
                        isOnline -> greenColor
                        else -> Color(0xFF6B7280)
                    }
                    val isCreator = memberName.equals("markens", ignoreCase = true)
                    val avUrl = if (isMe) currentUser.avatarUrl else pres?.avatarUrl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(34.dp)) {
                            MarkensAvatarBox(
                                name = memberName,
                                avatarUrl = avUrl,
                                size = 34.dp,
                                bgColor = if (isCreator) brandColor else Color(0xFF475569),
                                fontSize = 14.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .border(1.5.dp, bgSecondary, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isMe) "$memberName (Вы)" else memberName,
                                    color = textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                if (isCreator) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "👑 Создатель",
                                        color = Color(0xFFD97706),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            val statusLine = when {
                                isMe && !isOnline -> "👻 Невидимка • скрыт для других"
                                isMe && isDnd -> "🌙 Не беспокоить • 📱 Android"
                                isMe -> "🟢 В сети • 📱 Android"
                                pres != null -> pres.formatPresenceSubtitle()
                                else -> "не в сети"
                            }
                            Text(
                                text = statusLine,
                                color = when {
                                    isOnline && isDnd -> Color(0xFFF59E0B)
                                    isOnline -> greenColor
                                    else -> textMuted
                                },
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SwipeableMessageItem(
    onSwipeReply: () -> Unit,
    content: @Composable () -> Unit
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffsetX <= -90f) {
                            onSwipeReply()
                        }
                        dragOffsetX = 0f
                    },
                    onDragCancel = { dragOffsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        val next = (dragOffsetX + dragAmount).coerceIn(-140f, 0f)
                        dragOffsetX = next
                    }
                )
            }
    ) {
        if (dragOffsetX < -24f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4F46E5).copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Ответить",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
        ) {
            content()
        }
    }
}

@Composable
private fun VoiceMessagePlayerCard(
    mediaUrl: String,
    captionText: String,
    isMe: Boolean,
    brandColor: Color,
    greenColor: Color,
    bgSurface: Color,
    textColor: Color
) {
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            if (!isPlaying) {
                isPlaying = true
                scope.launch {
                    MarkensVoiceMessageEngine.playWavDataUrl(
                        dataUrl = mediaUrl,
                        onFinished = { isPlaying = false }
                    )
                }
            }
        },
        color = if (isMe) Color.Black.copy(alpha = 0.22f) else bgSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(vertical = 4.dp)
            .widthIn(min = 190.dp, max = 260.dp)
            .testTag("voice_message_player_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) greenColor else brandColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Воспроизводится" else "Воспроизвести голосовое",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPlaying) "🔊 Воспроизведение..." else captionText.ifBlank { "🎤 Голосовое сообщение" },
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                // Stylistic waveform bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val heights = listOf(6.dp, 12.dp, 9.dp, 15.dp, 11.dp, 16.dp, 8.dp, 13.dp, 10.dp, 14.dp, 7.dp, 11.dp)
                    heights.forEachIndexed { idx, h ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(h)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isPlaying && idx % 2 == 0) greenColor
                                    else textColor.copy(alpha = 0.55f)
                                )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscordCozyMessageRow(
    msg: MarkensMessage,
    senderAvatarUrl: String?,
    isMe: Boolean,
    isGroupedWithPrev: Boolean,
    currentUser: MarkensUser,
    bgSecondary: Color,
    bgTertiary: Color,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    borderColor: Color,
    brandColor: Color,
    greenColor: Color,
    onOpenActions: () -> Unit,
    onQuickReply: () -> Unit,
    onOpenImageLightbox: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onRetryMessage: () -> Unit
) {
    val isVoiceMsg = msg.mediaType == "voice" || msg.mediaUrl?.startsWith("data:audio/") == true
    val mediaBitmap = rememberDecodedImageBitmap(if (isVoiceMsg) null else msg.mediaUrl)
    val isCreatorSender = msg.sender.equals("markens", ignoreCase = true)
    val otherReaders = remember(msg.readBy, msg.sender) { msg.readersExcludingSender() }
    val deliveryStatus = msg.effectiveDeliveryStatus()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpenActions() }
            .padding(
                start = 4.dp,
                end = 4.dp,
                top = if (isGroupedWithPrev) 2.dp else 8.dp,
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        if (!isGroupedWithPrev) {
            MarkensAvatarBox(
                name = msg.sender,
                avatarUrl = senderAvatarUrl,
                size = 36.dp,
                bgColor = when {
                    isCreatorSender -> brandColor
                    isMe -> Color(0xFF3730A3)
                    else -> Color(0xFF475569)
                },
                fontSize = 14.sp
            )
        } else {
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = msg.time,
                    color = textMuted,
                    fontSize = 9.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (!isGroupedWithPrev) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = msg.sender,
                        color = when {
                            isCreatorSender -> Color(0xFFD97706)
                            isMe -> brandColor
                            else -> textPrimary
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (!msg.senderBadge.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = msg.senderBadge, fontSize = 12.sp)
                    }
                    if (!msg.senderRank.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            color = if (isCreatorSender) Color(0xFFF59E0B).copy(alpha = 0.16f) else bgSecondary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = msg.senderRank,
                                color = if (isCreatorSender) Color(0xFFD97706) else textMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = msg.time,
                        color = textMuted,
                        fontSize = 10.sp
                    )
                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (deliveryStatus) {
                            MessageDeliveryStatus.SENDING -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Отправляется",
                                    tint = textMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageDeliveryStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Ошибка отправки",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageDeliveryStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Прочитано",
                                    tint = greenColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                if (otherReaders.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${otherReaders.size}",
                                        color = greenColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            MessageDeliveryStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Доставлено на сервер",
                                    tint = textMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                    if (msg.pinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "📌", fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Быстрый ответ",
                        tint = textMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onQuickReply() }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Reply reference spine (Discord style)
            val reply = msg.replyTo
            if (reply != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgSecondary)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height(18.dp)
                            .background(brandColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "@${reply.sender}: ",
                        color = brandColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = reply.text,
                        color = textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Voice message player card
            if (isVoiceMsg && !msg.mediaUrl.isNullOrBlank()) {
                VoiceMessagePlayerCard(
                    mediaUrl = msg.mediaUrl,
                    captionText = msg.text,
                    isMe = isMe,
                    brandColor = brandColor,
                    greenColor = greenColor,
                    bgSurface = bgSecondary,
                    textColor = textPrimary
                )
            }

            // Optional attached photo
            if (mediaBitmap != null) {
                Image(
                    bitmap = mediaBitmap,
                    contentDescription = "Изображение",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .widthIn(max = 260.dp)
                        .heightIn(min = 110.dp, max = 210.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable { onOpenImageLightbox() }
                )
            }

            if (msg.text.isNotBlank() && !isVoiceMsg && !(mediaBitmap != null && msg.text == "📷 Фотография")) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = msg.text,
                        color = textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (msg.edited) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(изм.)",
                            color = textMuted,
                            fontSize = 10.sp
                        )
                    }
                    if (isGroupedWithPrev && isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (deliveryStatus) {
                            MessageDeliveryStatus.SENDING -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Отправляется",
                                    tint = textMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageDeliveryStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Ошибка отправки",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageDeliveryStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Прочитано",
                                    tint = greenColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageDeliveryStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Доставлено",
                                    tint = textMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Explicit FAILED banner with 1-tap retry (no duplicates)
            if (isMe && deliveryStatus == MessageDeliveryStatus.FAILED) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    onClick = onRetryMessage,
                    color = Color(0xFFEF4444).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("retry_message_${msg.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Не доставлено • Нажмите, чтобы повторить",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (msg.reactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    msg.reactions.forEach { (emoji, users) ->
                        val reactedByMe = users.any { it.equals(currentUser.name, ignoreCase = true) }
                        Surface(
                            onClick = { onToggleReaction(emoji) },
                            color = if (reactedByMe) brandColor.copy(alpha = 0.22f) else bgTertiary,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(
                                width = 1.dp,
                                color = if (reactedByMe) brandColor else borderColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                        ) {
                            Text(
                                text = "$emoji ${users.size}",
                                color = textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubbleRow(
    msg: MarkensMessage,
    senderAvatarUrl: String?,
    isMe: Boolean,
    isGroupedWithPrev: Boolean,
    currentUser: MarkensUser,
    bgSecondary: Color,
    bgTertiary: Color,
    textPrimary: Color,
    textMuted: Color,
    borderColor: Color,
    brandColor: Color,
    greenColor: Color,
    onOpenActions: () -> Unit,
    onOpenImageLightbox: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onRetryMessage: () -> Unit
) {
    val isVoiceMsg = msg.mediaType == "voice" || msg.mediaUrl?.startsWith("data:audio/") == true
    val mediaBitmap = rememberDecodedImageBitmap(if (isVoiceMsg) null else msg.mediaUrl)
    val isCreatorSender = msg.sender.equals("markens", ignoreCase = true)
    val bubbleTextPrimary = if (isMe) Color.White else textPrimary
    val bubbleTimeColor = if (isMe) Color.White.copy(alpha = 0.78f) else textMuted
    val deliveryStatus = msg.effectiveDeliveryStatus()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isGroupedWithPrev) 1.dp else 6.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
        ) {
            if (!isMe) {
                if (!isGroupedWithPrev) {
                    MarkensAvatarBox(
                        name = msg.sender,
                        avatarUrl = senderAvatarUrl,
                        size = 32.dp,
                        bgColor = if (isCreatorSender) brandColor else Color(0xFF475569),
                        fontSize = 13.sp
                    )
                } else {
                    Spacer(modifier = Modifier.width(32.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                onClick = onOpenActions,
                color = if (isMe) brandColor else bgSecondary,
                shape = RoundedCornerShape(
                    topStart = if (!isMe && isGroupedWithPrev) 6.dp else 15.dp,
                    topEnd = if (isMe && isGroupedWithPrev) 6.dp else 15.dp,
                    bottomStart = if (isMe) 15.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 15.dp
                ),
                modifier = Modifier
                    .widthIn(min = 76.dp, max = 304.dp)
                    .then(
                        if (!isMe) {
                            Modifier.border(
                                1.dp,
                                borderColor,
                                RoundedCornerShape(
                                    topStart = if (isGroupedWithPrev) 6.dp else 15.dp,
                                    topEnd = 15.dp,
                                    bottomStart = 4.dp,
                                    bottomEnd = 15.dp
                                )
                            )
                        } else Modifier
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                    if (!isMe && !isGroupedWithPrev) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = msg.sender,
                                color = if (isCreatorSender) Color(0xFFD97706) else brandColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            if (!msg.senderBadge.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = msg.senderBadge, fontSize = 11.sp)
                            }
                            if (!msg.senderRank.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = msg.senderRank,
                                    color = textMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    // Reply reference quote
                    val reply = msg.replyTo
                    if (reply != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isMe) Color.Black.copy(alpha = 0.2f) else bgTertiary
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.5.dp)
                                    .height(22.dp)
                                    .background(if (isMe) Color.White else brandColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = reply.sender,
                                    color = if (isMe) Color.White else brandColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = reply.text,
                                    color = if (isMe) Color.White.copy(alpha = 0.85f) else textMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Voice message player card
                    if (isVoiceMsg && !msg.mediaUrl.isNullOrBlank()) {
                        VoiceMessagePlayerCard(
                            mediaUrl = msg.mediaUrl,
                            captionText = msg.text,
                            isMe = isMe,
                            brandColor = brandColor,
                            greenColor = greenColor,
                            bgSurface = bgTertiary,
                            textColor = bubbleTextPrimary
                        )
                    }

                    // Optional image attachment
                    if (mediaBitmap != null) {
                        Image(
                            bitmap = mediaBitmap,
                            contentDescription = "Изображение",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp, max = 220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenImageLightbox() }
                        )
                        if (msg.text.isNotBlank() && msg.text != "📷 Фотография") {
                            Spacer(modifier = Modifier.height(5.dp))
                        }
                    }

                    // Message Text
                    if (msg.text.isNotBlank() && !isVoiceMsg && !(mediaBitmap != null && msg.text == "📷 Фотография")) {
                        Text(
                            text = msg.text,
                            color = bubbleTextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                    }

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (msg.pinned) {
                            Text(
                                text = "📌 ",
                                fontSize = 10.sp
                            )
                        }
                        if (msg.edited) {
                            Text(
                                text = "изм. ",
                                color = bubbleTimeColor,
                                fontSize = 9.sp
                            )
                        }
                        Text(
                            text = msg.time,
                            color = bubbleTimeColor,
                            fontSize = 10.sp
                        )
                        if (isMe) {
                            Spacer(modifier = Modifier.width(3.dp))
                            when (deliveryStatus) {
                                MessageDeliveryStatus.SENDING -> {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Отправляется",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                MessageDeliveryStatus.FAILED -> {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Ошибка отправки",
                                        tint = Color(0xFFFCA5A5),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                MessageDeliveryStatus.READ -> {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Прочитано",
                                        tint = Color(0xFF6EE7B7),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                MessageDeliveryStatus.SENT -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Отправлено",
                                        tint = Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isMe && deliveryStatus == MessageDeliveryStatus.FAILED) {
            Spacer(modifier = Modifier.height(3.dp))
            Surface(
                onClick = onRetryMessage,
                color = Color(0xFFEF4444).copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("retry_message_${msg.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ошибка сети • Повторить отправку",
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Reactions bar under bubble
        if (msg.reactions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            FlowRow(
                modifier = Modifier.padding(start = if (!isMe) 40.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                msg.reactions.forEach { (emoji, users) ->
                    val reactedByMe = users.any { it.equals(currentUser.name, ignoreCase = true) }
                    Surface(
                        onClick = { onToggleReaction(emoji) },
                        color = if (reactedByMe) brandColor.copy(alpha = 0.25f) else bgSecondary,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = if (reactedByMe) brandColor else borderColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                    ) {
                        Text(
                            text = "$emoji ${users.size}",
                            color = textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
