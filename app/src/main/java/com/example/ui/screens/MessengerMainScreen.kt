package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.auth.AuthSessionManager
import com.example.data.auth.RegisteredUser
import com.example.data.model.AccountSession
import com.example.data.model.BridgeProfile
import com.example.data.model.Channel
import com.example.data.model.Message
import com.example.data.repository.MessengerRepository
import com.example.data.repository.UnifiedSyncEngine
import com.example.ui.auth.signOut
import com.example.ui.viewmodel.MessengerViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

enum class MainTab {
    CHATS,
    CONTACTS,
    PROFILE
}

@Composable
fun MessengerMainScreen(
    session: AccountSession,
    authSessionManager: AuthSessionManager,
    onSignedOut: () -> Unit = {}
) {
    val viewModel: MessengerViewModel = viewModel(
        key = "${session.userId}_${session.syncRoomCode}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val syncEngine = UnifiedSyncEngine(
                    context = app,
                    firestoreRepository = MessengerRepository(db)
                )
                MessengerViewModel(
                    syncEngine = syncEngine,
                    session = session
                )
            }
        }
    )

    val channelsState by viewModel.channelsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val bridgeProfileState by viewModel.bridgeProfileState.collectAsStateWithLifecycle()
    val selectedChannelId by viewModel.selectedChannelId.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val registeredUsers by authSessionManager.registeredUsersFlow.collectAsStateWithLifecycle()

    var activeTab by rememberSaveable { mutableStateOf(MainTab.CHATS) }
    var isMobileChatOpen by rememberSaveable { mutableStateOf(false) }
    var showCreateChatDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val allChannels = (channelsState as? UiState.Success)?.data ?: emptyList()
    val activeChannel = allChannels.find { it.id == selectedChannelId } ?: allChannels.firstOrNull()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 700.dp

        if (!isExpandedScreen && isMobileChatOpen && activeTab == MainTab.CHATS) {
            BackHandler {
                isMobileChatOpen = false
            }
        } else if (activeTab != MainTab.CHATS) {
            BackHandler {
                activeTab = MainTab.CHATS
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isExpandedScreen && !isMobileChatOpen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NavigationBarItem(
                            selected = activeTab == MainTab.CHATS,
                            onClick = { activeTab = MainTab.CHATS },
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Чаты") },
                            label = { Text("Чаты") },
                            modifier = Modifier.testTag("nav_tab_channels")
                        )
                        NavigationBarItem(
                            selected = activeTab == MainTab.CONTACTS,
                            onClick = { activeTab = MainTab.CONTACTS },
                            icon = { Icon(Icons.Default.Group, contentDescription = "Люди") },
                            label = { Text("Люди") },
                            modifier = Modifier.testTag("nav_tab_contacts")
                        )
                        NavigationBarItem(
                            selected = activeTab == MainTab.PROFILE,
                            onClick = { activeTab = MainTab.PROFILE },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Профиль") },
                            label = { Text("Профиль") },
                            modifier = Modifier.testTag("nav_tab_profile")
                        )
                    }
                }
            },
            floatingActionButton = {
                if (activeTab == MainTab.CHATS && (!isMobileChatOpen || isExpandedScreen)) {
                    FloatingActionButton(
                        onClick = { showCreateChatDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("create_channel_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Новый чат")
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(visible = statusBanner != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = statusBanner ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissBanner() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (isExpandedScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            NavigationRailItem(
                                selected = activeTab == MainTab.CHATS,
                                onClick = { activeTab = MainTab.CHATS },
                                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Чаты") },
                                label = { Text("Чаты") }
                            )
                            NavigationRailItem(
                                selected = activeTab == MainTab.CONTACTS,
                                onClick = { activeTab = MainTab.CONTACTS },
                                icon = { Icon(Icons.Default.Group, contentDescription = "Люди") },
                                label = { Text("Люди") }
                            )
                            NavigationRailItem(
                                selected = activeTab == MainTab.PROFILE,
                                onClick = { activeTab = MainTab.PROFILE },
                                icon = { Icon(Icons.Default.Person, contentDescription = "Профиль") },
                                label = { Text("Профиль") }
                            )
                        }

                        when (activeTab) {
                            MainTab.CHATS -> {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .width(330.dp)
                                            .fillMaxHeight()
                                            .border(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        ChatListPane(
                                            channelsState = channelsState,
                                            selectedChannelId = activeChannel?.id,
                                            selectedCategory = selectedCategory,
                                            searchQuery = searchQuery,
                                            onSelectCategory = viewModel::setCategoryFilter,
                                            onSearchChange = viewModel::setSearchQuery,
                                            onChannelClick = { ch -> viewModel.selectChannel(ch.id) },
                                            onNewChatClick = { showCreateChatDialog = true },
                                            onTogglePin = viewModel::toggleChannelPin,
                                            onDeleteChannel = viewModel::deleteChannel
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        if (activeChannel != null) {
                                            ConversationPane(
                                                session = session,
                                                channel = activeChannel,
                                                messagesState = messagesState,
                                                showBackButton = false,
                                                onBack = {},
                                                onSendMessage = { text ->
                                                    viewModel.sendMessage(activeChannel.id, text)
                                                },
                                                onDeleteMessage = { msgId ->
                                                    viewModel.deleteMessage(activeChannel.id, msgId)
                                                }
                                            )
                                        } else {
                                            EmptyConversationPlaceholder(onNewChatClick = { showCreateChatDialog = true })
                                        }
                                    }
                                }
                            }
                            MainTab.CONTACTS -> {
                                ContactsPane(
                                    currentLogin = session.login,
                                    registeredUsers = registeredUsers,
                                    onStartDirectChat = { username, displayName ->
                                        viewModel.openOrCreateDirectMessage(username, displayName) {
                                            activeTab = MainTab.CHATS
                                        }
                                    }
                                )
                            }
                            MainTab.PROFILE -> {
                                ProfileSettingsPane(
                                    session = session,
                                    profileState = bridgeProfileState,
                                    onSaveProfile = { name, status, presence, roomCode, serverUrl ->
                                        authSessionManager.updateSyncConfig(roomCode, serverUrl, name)
                                        viewModel.updateProfile(name, status, presence)
                                    },
                                    onSignOut = {
                                        signOut(context, credentialManager, authSessionManager, onSignedOut, scope)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    when (activeTab) {
                        MainTab.CHATS -> {
                            if (isMobileChatOpen && activeChannel != null) {
                                ConversationPane(
                                    session = session,
                                    channel = activeChannel,
                                    messagesState = messagesState,
                                    showBackButton = true,
                                    onBack = { isMobileChatOpen = false },
                                    onSendMessage = { text ->
                                        viewModel.sendMessage(activeChannel.id, text)
                                    },
                                    onDeleteMessage = { msgId ->
                                        viewModel.deleteMessage(activeChannel.id, msgId)
                                    }
                                )
                            } else {
                                ChatListPane(
                                    channelsState = channelsState,
                                    selectedChannelId = activeChannel?.id,
                                    selectedCategory = selectedCategory,
                                    searchQuery = searchQuery,
                                    onSelectCategory = viewModel::setCategoryFilter,
                                    onSearchChange = viewModel::setSearchQuery,
                                    onChannelClick = { ch ->
                                        viewModel.selectChannel(ch.id)
                                        isMobileChatOpen = true
                                    },
                                    onNewChatClick = { showCreateChatDialog = true },
                                    onTogglePin = viewModel::toggleChannelPin,
                                    onDeleteChannel = viewModel::deleteChannel
                                )
                            }
                        }
                        MainTab.CONTACTS -> {
                            ContactsPane(
                                currentLogin = session.login,
                                registeredUsers = registeredUsers,
                                onStartDirectChat = { username, displayName ->
                                    viewModel.openOrCreateDirectMessage(username, displayName) {
                                        activeTab = MainTab.CHATS
                                        isMobileChatOpen = true
                                    }
                                }
                            )
                        }
                        MainTab.PROFILE -> {
                            ProfileSettingsPane(
                                session = session,
                                profileState = bridgeProfileState,
                                onSaveProfile = { name, status, presence, roomCode, serverUrl ->
                                    authSessionManager.updateSyncConfig(roomCode, serverUrl, name)
                                    viewModel.updateProfile(name, status, presence)
                                },
                                onSignOut = {
                                    signOut(context, credentialManager, authSessionManager, onSignedOut, scope)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showCreateChatDialog) {
            NewChatDialog(
                onDismiss = { showCreateChatDialog = false },
                onCreate = { name, topic, category ->
                    viewModel.createNewChannel(name, topic, category) {
                        showCreateChatDialog = false
                        isMobileChatOpen = true
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListPane(
    channelsState: UiState<List<Channel>>,
    selectedChannelId: String?,
    selectedCategory: String,
    searchQuery: String,
    onSelectCategory: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onNewChatClick: () -> Unit,
    onTogglePin: (Channel) -> Unit,
    onDeleteChannel: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "Сообщения",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Поиск чатов и сообщений...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Поиск") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("channel_search_input"),
                shape = RoundedCornerShape(24.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "Все",
                    "DIRECT" to "Личные",
                    "CHANNEL" to "Группы"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedCategory == key,
                        onClick = { onSelectCategory(key) },
                        label = { Text(label) }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        when (channelsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = channelsState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UiState.Success -> {
                val filtered = channelsState.data.filter { ch ->
                    val matchesCategory = when (selectedCategory) {
                        "ALL" -> true
                        "DIRECT" -> ch.category == "DIRECT"
                        else -> ch.category != "DIRECT"
                    }
                    val matchesSearch = searchQuery.isBlank() ||
                        ch.name.contains(searchQuery, ignoreCase = true) ||
                        ch.lastMessageText.contains(searchQuery, ignoreCase = true)
                    matchesCategory && matchesSearch
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "У вас пока нет активных чатов",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Начните личную переписку или создайте групповой чат",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onNewChatClick,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Начать чат")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filtered, key = { it.id }) { channel ->
                            val isSelected = channel.id == selectedChannelId
                            Surface(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChannelClick(channel) }
                                    .testTag("channel_card_${channel.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AvatarCircle(name = channel.name)

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = formatTimestamp(channel.updatedAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = channel.lastMessageText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row {
                                                if (channel.isPinned) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onDeleteChannel(channel.id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Удалить",
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationPane(
    session: AccountSession,
    channel: Channel,
    messagesState: UiState<List<Message>>,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit
) {
    var messageInput by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()

    LaunchedEffect(messages.size, channel.id) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                if (showBackButton) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AvatarCircle(name = channel.name, sizeDp = 38)
                    Column {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (channel.category == "DIRECT") "в сети" else channel.topic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Сообщений пока нет. Напишите первым!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isOwn = msg.senderHandle.equals("@${session.login}", ignoreCase = true) ||
                            msg.senderName.equals(session.displayName, ignoreCase = true)
                        ChatBubbleItem(
                            message = msg,
                            isOwnMessage = isOwn,
                            showSenderName = channel.category != "DIRECT" && !isOwn,
                            onDelete = { onDeleteMessage(msg.id) }
                        )
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("Сообщение...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_text_input"),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )

                IconButton(
                    onClick = {
                        if (messageInput.isNotBlank()) {
                            val textToSend = messageInput
                            messageInput = ""
                            onSendMessage(textToSend)
                        }
                    },
                    enabled = messageInput.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (messageInput.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить",
                        tint = if (messageInput.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: Message,
    isOwnMessage: Boolean,
    showSenderName: Boolean,
    onDelete: () -> Unit
) {
    val bubbleShape = if (isOwnMessage) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    val bubbleColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_card_${message.id}"),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            modifier = Modifier.widthIn(min = 80.dp, max = 310.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showSenderName) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatTimestamp(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                    if (isOwnMessage) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Доставлено",
                            modifier = Modifier.size(14.dp),
                            tint = textColor.copy(alpha = 0.85f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Удалить",
                        modifier = Modifier
                            .size(13.dp)
                            .clickable { onDelete() },
                        tint = textColor.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationPlaceholder(onNewChatClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Выберите чат слева или создайте новый диалог",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onNewChatClick) {
                Text("Новый чат")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsPane(
    currentLogin: String,
    registeredUsers: List<RegisteredUser>,
    onStartDirectChat: (String, String) -> Unit
) {
    var handleInput by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "Поиск людей и контакты",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Написать пользователю по логину",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = handleInput,
                            onValueChange = { handleInput = it },
                            placeholder = { Text("Введите @username...") },
                            leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        )
                        Button(
                            onClick = {
                                if (handleInput.isNotBlank()) {
                                    val clean = handleInput.trim().removePrefix("@")
                                    onStartDirectChat(clean, clean)
                                    handleInput = ""
                                }
                            },
                            enabled = handleInput.isNotBlank(),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Написать")
                        }
                    }
                }
            }

            Text(
                text = "Зарегистрированные аккаунты",
                style = MaterialTheme.typography.titleMedium
            )

            val otherUsers = registeredUsers.filterNot { it.login.equals(currentLogin, ignoreCase = true) }
            if (otherUsers.isEmpty()) {
                Text(
                    text = "Введите логин любого пользователя выше, чтобы сразу открыть с ним личный диалог.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(otherUsers, key = { it.userId }) { user ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStartDirectChat(user.login, user.displayName) }
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AvatarCircle(name = user.displayName)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "@${user.login}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                TextButton(onClick = { onStartDirectChat(user.login, user.displayName) }) {
                                    Text("Открыть чат")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSettingsPane(
    session: AccountSession,
    profileState: UiState<BridgeProfile?>,
    onSaveProfile: (String, String, String, String, String) -> Unit,
    onSignOut: () -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data
    var displayName by remember(profile?.displayName, session.displayName) {
        mutableStateOf(profile?.displayName ?: session.displayName)
    }
    var statusText by remember(profile?.statusText) {
        mutableStateOf(profile?.statusText ?: "В сети")
    }
    var presenceState by remember(profile?.presenceState) {
        mutableStateOf(profile?.presenceState ?: "ONLINE")
    }
    var syncRoomCode by remember(session.syncRoomCode) {
        mutableStateOf(session.syncRoomCode)
    }
    var serverUrl by remember(session.serverUrl) {
        mutableStateOf(session.serverUrl)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "Профиль и синхронизация",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AvatarCircle(name = displayName, sizeDp = 60)
                Column {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "@${session.login}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Имя в мессенджере") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = statusText,
                onValueChange = { statusText = it },
                label = { Text("О себе / Статус") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("URL сервера синхронизации (опционально)") },
                placeholder = { Text("https://... или оставьте пустым для Firebase") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Статус в сети", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ONLINE" to "В сети", "AWAY" to "Отошёл", "DND" to "Не беспокоить").forEach { (key, label) ->
                    FilterChip(
                        selected = presenceState == key,
                        onClick = { presenceState = key },
                        label = { Text(label) }
                    )
                }
            }

            Button(
                onClick = {
                    onSaveProfile(displayName, statusText, presenceState, syncRoomCode, serverUrl)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_bridge_profile_button")
            ) {
                Text("Сохранить")
            }

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Выйти из аккаунта")
            }
        }
    }
}

@Composable
private fun AvatarCircle(name: String, sizeDp: Int = 46) {
    val clean = name.trim().removePrefix("@").removePrefix("#")
    val initial = clean.firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun NewChatDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("DIRECT") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый диалог") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = category == "DIRECT",
                        onClick = { category = "DIRECT" },
                        label = { Text("Личный чат") }
                    )
                    FilterChip(
                        selected = category == "CHANNEL",
                        onClick = { category = "CHANNEL" },
                        label = { Text("Группа") }
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(
                            if (category == "DIRECT") "Имя или логин собеседника"
                            else "Название группы"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (category != "DIRECT") {
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Описание группы (опционально)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name, topic, category)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private fun formatTimestamp(timestamp: Timestamp?): String {
    val date = (timestamp ?: Timestamp.now()).toDate()
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(date)
}
