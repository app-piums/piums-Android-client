package com.piums.cliente.data.remote.dto

import com.google.gson.annotations.SerializedName

// Fix crítico: backend usa participant1Id/participant2Id, NO userId/artistId
// Fix: backend devuelve el nombre del otro participante como "clientName"/"clientAvatar"
// Fix: backend devuelve preview del último mensaje como "lastMessagePreview"
data class ConversationDto(
    val id: String,
    @SerializedName("participant1Id") val userId: String,
    @SerializedName("participant2Id") val artistId: String,
    val bookingId: String?,
    val status: String = "ACTIVE",
    val lastMessageAt: String?,
    @SerializedName("lastMessagePreview") val lastMessageContent: String? = null,
    val unreadCount: Int = 0,
    val messages: List<ChatMessageDto>? = null,
    @SerializedName("clientName") val artistName: String?,
    @SerializedName("clientAvatar") val artistAvatar: String?
)

data class ConversationsResponse(
    val conversations: List<ConversationDto>?,
    val data: List<ConversationDto>?
) {
    val list: List<ConversationDto> get() = conversations ?: data ?: emptyList()
}

// Fix crítico: backend envía status string, no read: Boolean; no hay senderType
data class ChatMessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val status: String,   // "SENT" | "DELIVERED" | "READ"
    val createdAt: String
) {
    val isRead: Boolean get() = status == "READ"
}

data class MessagesResponse(
    val messages: List<ChatMessageDto>?,
    val data: List<ChatMessageDto>?
) {
    val list: List<ChatMessageDto> get() = messages ?: data ?: emptyList()
}

data class SendMessageRequest(
    val conversationId: String,
    val content: String,
    val type: String = "TEXT"
)

data class UnreadCountResponse(val count: Int = 0, val unreadCount: Int = 0) {
    val total: Int get() = maxOf(count, unreadCount)
}
