package com.hermes.wear.data.repository

import com.google.gson.JsonParser
import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.data.model.MessageStatus
import com.hermes.wear.data.model.Sender
import com.hermes.wear.data.network.FakeServer
import com.hermes.wear.data.network.HermesApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private class FakeSettings(
    override var serverUrl: String = "https://hermes.example.com",
    override var apiKey: String = "k",
) : HermesSettings {
    private var id = "hermes-wear-1"
    private var counter = 1
    override val conversationId: String get() = id
    override fun rotateConversationId(): String = "hermes-wear-${++counter}".also { id = it }
}

class HermesRepositoryTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun conversationOf(body: String) = JsonParser.parseString(body).asJsonObject["conversation"].asString

    @Test
    fun `reply is appended and user message marked sent`() = runBlocking {
        val server = FakeServer(respond = {
            200 to """{"output":[{"type":"message","content":[{"type":"output_text","text":"hi there"}]}]}"""
        })
        val repo = HermesRepository(HermesApiClient(server.client), FakeSettings(), scope)

        repo.sendMessage("hello")!!.join()

        val messages = repo.messages.value
        assertEquals(listOf(Sender.USER, Sender.HERMES), messages.map { it.sender })
        assertEquals(MessageStatus.SENT, messages[0].status)
        assertEquals("hi there", messages[1].text)
        assertEquals(ConnectionStatus.CONNECTED, repo.connectionStatus.value)
        assertFalse(repo.isSending.value)
    }

    @Test
    fun `turns share one conversation id until a new conversation is started`() = runBlocking {
        val server = FakeServer()
        val repo = HermesRepository(HermesApiClient(server.client), FakeSettings(), scope)

        repo.sendMessage("one")!!.join()
        repo.sendMessage("two")!!.join()
        repo.startNewConversation()
        repo.sendMessage("three")!!.join()

        val ids = server.bodies.map(::conversationOf)
        assertEquals(ids[0], ids[1])
        assertNotEquals(ids[1], ids[2])
        assertEquals(1, repo.messages.value.size) // history cleared before "three"
    }

    @Test
    fun `second send while one is in flight is ignored`() = runBlocking {
        val release = CountDownLatch(1)
        val server = FakeServer(respond = {
            release.await(5, TimeUnit.SECONDS)
            200 to """{"output":[]}"""
        })
        val repo = HermesRepository(HermesApiClient(server.client), FakeSettings(), scope)

        val first = repo.sendMessage("first")!!
        assertNull(repo.sendMessage("double tap"))
        release.countDown()
        first.join()

        assertEquals(1, server.requests.size)
        assertEquals(listOf("first"), repo.messages.value.map { it.text })
    }

    @Test
    fun `failure marks the message and keeps the error until cleared`() = runBlocking {
        val server = FakeServer(respond = { 401 to "" })
        val repo = HermesRepository(HermesApiClient(server.client), FakeSettings(), scope)

        repo.sendMessage("hello")!!.join()

        assertEquals(MessageStatus.ERROR, repo.messages.value.single().status)
        assertEquals(ConnectionStatus.KEY_REJECTED, repo.connectionStatus.value)
        assert(repo.error.value!!.contains("401"))
        repo.clearError()
        assertNull(repo.error.value)
    }

    @Test
    fun `cancel aborts the in-flight send`() = runBlocking {
        val started = CountDownLatch(1)
        val server = FakeServer(respond = {
            started.countDown()
            Thread.sleep(2_000)
            200 to """{"output":[{"type":"message","content":[{"type":"output_text","text":"late"}]}]}"""
        })
        val repo = HermesRepository(HermesApiClient(server.client), FakeSettings(), scope)

        val job = repo.sendMessage("hello")!!
        started.await(5, TimeUnit.SECONDS)
        repo.cancelSend()
        job.join()

        assertEquals(MessageStatus.ERROR, repo.messages.value.single().status)
        assertEquals("Cancelled", repo.error.value)
        assertFalse(repo.isSending.value)
    }

    @Test
    fun `no server URL means nothing is sent`() {
        val server = FakeServer()
        val repo = HermesRepository(HermesApiClient(server.client), FakeSettings(serverUrl = ""), scope)

        assertNull(repo.sendMessage("hello"))
        assertEquals(0, server.requests.size)
        assertEquals(ConnectionStatus.NOT_CONFIGURED, repo.connectionStatus.value)
    }
}
