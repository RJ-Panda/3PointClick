package com.threepointclick

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class EventSender(
    private var host: String = "127.0.0.1",
    private var port: Int = 8888,
    private val listener: ConnectionListener? = null
) {
    interface ConnectionListener {
        fun onConnecting(host: String, port: Int)
        fun onConnected(host: String, port: Int)
        fun onDisconnected()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private var pingJob: Job? = null

    // Queue for outgoing JSON lines. Dropping oldest MOVE events on capacity prevents lag build-up.
    private val eventChannel = Channel<String>(capacity = 64)

    @Volatile
    var isConnected: Boolean = false
        private set

    fun updateConfig(newHost: String, newPort: Int) {
        if (newHost != host || newPort != port) {
            host = newHost
            port = newPort
            restart()
        }
    }

    fun start() {
        if (job != null) return
        job = scope.launch {
            connectionLoop()
        }
    }

    fun stop() {
        pingJob?.cancel()
        job?.cancel()
        job = null
        isConnected = false
    }

    fun restart() {
        stop()
        start()
    }

    fun sendMove(dx: Int, dy: Int) {
        if (!isConnected || (dx == 0 && dy == 0)) return
        val json = "{\"type\":\"MOVE\",\"dx\":$dx,\"dy\":$dy}"
        // trySend won't block UI; if buffer overflows, drops oldest to keep real-time latency
        eventChannel.trySend(json)
    }

    fun sendLeftClick(action: String) { // "DOWN" or "UP"
        val json = "{\"type\":\"LEFT\",\"action\":\"$action\"}"
        scope.launch {
            eventChannel.send(json)
        }
    }

    fun sendRightClick(action: String) { // "DOWN" or "UP"
        val json = "{\"type\":\"RIGHT\",\"action\":\"$action\"}"
        scope.launch {
            eventChannel.send(json)
        }
    }

    private suspend fun connectionLoop() {
        while (scope.isActive) {
            var socket: Socket? = null
            var writer: BufferedWriter? = null
            try {
                withContext(Dispatchers.Main) {
                    listener?.onConnecting(host, port)
                }

                socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), 2500)

                writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
                isConnected = true

                withContext(Dispatchers.Main) {
                    listener?.onConnected(host, port)
                }

                // Launch periodic PING every 2 seconds
                pingJob = scope.launch {
                    while (isActive && isConnected) {
                        delay(2000)
                        val pingJson = "{\"type\":\"PING\"}"
                        eventChannel.trySend(pingJson)
                    }
                }

                // Event consumer loop
                while (scope.isActive && isConnected) {
                    val line = eventChannel.receive()
                    writer.write(line)
                    writer.write("\n")
                    writer.flush()
                }

            } catch (e: Exception) {
                Log.d("EventSender", "Socket connection error: ${e.message}")
            } finally {
                isConnected = false
                pingJob?.cancel()
                try {
                    writer?.close()
                } catch (_: Exception) {}
                try {
                    socket?.close()
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    listener?.onDisconnected()
                }

                // Reconnect delay
                delay(3000)
            }
        }
    }
}
