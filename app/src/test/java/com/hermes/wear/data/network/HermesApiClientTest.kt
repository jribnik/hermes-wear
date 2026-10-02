package com.hermes.wear.data.network

import com.google.gson.JsonParser
import com.google.gson.JsonParseException
import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.data.model.Sender
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HermesApiClientTest {

    private val config = ServerConfig("https://hermes.example.com/", "secret-key")

    @Test
    fun `request body carries model, input and the conversation id`() = runBlocking {
        val server = FakeServer()
        HermesApiClient(server.client).send(config, "hermes-wear-abc", "hello")

        val request = server.requests.single()
        assertEquals("https://hermes.example.com/v1/responses", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals("Bearer secret-key", request.header("Authorization"))

        val json = JsonParser.parseString(server.bodies.single()).asJsonObject
        assertEquals("hermes-agent", json["model"].asString)
        assertEquals("hello", json["input"].asString)
        assertEquals("hermes-wear-abc", json["conversation"].asString)
        assertEquals(setOf("model", "input", "conversation"), json.keySet())
    }

    @Test
    fun `successful response is parsed into tool notes and the reply`() = runBlocking {
        val server = FakeServer(respond = { 200 to GATEWAY_RESPONSE })
        val result = HermesApiClient(server.client).send(config, "c", "hi")

        val messages = result.getOrThrow()
        assertEquals(listOf(Sender.SYSTEM, Sender.HERMES), messages.map { it.sender })
        assertEquals("Hermes ran terminal", messages[0].text)
        assertEquals("Part one\n\nPart two", messages[1].text)
    }

    @Test
    fun `non-2xx status is a failure with the status code`() = runBlocking {
        val server = FakeServer(respond = { 500 to """{"error":{"message":"boom"}}""" })
        val error = HermesApiClient(server.client).send(config, "c", "hi").exceptionOrNull()

        assertTrue(error is HermesHttpException)
        assertEquals(500, (error as HermesHttpException).code)
        assertTrue(error.message!!.contains("boom"))
    }

    @Test
    fun `401 is reported as a rejected key`() = runBlocking {
        val server = FakeServer(respond = { 401 to "" })
        val error = HermesApiClient(server.client).send(config, "c", "hi").exceptionOrNull()

        assertEquals(401, (error as HermesHttpException).code)
        assertTrue(error.message!!.contains("API key rejected"))
    }

    @Test
    fun `malformed JSON is a failure, not a crash`() = runBlocking {
        val server = FakeServer(respond = { 200 to "<html>tunnel error</html>" })
        val error = HermesApiClient(server.client).send(config, "c", "hi").exceptionOrNull()

        assertTrue(error is JsonParseException)
    }

    @Test
    fun `network error is a failure`() = runBlocking {
        val server = FakeServer(respond = { throw IOException("no route") })
        val error = HermesApiClient(server.client).send(config, "c", "hi").exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun `health check hits v1 models with the key and maps statuses`() = runBlocking {
        val server = FakeServer()
        val client = HermesApiClient(server.client)

        server.respond = { 200 to """{"object":"list","data":[]}""" }
        assertEquals(ConnectionStatus.CONNECTED, client.checkHealth(config))
        assertEquals("https://hermes.example.com/v1/models", server.requests.last().url.toString())
        assertEquals("Bearer secret-key", server.requests.last().header("Authorization"))

        server.respond = { 401 to "" }
        assertEquals(ConnectionStatus.KEY_REJECTED, client.checkHealth(config))

        server.respond = { 502 to "" }
        assertEquals(ConnectionStatus.UNREACHABLE, client.checkHealth(config))

        server.respond = { throw IOException("down") }
        assertEquals(ConnectionStatus.UNREACHABLE, client.checkHealth(config))

        assertEquals(ConnectionStatus.NOT_CONFIGURED, client.checkHealth(ServerConfig("", "k")))
    }

    companion object {
        /** Shape of a real non-streaming gateway response (see api_server_openai_routes.py). */
        const val GATEWAY_RESPONSE = """
            {"id":"resp_1","object":"response","status":"completed","output":[
              {"id":"rs_1","type":"reasoning","status":"completed",
               "summary":[{"type":"summary_text","text":"thinking..."}]},
              {"id":"fc_1","type":"function_call","status":"completed","name":"terminal",
               "arguments":"{\"command\":\"ls\"}","call_id":"call_1"},
              {"id":"fco_1","type":"function_call_output","status":"completed","call_id":"call_1",
               "output":"file.txt"},
              {"type":"message","role":"assistant","content":[
                {"type":"output_text","text":"Part one"},
                {"type":"refusal","text":"ignored"},
                {"type":"output_text","text":"Part two"}]}
            ]}"""
    }
}
