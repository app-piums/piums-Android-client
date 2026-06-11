package com.piums.cliente.utils

import android.util.Log
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.dto.ChatMessageDto
import dagger.hilt.android.scopes.ActivityRetainedScoped
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatSocketManager @Inject constructor(
    private val tokenStorage: TokenStorage
) {
    private var socket: Socket? = null
    private var activeConversationId: String? = null
    private var hadConnectedBefore = false

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<ChatMessageDto>(extraBufferCapacity = 20)
    val incomingMessages: SharedFlow<ChatMessageDto> = _incomingMessages.asSharedFlow()

    private val _reconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reconnected: SharedFlow<Unit> = _reconnected.asSharedFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _typingConversationId = MutableStateFlow<String?>(null)
    val typingConversationId: StateFlow<String?> = _typingConversationId.asStateFlow()

    private val _pendingDeepLinkConversationId = MutableStateFlow<String?>(null)
    val pendingDeepLinkConversationId: StateFlow<String?> = _pendingDeepLinkConversationId.asStateFlow()

    fun setPendingConversationId(id: String?) { _pendingDeepLinkConversationId.value = id }
    fun clearPendingConversationId() { _pendingDeepLinkConversationId.value = null }

    fun connect() {
        val token = tokenStorage.accessToken ?: return
        if (socket?.connected() == true) return

        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            path = "/socket.io/"
            transports = arrayOf("polling", "websocket")
            reconnection = true
            reconnectionAttempts = 10
            reconnectionDelay = 3000L
            reconnectionDelayMax = 30000L
        }

        runCatching {
            val s = IO.socket(SOCKET_URL, options)
            socket = s

            s.on(Socket.EVENT_CONNECT) {
                if (hadConnectedBefore) _reconnected.tryEmit(Unit)
                hadConnectedBefore = true
                _isConnected.value = true
                activeConversationId?.let { joinConversation(it) }
            }
            s.on(Socket.EVENT_DISCONNECT) {
                _isConnected.value = false
            }
            s.on(Socket.EVENT_CONNECT_ERROR) { /* reconnection handled automatically */ }

            s.on("message:received") { args ->
                val result = parseMessage(args)
                if (result != null) {
                    _incomingMessages.tryEmit(result)
                } else {
                    Log.w("ChatSocket", "message:received parse failed: ${args.firstOrNull()}")
                }
            }
            s.on("typing:start") { args ->
                val obj = args.firstOrNull() as? JSONObject ?: return@on
                _typingConversationId.value = obj.optString("conversationId").takeIf { it.isNotEmpty() }
            }
            s.on("typing:stop") { _ ->
                _typingConversationId.value = null
            }
            s.on("message:read") { /* mark read handled via REST */ }
            s.on("unread:count") { args ->
                val dict = args.firstOrNull() as? JSONObject ?: return@on
                _unreadCount.value = dict.optInt("unreadCount", 0)
            }

            s.connect()
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        _isConnected.value = false
        activeConversationId = null
        hadConnectedBefore = false
    }

    fun joinConversation(conversationId: String) {
        activeConversationId = conversationId
        socket?.emit("conversation:join", JSONObject().put("conversationId", conversationId))
    }

    fun leaveConversation(conversationId: String) {
        if (activeConversationId == conversationId) activeConversationId = null
        socket?.emit("conversation:leave", JSONObject().put("conversationId", conversationId))
    }

    fun markRead(conversationId: String) {
        socket?.emit("conversation:read", JSONObject().put("conversationId", conversationId))
    }

    fun emitTypingStart(conversationId: String) {
        socket?.emit("typing:start", JSONObject().put("conversationId", conversationId))
    }

    fun emitTypingStop(conversationId: String) {
        socket?.emit("typing:stop", JSONObject().put("conversationId", conversationId))
    }

    private fun parseMessage(args: Array<Any>): ChatMessageDto? {
        val obj = args.firstOrNull() as? JSONObject ?: return null
        val msg = if (obj.has("message")) obj.optJSONObject("message") ?: obj else obj
        val id = msg.optString("id").takeIf { it.isNotEmpty() } ?: return null
        val convId = msg.optString("conversationId").takeIf { it.isNotEmpty() } ?: return null
        return ChatMessageDto(
            id             = id,
            conversationId = convId,
            senderId       = msg.optString("senderId"),
            content        = msg.optString("content"),
            status         = msg.optString("status", "SENT"),
            createdAt      = msg.optString("createdAt", "")
        )
    }

    companion object {
        private const val SOCKET_URL = "https://backend.piums.io"
    }
}
