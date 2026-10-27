package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.markens.MarkensCallManager
import com.example.data.markens.MarkensCallSession
import com.example.data.markens.MarkensCallStatus
import com.example.data.markens.MarkensUser
import java.util.Locale

private fun formatDurationMmSs(seconds: Long): String {
    val m = seconds / 60L
    val s = seconds % 60L
    return String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
fun MarkensCallOverlay(
    session: MarkensCallSession,
    durationSec: Long,
    currentUser: MarkensUser,
    peerAvatarUrl: String?,
    callManager: MarkensCallManager,
    palette: MarkensPalette,
    onPermissionError: (String) -> Unit
) {
    var isMinimized by remember(session.callId) { mutableStateOf(false) }

    val acceptPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val micGranted = perms[Manifest.permission.RECORD_AUDIO] == true || callManager.hasAudioPermission()
        if (!micGranted) {
            onPermissionError("Для звонка необходимо разрешить доступ к микрофону в настройках")
            return@rememberLauncherForActivityResult
        }
        val res = callManager.acceptIncomingCall(currentUser)
        res.onFailure { onPermissionError(it.message ?: "Ошибка подключения к звонку") }
    }

    val cameraTogglePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            callManager.toggleLocalVideo()
        } else {
            onPermissionError("Для включения видео необходимо разрешение на камеру")
        }
    }

    val statusLabel = when (session.status) {
        MarkensCallStatus.OUTGOING_RINGING -> if (session.isVideo) "📹 Видеовызов... Ожидание ответа" else "📞 Вызов... Ожидание ответа"
        MarkensCallStatus.INCOMING_RINGING -> if (session.isVideo) "📹 Входящий видеозвонок" else "📞 Входящий голосовой звонок"
        MarkensCallStatus.CONNECTING -> "Установка защищённого соединения..."
        MarkensCallStatus.CONNECTED -> "🟢 В звонке • ${formatDurationMmSs(durationSec)}"
        MarkensCallStatus.BUSY -> "Абонент занят в другом звонке"
        MarkensCallStatus.DECLINED -> "Вызов отклонён"
        MarkensCallStatus.TIMEOUT -> "Абонент не отвечает"
        MarkensCallStatus.NETWORK_ERROR -> session.errorReason ?: "Ошибка сети во время звонка"
        MarkensCallStatus.ENDED -> "Звонок завершён"
        MarkensCallStatus.IDLE -> ""
    }

    val isTerminalState = session.status in listOf(
        MarkensCallStatus.BUSY,
        MarkensCallStatus.DECLINED,
        MarkensCallStatus.TIMEOUT,
        MarkensCallStatus.NETWORK_ERROR,
        MarkensCallStatus.ENDED
    )

    Surface(
        color = palette.bgSecondary,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .border(
                width = 1.5.dp,
                color = when {
                    session.status == MarkensCallStatus.CONNECTED -> palette.greenColor
                    isTerminalState -> Color(0xFFEF4444)
                    else -> palette.brandColor
                },
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("markens_call_overlay")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top bar: Avatar + Peer Name + Status + Minimize/Expand button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MarkensAvatarBox(
                    name = session.peerName,
                    avatarUrl = peerAvatarUrl,
                    size = 40.dp,
                    bgColor = palette.brandColor,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.peerName,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (session.remoteMicMuted && session.status == MarkensCallStatus.CONNECTED) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🔇 микрофон выкл.",
                                color = Color(0xFFF59E0B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = statusLabel,
                        color = when {
                            session.status == MarkensCallStatus.CONNECTED -> palette.greenColor
                            isTerminalState -> Color(0xFFEF4444)
                            else -> palette.textSecondary
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (session.status == MarkensCallStatus.CONNECTED || session.status == MarkensCallStatus.OUTGOING_RINGING) {
                    IconButton(
                        onClick = { isMinimized = !isMinimized },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (isMinimized) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = if (isMinimized) "Развернуть" else "Свернуть",
                            tint = palette.textMuted
                        )
                    }
                }
            }

            if (!isMinimized) {
                // Video Stage when video is active on either side
                if ((session.isLocalVideoEnabled || session.remoteVideoEnabled) &&
                    session.status in listOf(MarkensCallStatus.OUTGOING_RINGING, MarkensCallStatus.CONNECTED)
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.bgTertiary)
                            .border(1.dp, palette.borderColor, RoundedCornerShape(12.dp))
                            .testTag("call_video_stage"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MarkensAvatarBox(
                                name = session.peerName,
                                avatarUrl = peerAvatarUrl,
                                size = 54.dp,
                                bgColor = palette.brandColor,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (session.remoteVideoEnabled) {
                                    "Видеопоток @${session.peerName} активен"
                                } else {
                                    "Камера @${session.peerName} отключена"
                                },
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Self PIP Camera Preview Badge
                        if (session.isLocalVideoEnabled) {
                            Surface(
                                color = palette.bgSecondary.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .border(1.dp, palette.greenColor, RoundedCornerShape(8.dp))
                                    .clickable { callManager.switchCameraFacing() }
                                    .testTag("call_self_pip_box")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cameraswitch,
                                        contentDescription = "Переключить камеру",
                                        tint = palette.greenColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (session.isFrontCamera) "Вы • Фронтальная" else "Вы • Основная",
                                        color = palette.textPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Call Controls Row
                when {
                    session.status == MarkensCallStatus.INCOMING_RINGING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val permsToAsk = if (session.isVideo) {
                                        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                                    } else {
                                        arrayOf(Manifest.permission.RECORD_AUDIO)
                                    }
                                    if (callManager.hasAudioPermission() && (!session.isVideo || callManager.hasCameraPermission())) {
                                        val res = callManager.acceptIncomingCall(currentUser)
                                        res.onFailure { onPermissionError(it.message ?: "Ошибка") }
                                    } else {
                                        acceptPermissionsLauncher.launch(permsToAsk)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.greenColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("accept_incoming_call_button")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Принять", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = { callManager.declineOrEndCall(currentUser.name) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("decline_incoming_call_button")
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Отклонить", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    isTerminalState -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { callManager.dismissEndedCallBanner() },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.bgTertiary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("dismiss_call_banner_button")
                            ) {
                                Text("Закрыть", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {
                        // Active / Outgoing Call Hardware Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Mute/Unmute Mic
                            CallControlButton(
                                active = !session.isMicMuted,
                                activeColor = palette.bgTertiary,
                                inactiveColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                icon = if (session.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                iconTint = if (session.isMicMuted) Color(0xFFEF4444) else palette.textPrimary,
                                label = if (session.isMicMuted) "Микр. выкл" else "Микрофон",
                                palette = palette,
                                testTag = "call_toggle_mic_button",
                                onClick = { callManager.toggleMuteMic() }
                            )

                            // 2. Speakerphone Toggle
                            CallControlButton(
                                active = session.isSpeakerOn,
                                activeColor = palette.brandColor.copy(alpha = 0.22f),
                                inactiveColor = palette.bgTertiary,
                                icon = if (session.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                iconTint = if (session.isSpeakerOn) palette.brandColor else palette.textSecondary,
                                label = if (session.isSpeakerOn) "Динамик" else "Телефон",
                                palette = palette,
                                testTag = "call_toggle_speaker_button",
                                onClick = { callManager.toggleSpeakerphone() }
                            )

                            // 3. Camera Video On/Off
                            CallControlButton(
                                active = session.isLocalVideoEnabled,
                                activeColor = palette.greenColor.copy(alpha = 0.22f),
                                inactiveColor = palette.bgTertiary,
                                icon = if (session.isLocalVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                iconTint = if (session.isLocalVideoEnabled) palette.greenColor else palette.textSecondary,
                                label = if (session.isLocalVideoEnabled) "Видео вкл" else "Видео",
                                palette = palette,
                                testTag = "call_toggle_video_button",
                                onClick = {
                                    if (!session.isLocalVideoEnabled && !callManager.hasCameraPermission()) {
                                        cameraTogglePermissionLauncher.launch(Manifest.permission.CAMERA)
                                    } else {
                                        val res = callManager.toggleLocalVideo()
                                        res.onFailure { onPermissionError(it.message ?: "Ошибка камеры") }
                                    }
                                }
                            )

                            // 4. Switch Front/Back Camera (if video enabled)
                            if (session.isLocalVideoEnabled) {
                                CallControlButton(
                                    active = true,
                                    activeColor = palette.bgTertiary,
                                    inactiveColor = palette.bgTertiary,
                                    icon = Icons.Default.Cameraswitch,
                                    iconTint = palette.textPrimary,
                                    label = if (session.isFrontCamera) "Фронт." else "Тыл.",
                                    palette = palette,
                                    testTag = "call_switch_camera_button",
                                    onClick = { callManager.switchCameraFacing() }
                                )
                            }

                            // 5. Hang Up / End Call
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    onClick = { callManager.declineOrEndCall(currentUser.name) },
                                    color = Color(0xFFEF4444),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("call_hangup_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CallEnd,
                                            contentDescription = "Завершить звонок",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Сброс",
                                    color = Color(0xFFEF4444),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    palette: MarkensPalette,
    testTag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            color = if (active) activeColor else inactiveColor,
            shape = CircleShape,
            modifier = Modifier
                .size(44.dp)
                .border(1.dp, palette.borderColor, CircleShape)
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = palette.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
