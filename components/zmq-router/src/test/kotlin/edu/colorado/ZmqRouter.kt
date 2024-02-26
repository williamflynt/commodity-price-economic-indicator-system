package edu.colorado

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.IndexOutOfBoundsException

class MessageParserTest {

    @Test
    fun `parseMessage should parse message with payload`() {
        val message = "type1 id1 key1=value1 key2=value2"
        val result = parseMessage(message)

        assertEquals("type1", result.type)
        assertEquals("id1", result.id)
        assertEquals(mapOf("key1" to "value1", "key2" to "value2"), result.payload)
    }

    @Test
    fun `parseMessage should parse message without payload`() {
        val message = "type2 id2"
        val result = parseMessage(message)

        assertEquals("type2", result.type)
        assertEquals("id2", result.id)
        assertTrue(result.payload.isEmpty())
    }

    @Test
    fun `parseMessage should throw exception for invalid message`() {
        val message = "type3"
        assertThrows(IndexOutOfBoundsException::class.java) {
            parseMessage(message)
        }
    }
}
