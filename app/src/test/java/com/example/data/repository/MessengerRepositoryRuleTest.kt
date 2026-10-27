package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MessengerRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createChannelAndSendMessage_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = MessengerRepository(firestore)

        val channelResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createChannel(
                name = "#exe-port-test",
                topic = "Testing desktop .exe port channel",
                category = "EXE_SYNC",
                linkedRepo = "JetBrains/kotlin",
                isPinned = true
            )
        }
        assertTrue("Expected channel creation to succeed: ${channelResult.exceptionOrNull()}", channelResult.isSuccess)
        val channelId = channelResult.getOrThrow()

        val messageResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.sendMessage(
                channelId = channelId,
                text = "Hello from Android port!",
                messageType = "CODE_SNIPPET",
                codeLanguage = "kotlin",
                codeSnippet = "println(\"Ported!\")"
            )
        }
        assertTrue("Expected message creation to succeed: ${messageResult.exceptionOrNull()}", messageResult.isSuccess)
        val messageId = messageResult.getOrThrow()

        val emittedMessages = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMessages(aliceUid, channelId).first { list -> list.any { it.id == messageId } }
        }
        assertTrue(emittedMessages.any { it.id == messageId && it.text == "Hello from Android port!" })
    }

    @Test
    fun getChannelById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = MessengerRepository(firestore)
        val channelId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createChannel(
                name = "#alice-private",
                topic = "Alice only channel"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = MessengerRepository(firestore)
        val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getChannelById(channelId)
        }
        assertTrue("Expected cross-user read to fail", bobReadResult.isFailure)
        val firestoreEx = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeChannels_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = MessengerRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeChannels("unauth_uid_123").first()
            }
            fail("Expected observeChannels to fail when unauthenticated")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@gitpulse.test"
        const val BOB_EMAIL = "bob@gitpulse.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
