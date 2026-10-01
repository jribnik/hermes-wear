package com.hermes.wear.data.network

import com.google.gson.JsonParseException
import com.hermes.wear.data.model.Sender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsesParserTest {

    @Test
    fun `function_call becomes a read-only ran note, never an approval`() {
        val messages = ResponsesParser.parse(
            """{"output":[{"type":"function_call","name":"web_search","call_id":"c1","arguments":"{}"}]}"""
        )
        assertEquals(1, messages.size)
        assertEquals(Sender.SYSTEM, messages[0].sender)
        assertEquals("Hermes ran web_search", messages[0].text)
    }

    @Test
    fun `function_call without a name still gets a note`() {
        val messages = ResponsesParser.parse("""{"output":[{"type":"function_call"}]}""")
        assertEquals("Hermes ran a tool", messages.single().text)
    }

    @Test
    fun `message text parts are joined, blank parts dropped`() {
        val messages = ResponsesParser.parse(
            """{"output":[{"type":"message","content":[
                {"type":"output_text","text":"a"},{"type":"output_text","text":"  "},
                {"type":"output_text","text":"b"}]}]}"""
        )
        assertEquals("a\n\nb", messages.single().text)
        assertEquals(Sender.HERMES, messages.single().sender)
    }

    @Test
    fun `empty, missing or blank output yields no messages`() {
        assertTrue(ResponsesParser.parse("""{"output":[]}""").isEmpty())
        assertTrue(ResponsesParser.parse("""{"id":"r"}""").isEmpty())
        assertTrue(
            ResponsesParser.parse("""{"output":[{"type":"message","content":[{"type":"output_text","text":""}]}]}""")
                .isEmpty()
        )
    }

    @Test(expected = JsonParseException::class)
    fun `non-JSON body throws JsonParseException`() {
        ResponsesParser.parse("not json at all {")
    }

    @Test(expected = JsonParseException::class)
    fun `empty body throws JsonParseException`() {
        ResponsesParser.parse("")
    }

    @Test
    fun `server URL is normalized`() {
        assertEquals("https://h:8080", ServerUrl.normalize("  https://h:8080/  "))
        assertEquals("https://h:8080", ServerUrl.normalize("https://h:8080/v1/responses"))
        assertEquals("https://h", ServerUrl.normalize("https://h/v1/"))
        assertEquals("", ServerUrl.normalize("   "))
    }
}
