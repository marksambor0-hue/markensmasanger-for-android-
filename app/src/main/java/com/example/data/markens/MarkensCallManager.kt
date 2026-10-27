package com.example.data.markens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MarkensCallStatus {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTING,
    CONNECTED,
    BUSY,
    DECLINED,
    TIMEOUT,
    NETWORK_ERROR,
    ENDED
}

@Immutable
data class MarkensIceCandidate(
    val candidate: String,
    val sdpMid: String = "0",
    val sdpMLineIndex: Int = 0
)

@Immutable
data class MarkensCallSession(
    val callId: String,
    val caller: String,
    val callee: String,
    val isVideo: Boolean,
    val status: MarkensCallStatus,
    val startedAtTs: Long,
    val connectedAtTs: Long = 0L,
    val isIncoming: Boolean = false,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isLocalVideoEnabled: Boolean = false,
    val isFrontCamera: Boolean = true,
    val remoteMicMuted: Boolean = false,
    val remoteVideoEnabled: Boolean = false,
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val stunServer: String = "stun:stun.l.google.com:19302",
    val errorReason: String? = null
) {
    val peerName: String
        get() = if (isIncoming) caller else callee
}

/**
 * Manages real-time Voice & Video call signaling (compatible with Web/Windows Firebase RTDB `/calls_v25`)
 * and hardware audio routing, permission verification, busy detection, and resource cleanup.
 */
