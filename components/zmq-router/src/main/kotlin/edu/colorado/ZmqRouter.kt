package edu.colorado

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ

/**
 * Messages:
 *
 * COLLECT
 * COLLECT-START
 * COLLECT-DONE
 *
 * ANALYZE
 * ANALYZE-START
 * ANALYZE-DONE
 */

@Serializable
data class Message(val type: String, val id: String, val payload: Map<String, String>)

class ZmqRouter(private val socketPort: Int) {
    private val context = ZContext()
    private val zmq = context.createSocket(SocketType.PAIR)

    init {
        zmq.bind("tcp://*:$socketPort")
    }

    private val channels = mutableMapOf<String, MutableList<Channel<Message>>>()
    val fullStream = Channel<Message>(Channel.CONFLATED)

    /**
     * type - A message "topic" / event type
     * channel - Channel to get events on with a Message payload type.
     */
    fun registerChannel(type: String, channel: Channel<Message>): Channel<Message> {
        channels.getOrPut(type) { mutableListOf() }.add(channel)
        return channel
    }

    fun publish(event: String) {
        zmq.send(event, ZMQ.NOBLOCK)
    }

    fun startListening() = GlobalScope.launch {
        val listenZmq = context.createSocket(SocketType.PAIR)
        listenZmq.connect("tcp://*:$socketPort")

        while (isActive) {
            val message = listenZmq.recvStr()
            val parsedMessage = parseMessage(message)
            // Full stream always.
            fullStream.send(parsedMessage)
            // Registered listeners.
            val channelList = channels[parsedMessage.type]
            channelList?.forEach { channel: Channel<Message> ->
                channel.send(parsedMessage)
            }
        }
    }
}

fun parseMessage(message: String): Message {
    val parts = message.split(" ")
    val type = parts[0]
    val id = parts[1]
    val payload = parts.drop(2).map {
        val (key, value) = it.split("=")
        key to value
    }.toMap()

    return Message(type, id, payload)
}