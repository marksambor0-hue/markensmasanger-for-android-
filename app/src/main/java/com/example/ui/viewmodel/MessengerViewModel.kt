package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AccountSession
import com.example.data.model.BridgeProfile
import com.example.data.model.Channel
import com.example.data.model.Message
import com.example.data.repository.UnifiedSyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MessengerViewModel(
    private val syncEngine: UnifiedSyncEngine,
    private val session: AccountSession
) : ViewModel() {

    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelId: StateFlow<String?> = _selectedChannelId.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("ALL")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        syncEngine.initializeForSession(session)
    }

    val channelsState: StateFlow<UiState<List<Channel>>> = syncEngine.observeChannels()
        .map<List<Channel>, UiState<List<Channel>>> { channels ->
            if (_selectedChannelId.value == null && channels.isNotEmpty()) {
                _selectedChannelId.value = channels.first().id
            }
            UiState.Success(channels)
        }
        .catch { error ->
            Log.w(TAG, "Error observing channels", error)
            emit(UiState.Error(error.message ?: "Не удалось загрузить чаты"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<UiState<List<Message>>> = _selectedChannelId
        .flatMapLatest { channelId ->
            if (channelId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                syncEngine.observeMessages(channelId)
                    .map<List<Message>, UiState<List<Message>>> { UiState.Success(it) }
                    .catch { error ->
                        Log.w(TAG, "Error observing messages", error)
                        emit(UiState.Error(error.message ?: "Не удалось загрузить сообщения"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val bridgeProfileState: StateFlow<UiState<BridgeProfile?>> = syncEngine.observeProfile()
        .map<BridgeProfile?, UiState<BridgeProfile?>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing profile", error)
            emit(UiState.Error(error.message ?: "Не удалось загрузить профиль"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun selectChannel(channelId: String?) {
        _selectedChannelId.value = channelId
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun openOrCreateDirectMessage(username: String, displayName: String, onReady: (Channel) -> Unit = {}) {
        viewModelScope.launch {
            val ch = syncEngine.createOrOpenDirectChat(session, username, displayName)
            _selectedChannelId.value = ch.id
            onReady(ch)
        }
    }

    fun createNewChannel(
        name: String,
        topic: String,
        category: String,
        onCreated: (Channel) -> Unit = {}
    ) {
        viewModelScope.launch {
            val created = syncEngine.createChannel(
                session = session,
                name = name,
                topic = topic,
                category = category,
                linkedRepo = ""
            )
            _selectedChannelId.value = created.id
            onCreated(created)
        }
    }

    fun toggleChannelPin(channel: Channel) {
        viewModelScope.launch {
            syncEngine.toggleChannelPin(session, channel)
        }
    }

    fun deleteChannel(channelId: String) {
        viewModelScope.launch {
            syncEngine.deleteChannel(session, channelId)
            if (_selectedChannelId.value == channelId) {
                _selectedChannelId.value = (channelsState.value as? UiState.Success)?.data?.firstOrNull()?.id
            }
        }
    }

    fun sendMessage(
        channelId: String,
        text: String
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            syncEngine.sendMessage(
                session = session,
                channelId = channelId,
                text = text
            )
        }
    }

    fun addReaction(message: Message) {
        viewModelScope.launch {
            syncEngine.addReaction(session, message)
        }
    }

    fun toggleMessagePin(message: Message) {
        viewModelScope.launch {
            syncEngine.toggleMessagePin(session, message)
        }
    }

    fun deleteMessage(channelId: String, messageId: String) {
        viewModelScope.launch {
            syncEngine.deleteMessage(session, channelId, messageId)
        }
    }

    fun updateProfile(
        displayName: String,
        statusText: String,
        presenceState: String
    ) {
        viewModelScope.launch {
            syncEngine.updateBridgeProfile(
                session = session,
                displayName = displayName,
                statusText = statusText,
                presenceState = presenceState,
                githubRepo = "",
                exeBridgeMode = "ACTIVE_SYNC"
            )
            _statusBanner.value = "Профиль сохранён"
        }
    }

    private companion object {
        const val TAG = "MessengerVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