class MarkensCallManager(
    context: Context,
    private val cloudClient: MarkensCloudClient
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _activeCall = MutableStateFlow<MarkensCallSession?>(null)
    val activeCall: StateFlow<MarkensCallSession?> = _activeCall.asStateFlow()

    private val _callDurationSec = MutableStateFlow(0L)
    val callDurationSec: StateFlow<Long> = _callDurationSec.asStateFlow()

    private var signalingPollingJob: Job? = null
    private var durationTimerJob: Job? = null
    private var ringtoneJob: Job? = null
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeakerphoneState: Boolean = false
    private var audioConfigured = false

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startOutgoingCall(
        currentUser: MarkensUser,
        targetPeer: String,
        isVideo: Boolean
    ): Result<MarkensCallSession> {
        val cleanPeer = targetPeer.trim()
        if (cleanPeer.isEmpty() || cleanPeer.equals(currentUser.name, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Некорректный адресат вызова"))
        }
        val existing = _activeCall.value
        if (existing != null && existing.status in listOf(
                MarkensCallStatus.OUTGOING_RINGING,
                MarkensCallStatus.INCOMING_RINGING,
                MarkensCallStatus.CONNECTING,
                MarkensCallStatus.CONNECTED
            )
        ) {
            return Result.failure(IllegalStateException("Вы уже находитесь в активном звонке"))
        }
        if (!hasAudioPermission()) {
            return Result.failure(SecurityException("Для звонка требуется разрешение на доступ к микрофону"))
        }
        if (isVideo && !hasCameraPermission()) {
            return Result.failure(SecurityException("Для видеозвонка требуется разрешение на доступ к камере"))
        }

        val now = System.currentTimeMillis()
        val pair = listOf(currentUser.name.lowercase(), cleanPeer.lowercase()).sorted().joinToString("_")
        val callId = "call_${now}_$pair"
        val localSdpOffer = buildStandardWebRtcOfferSdp(
            caller = currentUser.name,
            sessionId = now,
            isVideo = isVideo
        )

        val session = MarkensCallSession(
            callId = callId,
            caller = currentUser.name,
            callee = cleanPeer,
            isVideo = isVideo,
            status = MarkensCallStatus.OUTGOING_RINGING,
            startedAtTs = now,
            isIncoming = false,
            isMicMuted = false,
            isSpeakerOn = true,
            isLocalVideoEnabled = isVideo,
            isFrontCamera = true,
            offerSdp = localSdpOffer
        )

        _activeCall.value = session
        _callDurationSec.value = 0L
        configureCallAudioHardware(enableSpeaker = true, muteMic = false)
        startRingbackToneLoop()

        scope.launch {
            val published = cloudClient.publishCallSignal(session)
            if (published.isFailure) {
                _activeCall.value = session.copy(
                    status = MarkensCallStatus.NETWORK_ERROR,
                    errorReason = "Не удалось установить соединение с сервером вызовов. Проверьте интернет."
                )
                stopRingtoneLoop()
                releaseCallAudioHardware()
                return@launch
            }
            observeCallSessionLoop(session.callId, currentUser.name)
        }

        return Result.success(session)
    }

    fun handleIncomingCallFromCloud(incoming: MarkensCallSession, myUserName: String) {
        val current = _activeCall.value
        if (current != null && current.callId != incoming.callId && current.status in listOf(
                MarkensCallStatus.OUTGOING_RINGING,
                MarkensCallStatus.INCOMING_RINGING,
                MarkensCallStatus.CONNECTING,
                MarkensCallStatus.CONNECTED
            )
        ) {
            // User is already in another call: send BUSY signal automatically
            scope.launch {
                cloudClient.updateCallStatusInCloud(
                    callId = incoming.callId,
                    callee = myUserName,
                    newStatus = "BUSY"
                )
            }
            return
        }

        if (current == null || current.status in listOf(
                MarkensCallStatus.IDLE,
                MarkensCallStatus.ENDED,
                MarkensCallStatus.DECLINED,
                MarkensCallStatus.BUSY,
                MarkensCallStatus.TIMEOUT,
                MarkensCallStatus.NETWORK_ERROR
            )
        ) {
            _activeCall.value = incoming.copy(
                status = MarkensCallStatus.INCOMING_RINGING,
                isIncoming = true
            )
            _callDurationSec.value = 0L
            startIncomingRingtoneLoop()
            observeCallSessionLoop(incoming.callId, myUserName)
        }
    }

    fun acceptIncomingCall(currentUser: MarkensUser): Result<Unit> {
        val session = _activeCall.value ?: return Result.failure(IllegalStateException("Нет активного входящего вызова"))
        if (!hasAudioPermission()) {
            return Result.failure(SecurityException("Для ответа на звонок необходимо разрешение на микрофон"))
        }
        val videoAllowed = session.isVideo && hasCameraPermission()
        stopRingtoneLoop()

        val now = System.currentTimeMillis()
        val answerSdp = buildStandardWebRtcAnswerSdp(
            callee = currentUser.name,
            sessionId = now,
            isVideo = videoAllowed
        )
        val connectedSession = session.copy(
            status = MarkensCallStatus.CONNECTED,
            connectedAtTs = now,
            isLocalVideoEnabled = videoAllowed,
            answerSdp = answerSdp
        )
        _activeCall.value = connectedSession
        configureCallAudioHardware(enableSpeaker = connectedSession.isSpeakerOn, muteMic = connectedSession.isMicMuted)
        startDurationTimer()

        scope.launch {
            val res = cloudClient.acceptCallInCloud(connectedSession)
            if (res.isFailure) {
                _activeCall.value = connectedSession.copy(
                    status = MarkensCallStatus.NETWORK_ERROR,
                    errorReason = "Ошибка сети при подтверждении звонка"
                )
                releaseAllCallResources()
            }
        }
        return Result.success(Unit)
    }

    fun declineOrEndCall(currentUserName: String) {
        val session = _activeCall.value ?: return
        val newStatus = if (session.isIncoming && session.status == MarkensCallStatus.INCOMING_RINGING) {
            MarkensCallStatus.DECLINED
        } else {
            MarkensCallStatus.ENDED
        }
        val cloudStatusStr = if (newStatus == MarkensCallStatus.DECLINED) "DECLINED" else "ENDED"
        _activeCall.value = session.copy(status = newStatus)
        releaseAllCallResources()

        scope.launch {
            cloudClient.updateCallStatusInCloud(
                callId = session.callId,
                callee = session.callee,
                newStatus = cloudStatusStr
            )
            delay(1400L)
            if (_activeCall.value?.callId == session.callId) {
                _activeCall.value = null
            }
        }
    }

    fun dismissEndedCallBanner() {
        releaseAllCallResources()
        _activeCall.value = null
    }

    fun toggleMuteMic() {
        val cur = _activeCall.value ?: return
        val nextMute = !cur.isMicMuted
        _activeCall.value = cur.copy(isMicMuted = nextMute)
        try {
            audioManager?.isMicrophoneMute = nextMute
        } catch (_: Exception) {
        }
        scope.launch {
            cloudClient.updateCallMediaFlagsInCloud(
                callId = cur.callId,
                isCaller = !cur.isIncoming,
                micMuted = nextMute,
                videoEnabled = cur.isLocalVideoEnabled
            )
        }
    }

    fun toggleSpeakerphone() {
        val cur = _activeCall.value ?: return
        val nextSpeaker = !cur.isSpeakerOn
        _activeCall.value = cur.copy(isSpeakerOn = nextSpeaker)
        try {
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = nextSpeaker
        } catch (_: Exception) {
        }
    }

    fun toggleLocalVideo(): Result<Boolean> {
        val cur = _activeCall.value ?: return Result.failure(IllegalStateException("Нет активного звонка"))
        val nextVideo = !cur.isLocalVideoEnabled
        if (nextVideo && !hasCameraPermission()) {
            return Result.failure(SecurityException("Разрешите доступ к камере для включения видео"))
        }
        _activeCall.value = cur.copy(isLocalVideoEnabled = nextVideo)
        scope.launch {
            cloudClient.updateCallMediaFlagsInCloud(
                callId = cur.callId,
                isCaller = !cur.isIncoming,
                micMuted = cur.isMicMuted,
                videoEnabled = nextVideo
            )
        }
        return Result.success(nextVideo)
    }

    fun switchCameraFacing() {
        val cur = _activeCall.value ?: return
        _activeCall.value = cur.copy(isFrontCamera = !cur.isFrontCamera)
    }

    private fun observeCallSessionLoop(callId: String, myUserName: String) {
        signalingPollingJob?.cancel()
        signalingPollingJob = scope.launch {
            val startedMs = System.currentTimeMillis()
            var consecutiveErrors = 0
            while (isActive) {
                val cur = _activeCall.value ?: break
                if (cur.callId != callId) break

                // 30-second unanswered ringing timeout
                if (cur.status in listOf(MarkensCallStatus.OUTGOING_RINGING, MarkensCallStatus.INCOMING_RINGING) &&
                    System.currentTimeMillis() - startedMs > 30_000L
                ) {
                    _activeCall.value = cur.copy(
                        status = MarkensCallStatus.TIMEOUT,
                        errorReason = "Абонент не отвечает"
                    )
                    cloudClient.updateCallStatusInCloud(callId, cur.callee, "TIMEOUT")
                    releaseAllCallResources()
                    delay(1800L)
                    if (_activeCall.value?.callId == callId) _activeCall.value = null
                    break
                }

                val remoteResult = cloudClient.fetchCallSession(callId, isIncoming = cur.isIncoming)
                if (remoteResult.isFailure) {
                    consecutiveErrors++
                    if (consecutiveErrors >= 5) {
                        _activeCall.value = cur.copy(
                            status = MarkensCallStatus.NETWORK_ERROR,
                            errorReason = "Потеряно соединение с сетью во время звонка"
                        )
                        releaseAllCallResources()
                        break
                    }
                } else {
                    consecutiveErrors = 0
                    val remote = remoteResult.getOrNull()
                    if (remote != null) {
                        when (remote.status) {
                            MarkensCallStatus.CONNECTED -> {
                                if (cur.status != MarkensCallStatus.CONNECTED) {
                                    stopRingtoneLoop()
                                    val now = System.currentTimeMillis()
                                    _activeCall.value = cur.copy(
                                        status = MarkensCallStatus.CONNECTED,
                                        connectedAtTs = if (cur.connectedAtTs > 0L) cur.connectedAtTs else now,
                                        answerSdp = remote.answerSdp ?: cur.answerSdp,
                                        remoteMicMuted = remote.remoteMicMuted,
                                        remoteVideoEnabled = remote.remoteVideoEnabled
                                    )
                                    startDurationTimer()
                                } else {
                                    _activeCall.value = cur.copy(
                                        remoteMicMuted = remote.remoteMicMuted,
                                        remoteVideoEnabled = remote.remoteVideoEnabled
                                    )
                                }
                            }
                            MarkensCallStatus.DECLINED,
                            MarkensCallStatus.BUSY,
                            MarkensCallStatus.TIMEOUT,
                            MarkensCallStatus.ENDED -> {
                                _activeCall.value = cur.copy(status = remote.status)
                                releaseAllCallResources()
                                delay(1600L)
                                if (_activeCall.value?.callId == callId) {
                                    _activeCall.value = null
                                }
                                break
                            }
                            else -> Unit
                        }
                    }
                }

                delay(1500L)
            }
        }
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = scope.launch {
            val baseTs = _activeCall.value?.connectedAtTs?.takeIf { it > 0L } ?: System.currentTimeMillis()
            while (isActive && _activeCall.value?.status == MarkensCallStatus.CONNECTED) {
                _callDurationSec.value = ((System.currentTimeMillis() - baseTs) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    private fun startRingbackToneLoop() {
        ringtoneJob?.cancel()
        ringtoneJob = scope.launch(Dispatchers.Default) {
            while (isActive && _activeCall.value?.status == MarkensCallStatus.OUTGOING_RINGING) {
                playSoftTone(freqHz = 425.0, durationMs = 450)
                delay(2200L)
            }
        }
    }

    private fun startIncomingRingtoneLoop() {
        ringtoneJob?.cancel()
        ringtoneJob = scope.launch(Dispatchers.Default) {
            while (isActive && _activeCall.value?.status == MarkensCallStatus.INCOMING_RINGING) {
                playSoftTone(freqHz = 523.25, durationMs = 180)
                delay(90L)
                playSoftTone(freqHz = 659.25, durationMs = 240)
                delay(1600L)
            }
        }
    }

    private suspend fun playSoftTone(freqHz: Double, durationMs: Int) = withContext(Dispatchers.Default) {
        try {
            val sampleRate = 24000
            val numSamples = (sampleRate * durationMs) / 1000
            val pcm = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val env = ((t / 0.015).coerceAtMost(1.0)) * exp(-t * 3.2)
                val v = sin(2.0 * PI * freqHz * t) * env * 0.35
                pcm[i] = (v.coerceIn(-0.9, 0.9) * Short.MAX_VALUE).toInt().toShort()
            }
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(pcm.size * 2)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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
                delay(durationMs.toLong() + 40L)
                track.stop()
            }
            track.release()
        } catch (_: Exception) {
        }
    }

    private fun stopRingtoneLoop() {
        ringtoneJob?.cancel()
        ringtoneJob = null
    }

    private fun configureCallAudioHardware(enableSpeaker: Boolean, muteMic: Boolean) {
        try {
            val am = audioManager ?: return
            if (!audioConfigured) {
                savedAudioMode = am.mode
                @Suppress("DEPRECATION")
                savedSpeakerphoneState = am.isSpeakerphoneOn
                audioConfigured = true
            }
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = enableSpeaker
            am.isMicrophoneMute = muteMic
        } catch (_: Exception) {
        }
    }

    private fun releaseCallAudioHardware() {
        if (!audioConfigured) return
        try {
            val am = audioManager ?: return
            am.isMicrophoneMute = false
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = savedSpeakerphoneState
            am.mode = savedAudioMode
        } catch (_: Exception) {
        } finally {
            audioConfigured = false
        }
    }

    fun releaseAllCallResources() {
        stopRingtoneLoop()
        durationTimerJob?.cancel()
        durationTimerJob = null
        signalingPollingJob?.cancel()
        signalingPollingJob = null
        releaseCallAudioHardware()
    }

    fun shutdown() {
        releaseAllCallResources()
        scope.cancel()
    }

    fun destroy() {
        shutdown()
    }

    companion object {
        fun buildStandardWebRtcOfferSdp(caller: String, sessionId: Long, isVideo: Boolean): String {
            return buildString {
                appendLine("v=0")
                appendLine("o=$caller $sessionId 2 IN IP4 127.0.0.1")
                appendLine("s=MARKENS-WebRTC-Session")
                appendLine("t=0 0")
                appendLine("a=group:BUNDLE 0${if (isVideo) " 1" else ""}")
                appendLine("m=audio 9 UDP/TLS/RTP/SAVPF 111")
                appendLine("c=IN IP4 0.0.0.0")
                appendLine("a=rtpmap:111 opus/48000/2")
                appendLine("a=sendrecv")
                if (isVideo) {
                    appendLine("m=video 9 UDP/TLS/RTP/SAVPF 96")
                    appendLine("a=rtpmap:96 VP8/90000")
                    appendLine("a=sendrecv")
                }
            }
        }

        fun buildStandardWebRtcAnswerSdp(callee: String, sessionId: Long, isVideo: Boolean): String {
            return buildStandardWebRtcOfferSdp(callee, sessionId, isVideo)
        }
    }
}

/**
 * Records real PCM audio from the device microphone into a standard cross-platform WAV data URL
 * (`data:audio/wav;base64,...`) playable on both Android and Web/Windows browsers.
 */
class MarkensVoiceMessageEngine(private val context: Context) {
    private val isRecordingAtomic = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private val recordedPcmStream = ByteArrayOutputStream()

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(scope: CoroutineScope): Result<Unit> {
        if (!hasRecordPermission()) {
            return Result.failure(SecurityException("Нет разрешения на запись микрофона"))
        }
        if (isRecordingAtomic.getAndSet(true)) {
            return Result.success(Unit)
        }
        synchronized(recordedPcmStream) {
            recordedPcmStream.reset()
        }

        recordingJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    isRecordingAtomic.set(false)
                    return@launch
                }
                audioRecord.startRecording()
                val buffer = ByteArray(1024)
                val maxBytes = sampleRate * 2 * 30 // Max 30 seconds voice note to bound RTDB size
                while (isActive && isRecordingAtomic.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        synchronized(recordedPcmStream) {
                            if (recordedPcmStream.size() + read <= maxBytes) {
                                recordedPcmStream.write(buffer, 0, read)
                            } else {
                                isRecordingAtomic.set(false)
                            }
                        }
                    }
                }
            } catch (_: SecurityException) {
                isRecordingAtomic.set(false)
            } catch (_: Exception) {
                isRecordingAtomic.set(false)
            } finally {
                try {
                    audioRecord?.stop()
                } catch (_: Exception) {
                }
                try {
                    audioRecord?.release()
                } catch (_: Exception) {
                }
            }
        }
        return Result.success(Unit)
    }

    suspend fun stopRecordingAndEncodeDataUrl(): String? = withContext(Dispatchers.IO) {
        isRecordingAtomic.set(false)
        recordingJob?.join()
        recordingJob = null
        val pcmBytes = synchronized(recordedPcmStream) {
            val arr = recordedPcmStream.toByteArray()
            recordedPcmStream.reset()
            arr
        }
        if (pcmBytes.size < 1600) return@withContext null // Less than 50ms
        val wavBytes = wrapPcmInWavHeader(pcmBytes, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val b64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        "data:audio/wav;base64,$b64"
    }

    fun cancelRecording() {
        isRecordingAtomic.set(false)
        recordingJob?.cancel()
        recordingJob = null
        synchronized(recordedPcmStream) {
            recordedPcmStream.reset()
        }
    }

    companion object {
        private val staticScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var activeEngine: MarkensVoiceMessageEngine? = null
        private var activeCallback: ((String?, Int) -> Unit)? = null
        private var startedAtMs: Long = 0L

        fun isCurrentlyRecording(): Boolean {
            return activeEngine?.isRecordingAtomic?.get() == true
        }

        fun startRecording(
            context: Context,
            onFinished: (wavDataUrl: String?, durationSec: Int) -> Unit
        ): Result<Unit> {
            cancelRecording()
            val engine = MarkensVoiceMessageEngine(context.applicationContext)
            val res = engine.startRecording(staticScope)
            if (res.isSuccess) {
                activeEngine = engine
                activeCallback = onFinished
                startedAtMs = System.currentTimeMillis()
            }
            return res
        }

        fun stopRecordingAndFinalize() {
            val engine = activeEngine ?: return
            val cb = activeCallback
            val durSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).toInt().coerceAtLeast(1)
            activeEngine = null
            activeCallback = null
            staticScope.launch {
                val dataUrl = engine.stopRecordingAndEncodeDataUrl()
                cb?.invoke(dataUrl, durSec)
            }
        }

        fun cancelRecording() {
            activeEngine?.cancelRecording()
            activeEngine = null
            activeCallback = null
        }

        fun wrapPcmInWavHeader(
            pcmData: ByteArray,
            sampleRate: Int = 16000,
            channels: Int = 1,
            bitsPerSample: Int = 16
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = (channels * bitsPerSample / 8).toShort()
            val totalDataLen = pcmData.size + 36
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalDataLen)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1) // AudioFormat 1 = PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign)
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(pcmData.size)

            val out = ByteArray(44 + pcmData.size)
            System.arraycopy(header.array(), 0, out, 0, 44)
            System.arraycopy(pcmData, 0, out, 44, pcmData.size)
            return out
        }

        suspend fun playWavDataUrl(
            dataUrl: String,
            onFinished: (() -> Unit)? = null
        ) = withContext(Dispatchers.IO) {
            try {
                if (!dataUrl.startsWith("data:audio")) return@withContext
                val comma = dataUrl.indexOf(',')
                if (comma < 0) return@withContext
                val rawBytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                if (rawBytes.size <= 44) return@withContext
                val sampleRate = ByteBuffer.wrap(rawBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 48000)
                val pcmOffset = 44
                val pcmLen = rawBytes.size - pcmOffset
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(pcmLen)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
                    track.write(rawBytes, pcmOffset, pcmLen)
                    val durationMs = (pcmLen.toLong() * 1000L) / (sampleRate * 2).coerceAtLeast(1)
                    delay(durationMs + 60L)
                    track.stop()
                }
                track.release()
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    onFinished?.invoke()
                }
            }
        }
    }
}
