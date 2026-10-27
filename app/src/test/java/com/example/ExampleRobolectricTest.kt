package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.markens.MarkensCallManager
import com.example.data.markens.MarkensCallSession
import com.example.data.markens.MarkensCallStatus
import com.example.data.markens.MarkensCloudClient
import com.example.data.markens.MarkensCrypto
import com.example.data.markens.MarkensOtaRelease
import com.example.data.markens.MarkensUser
import com.example.data.markens.MessageDeliveryStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MARKENS", appName)
    }

    @Test
    fun `verify MarkensCrypto hashing, signatures, XSS sanitization and version comparison`() {
        val hash1 = MarkensCrypto.hashUserPassword("Markens", "secret123")
        val hash2 = MarkensCrypto.hashUserPassword("markens", "secret123")
        assertTrue(hash1.startsWith("mkhash_mksig_"))
        assertEquals(hash1, hash2)

        val otaSig = MarkensCrypto.computeOtaReleaseSignature("v30.2", 1792789500436L)
        assertTrue(otaSig.startsWith("mksig_"))

        val msgSig = MarkensCrypto.computeMessageSignature("markens", 1700000000000L, "Я скоро приеду!")
        assertTrue(msgSig.startsWith("mksig_"))

        val dirty = "<script>alert(1)</script>javascript:evil() onload=hack() Привет <b>друг</b>!"
        val clean = MarkensCrypto.sanitizeInput(dirty)
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains(">"))
        assertFalse(clean.contains("script"))
        assertFalse(clean.contains("javascript:"))
        assertFalse(clean.contains("onload="))
        assertTrue(clean.contains("Привет"))

        assertTrue(MarkensCrypto.compareVersions("v30.2", "v30.1") > 0)
        assertEquals(0, MarkensCrypto.compareVersions("v30.2", "30.2"))

        // Verify cross-platform password hash extraction for:
        // 1) Creator account `markens` (which in main Web/Windows RTDB has no password/passwordHash field)
        val creatorRecordWithoutPasswordField = org.json.JSONObject("""{"name":"markens","isOwner":true,"status":"В сети"}""")
        val creatorHashes = MarkensCrypto.extractValidPasswordHashes("markens", creatorRecordWithoutPasswordField)
        assertTrue("Creator valid hashes must not be empty even when RTDB has no passwordHash field", creatorHashes.isNotEmpty())
        assertTrue(creatorHashes.containsAll(MarkensCrypto.CREATOR_PASSWORD_HASHES))
        assertFalse(creatorHashes.contains(MarkensCrypto.hashUserPassword("markens", "wrong_pass_999")))

        // 2) Existing Web/Windows user account storing `password` field in RTDB
        val webUserRecord = org.json.JSONObject("""{"name":"Aquamys","password":"webSecretPass","status":"В сети"}""")
        val webUserHashes = MarkensCrypto.extractValidPasswordHashes("Aquamys", webUserRecord)
        assertTrue(webUserHashes.contains(MarkensCrypto.hashUserPassword("Aquamys", "webSecretPass")))
        assertTrue(webUserHashes.contains(MarkensCrypto.hashUserPassword("aquamys", "webSecretPass")))
        assertFalse(webUserHashes.contains(MarkensCrypto.hashUserPassword("Aquamys", "wrongPass")))
    }

    @Test
    fun `verify VORTEX default channels and global ideas channel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = MarkensCloudClient(context)
        val vortex = client.defaultVortexGroup()

        assertEquals("VORTEX", vortex.name)
        assertEquals("VX", vortex.shortTag)
        val keys = vortex.textChannels.map { it.key }
        assertTrue(keys.contains("основной-чат"))
        assertTrue(keys.contains("обновления-markens"))
        assertFalse("Steam patchnotes channel must be removed", keys.any { it.contains("steam", ignoreCase = true) })

        val globalIdeas = client.globalIdeasChannel()
        assertEquals("идеи-и-фидбек", globalIdeas.key)

        // Verify VORTEX invitation & membership state for non-owners vs owner
        assertTrue(client.isVortexJoined("markens", isOwner = true))
        client.setVortexJoined("new_friend", false)
        assertFalse(client.isVortexJoined("new_friend", isOwner = false))
        client.setVortexJoined("new_friend", true)
        assertTrue(client.isVortexJoined("new_friend", isOwner = false))
    }

    @Test
    fun `verify session integrity tamper protection, plaintext password scrubbing, avatars, themes, and outbox delivery status`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = MarkensCloudClient(context)
        client.clearSession()
        assertNull(client.loadSavedUser())

        val sampleAvatar = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/"
        val user = MarkensUser(
            name = "markens",
            isOwner = true,
            status = "В сети",
            bio = "Основатель markens",
            badge = "👑",
            level = 1000,
            xp = 100,
            securityToken = "mksig_test",
            avatarUrl = sampleAvatar
        )
        client.saveUserSession(user)
        client.saveRememberedCredentials(user.name, "mySecretPass123", user.badge, user.isOwner, user.avatarUrl)

        val loaded = client.loadSavedUser()
        assertNotNull(loaded)
        assertEquals("markens", loaded?.name)
        assertTrue(loaded?.isOwner == true)
        assertEquals("Основатель markens", loaded?.bio)
        assertEquals("mksig_test", loaded?.securityToken)
        assertEquals(sampleAvatar, loaded?.avatarUrl)
        assertEquals(sampleAvatar, client.getUserAvatar("markens"))

        // Verify remembered login survives logout, while plaintext password is NEVER stored
        client.clearSession()
        assertNull(client.loadSavedUser())
        val remembered = client.getLastRememberedCredentials()
        assertEquals("markens", remembered.first)
        assertEquals("Plaintext password must never be stored or returned", "", remembered.second)

        val expectedVerifier = MarkensCrypto.hashUserPassword("markens", "mySecretPass123")
        val savedAccounts = client.getSavedAccounts()
        assertTrue(
            savedAccounts.any {
                it.login == "markens" &&
                    it.sessionVerifier == expectedVerifier &&
                    it.sessionVerifier != "mySecretPass123" &&
                    it.avatarUrl == sampleAvatar
            }
        )
        client.removeSavedAccount("markens")
        assertFalse(client.getSavedAccounts().any { it.login == "markens" })

        // Verify legacy plaintext passwords in SharedPreferences are automatically scrubbed on startup
        val prefs = context.getSharedPreferences("markens_native_session_v30", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_remembered_password", "legacyPlainPass")
            .putString(
                "saved_accounts_list",
                """[{"login":"alex","password":"plainPassword123","badge":"🎯","isOwner":true}]"""
            )
            .commit()

        val freshClient = MarkensCloudClient(context)
        assertNull("Legacy last_remembered_password key must be removed", prefs.getString("last_remembered_password", null))
        val migratedAccounts = freshClient.getSavedAccounts()
        val migratedAlex = migratedAccounts.find { it.login == "alex" }
        assertNotNull(migratedAlex)
        assertNotEquals("plainPassword123", migratedAlex?.sessionVerifier)
        assertEquals(MarkensCrypto.hashUserPassword("alex", "plainPassword123"), migratedAlex?.sessionVerifier)
        assertFalse("Non-markens account must never retain isOwner=true", migratedAlex?.isOwner == true)

        // Tamper with stored session JSON and verify loadSavedUser rejects it
        client.saveUserSession(user)
        prefs.edit().putString("current_user_json", """{"name":"hacker","isOwner":true}""").commit()
        assertNull("Tampered session must be rejected and cleared", client.loadSavedUser())

        listOf("light", "dark", "system", "amoled").forEach { th ->
            client.saveTheme(th)
            assertEquals(th, client.getSavedTheme())
        }

        client.setAutoUpdateEnabled(false)
        assertFalse(client.getAutoUpdateEnabled())
        client.setAutoUpdateEnabled(true)
        assertTrue(client.getAutoUpdateEnabled())

        assertTrue(client.isStartupSoundEnabled())
        client.setStartupSoundEnabled(false)
        assertFalse(client.isStartupSoundEnabled())
        client.setStartupSoundEnabled(true)
        assertTrue(client.isStartupSoundEnabled())

        client.markVersionInstalled("v30.5", 1799999999999L)
        assertEquals("v30.5", client.getInstalledVersion())
        assertTrue(client.isOtaAlreadyApplied("v30.5", 1799999999999L))

        client.saveLocalFriends("markens", listOf("alex", "dima"))
        val friends = client.getLocalFriends("markens")
        assertEquals(listOf("alex", "dima"), friends)

        // Verify optimistic message starts as SENDING (not falsely marked as SENT before server confirmation)
        val optimisticMsg = client.createOptimisticMessage(
            channelKey = "основной-чат",
            user = user,
            text = "Привет из мобильной версии!",
            isDm = false
        )
        assertNotNull(optimisticMsg)
        assertEquals("Привет из мобильной версии!", optimisticMsg?.text)
        assertEquals(MessageDeliveryStatus.SENDING, optimisticMsg?.deliveryStatus)
        assertEquals(sampleAvatar, optimisticMsg?.senderAvatarUrl)

        val preview = client.getChannelLastPreview("основной-чат")
        assertNotNull(preview)
        assertEquals("Привет из мобильной версии!", preview?.text)

        val reacted = client.toggleReactionOptimistic("основной-чат", optimisticMsg!!, "🔥", "markens")
        assertEquals(listOf("markens"), reacted.reactions["🔥"])
    }

    @Test
    fun `verify PC v31_2_0 PBKDF2 auth vault keys, E2EE v2 DM encryption and WebRTC HMAC signatures`() {
        // 1. Verify deterministic PBKDF2-HMAC-SHA-256 (100,000 iterations) key derivation
        val fixedSalt = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        val keys1 = MarkensCrypto.derivePbkdf2AccountKeys("Markens", "SecretPass!", fixedSalt, 100_000)
        val keys2 = MarkensCrypto.derivePbkdf2AccountKeys("markens", "SecretPass!", fixedSalt, 100_000)
        assertEquals(keys1.salt, keys2.salt)
        assertEquals(keys1.authToken, keys2.authToken)
        assertEquals(keys1.verifierHash, keys2.verifierHash)
        assertEquals(64, keys1.authToken.length)
        assertEquals(64, keys1.verifierHash.length)

        val wrongKeys = MarkensCrypto.derivePbkdf2AccountKeys("markens", "WrongPass!", fixedSalt, 100_000)
        assertNotEquals(keys1.verifierHash, wrongKeys.verifierHash)

        // 2. Verify E2EE v2 DM encryption & decryption (CTR-SHA256 + HMAC-SHA256)
        val dmChannel = "dm_markens_vova"
        val originalText = "Привет, Вова! 🚀 Тестируем сквозное шифрование E2EE v2 между ПК и Android."
        val encrypted = MarkensCrypto.encryptDmPayload(originalText, dmChannel)
        assertTrue("DM payload must be encrypted with e2ee2: prefix", encrypted.startsWith("e2ee2:"))
        assertNotEquals(originalText, encrypted)

        val decrypted = MarkensCrypto.decryptDmPayload(encrypted, dmChannel)
        assertEquals(originalText, decrypted)

        // Verify tampered MAC tag is detected
        val parts = encrypted.split(":")
        assertEquals(4, parts.size)
        val tamperedMac = if (parts[2].startsWith("a")) "b" + parts[2].substring(1) else "a" + parts[2].substring(1)
        val tamperedCipher = "e2ee2:${parts[1]}:$tamperedMac:${parts[3]}"
        val tamperedResult = MarkensCrypto.decryptDmPayload(tamperedCipher, dmChannel)
        assertTrue(tamperedResult.contains("Ошибка криптографической подписи HMAC-SHA-256"))

        // Verify non-DM channel is not encrypted
        val publicText = "Сообщение в общий чат VORTEX"
        assertEquals(publicText, MarkensCrypto.encryptDmPayload(publicText, "основной-чат"))

        // 3. Verify WebRTC signaling HMAC signature
        val sig1 = MarkensCrypto.signWebRtcSignalPayload("markens_vova", "OFFER", "markens", "vova", "v=0...", 1793350000000L)
        val sig2 = MarkensCrypto.signWebRtcSignalPayload("markens_vova", "OFFER", "MARKENS", "VOVA", "v=0...", 1793350000000L)
        assertEquals(32, sig1.length)
        assertEquals(sig1, sig2)
    }

    @Test
    fun `verify OTA security rejects unsigned, forged, downgrade, and cleartext HTTP releases`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = MarkensCloudClient(context)
        client.markVersionInstalled("31.2.0", 1793350000000L)

        val validSig32 = MarkensCrypto.computeOtaReleaseSignature("32.0", 1800000000000L)

        // 1. Reject empty signature
        val unsignedRelease = MarkensOtaRelease(
            version = "32.0",
            buildTs = 1800000000000L,
            changelog = "Unsigned update",
            signature = "",
            autoForce = true,
            confirmedByCreator = true,
            updatedBy = "markens",
            apkUrl = "https://markensmesenger.netlify.app/markens.apk",
            signatureVerified = false
        )
        assertFalse("Unsigned OTA release must be rejected", client.isNewerOtaAvailable(unsignedRelease))

        // 2. Reject forged signature
        val forgedRelease = unsignedRelease.copy(
            signature = "mksig_forged_deadbeef",
            signatureVerified = false
        )
        assertFalse("Forged OTA release must be rejected", client.isNewerOtaAvailable(forgedRelease))

        // 3. Reject cleartext HTTP APK URL
        val httpRelease = unsignedRelease.copy(
            apkUrl = "http://markensmesenger.netlify.app/markens.apk",
            signature = validSig32,
            signatureVerified = true
        )
        assertFalse("Cleartext HTTP APK URL must be rejected", client.isNewerOtaAvailable(httpRelease))

        // 4. Reject version downgrade
        val validSig30 = MarkensCrypto.computeOtaReleaseSignature("30.0", 1850000000000L)
        val downgradeRelease = unsignedRelease.copy(
            version = "30.0",
            buildTs = 1850000000000L,
            apkUrl = "https://markensmesenger.netlify.app/markens.apk",
            signature = validSig30,
            signatureVerified = true
        )
        assertFalse("Downgrade OTA release must be rejected", client.isNewerOtaAvailable(downgradeRelease))

        // 5. Accept properly signed newer HTTPS release
        val validHttpsRelease = unsignedRelease.copy(
            apkUrl = "https://markensmesenger.netlify.app/markens.apk",
            signature = validSig32,
            signatureVerified = true
        )
        assertTrue("Valid signed HTTPS OTA release must be accepted", client.isNewerOtaAvailable(validHttpsRelease))

        // 6. Verify APK file verification rejects missing or hash-mismatched files
        val tempFile = File.createTempFile("fake_update", ".apk", context.cacheDir)
        tempFile.writeText("not a valid signed apk")
        val apkCheck = client.verifyDownloadedApk(
            apkFile = tempFile,
            expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        )
        assertTrue("Tampered APK with wrong SHA-256 must fail verification", apkCheck.isFailure)
        tempFile.delete()
    }

    @Test
    fun `verify MarkensCallManager incoming call state transitions and controls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = MarkensCloudClient(context)
        val callManager = MarkensCallManager(context, client)

        assertNull(callManager.activeCall.value)

        val incomingSession = MarkensCallSession(
            callId = "call_1700000000_alex_markens",
            caller = "alex",
            callee = "markens",
            isVideo = true,
            status = MarkensCallStatus.INCOMING_RINGING,
            startedAtTs = System.currentTimeMillis(),
            isIncoming = true,
            offerSdp = "v=0\r\no=alex 1 2 IN IP4 127.0.0.1\r\n"
        )

        callManager.handleIncomingCallFromCloud(incomingSession, "markens")
        val current = callManager.activeCall.value
        assertNotNull(current)
        assertEquals(MarkensCallStatus.INCOMING_RINGING, current?.status)
        assertEquals("alex", current?.peerName)

        callManager.toggleMuteMic()
        assertEquals(true, callManager.activeCall.value?.isMicMuted)

        callManager.toggleSpeakerphone()
        assertEquals(false, callManager.activeCall.value?.isSpeakerOn)

        callManager.switchCameraFacing()
        assertEquals(false, callManager.activeCall.value?.isFrontCamera)

        callManager.declineOrEndCall("markens")
        assertEquals(MarkensCallStatus.DECLINED, callManager.activeCall.value?.status)

        callManager.dismissEndedCallBanner()
        assertNull(callManager.activeCall.value)
        callManager.destroy()
    }

    @Test
    fun `verify loginOrRegister rejects wrong password and unknown user and supports registration and login`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = MarkensCloudClient(context)

        kotlinx.coroutines.runBlocking {
            // 1. Wrong password for existing Markens account must be rejected
            val badPassRes = client.loginOrRegister("Markens", "definitely_wrong_password_999", isRegister = false)
            assertTrue("Wrong password for Markens must be rejected", badPassRes.isFailure)

            // 2. Non-existent account on Login mode must be rejected
            val ghostLogin = "nonexistent_ghost_${System.currentTimeMillis()}"
            val unknownRes = client.loginOrRegister(ghostLogin, "pass1234", isRegister = false)
            assertTrue("Unknown account on Login must be rejected", unknownRes.isFailure)

            // 3. Registering a temporary test user, logging in with different casing, and cleaning up
            val tempNick = "TestAuth_${System.currentTimeMillis() % 100000}"
            val regRes = client.loginOrRegister(tempNick, "TmpPass42!", isRegister = true)
            if (regRes.isSuccess) {
                val registeredUser = regRes.getOrNull()
                assertNotNull(registeredUser)
                assertEquals(tempNick, registeredUser?.name)
                assertFalse(registeredUser?.isOwner == true)

                // Verify login with lowercase/uppercase variation succeeds with right password
                val loginRes = client.loginOrRegister(tempNick.lowercase(), "TmpPass42!", isRegister = false)
                assertTrue("Case-insensitive login to existing account must succeed", loginRes.isSuccess)
                assertEquals(tempNick, loginRes.getOrNull()?.name)

                // Verify login with wrong password fails
                val wrongTmpRes = client.loginOrRegister(tempNick, "WrongPass000", isRegister = false)
                assertTrue("Wrong password for registered user must fail", wrongTmpRes.isFailure)

                // Clean up temporary test account from RTDB so production database stays clean
                try {
                    okhttp3.OkHttpClient().newCall(
                        okhttp3.Request.Builder()
                            .url("https://markenschatik-default-rtdb.europe-west1.firebasedatabase.app/accounts/$tempNick.json")
                            .delete()
                            .build()
                    ).execute().close()
                } catch (_: Exception) {
                }
            }
        }
    }
}
