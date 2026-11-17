package com.ankit.phonemirror

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MirrorWebSocketClient(
    private val serverUrl: String,
    private val roomCode: String,
    private val role: String, // "host" or "viewer"
    private val onStatus: (String) -> Unit,
    private val onFrameReceived: ((ByteArray) -> Unit)? = null,
    private val onCommandReceived: ((JSONObject) -> Unit)? = null
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null

    fun connect() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val joinMsg = JSONObject().apply {
                    put("type", "join")
                    put("room", roomCode)
                    put("role", role)
                }
                webSocket.send(joinMsg.toString())
                onStatus("Connected ($role) in room $roomCode")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onFrameReceived?.invoke(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    onCommandReceived?.invoke(json)
                } catch (_: Exception) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStatus("Connection error: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStatus("Disconnected")
            }
        })
    }

    fun sendFrame(jpegBytes: ByteArray) {
        webSocket?.send(jpegBytes.toByteString(0, jpegBytes.size))
    }

    fun sendCommand(json: JSONObject) {
        webSocket?.send(json.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "User stopped")
        webSocket = null
    }
}
