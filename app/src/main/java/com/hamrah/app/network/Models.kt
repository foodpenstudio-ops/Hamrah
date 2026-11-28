package com.hamrah.app.network

data class DiscoveredDevice(val endpointId: String, val name: String)

data class PendingRequest(
    val endpointId: String,
    val name: String,
    val code: String,
    val incoming: Boolean
)

data class ConnectedDevice(val endpointId: String, val name: String)

enum class MessageStatus { PENDING, DELIVERED, FAILED, RECEIVED }

data class ChatMessage(
    val id: String,
    val endpointId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val mine: Boolean,
    val status: MessageStatus,
    val peerName: String = ""
)

data class EmergencyAlert(
    val id: String,
    val endpointId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val mine: Boolean,
    val cancelled: Boolean = false,
    val acknowledgedBy: List<String> = emptyList(),
    val acknowledgedByMe: Boolean = false
)

data class SharedLocation(
    val senderName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long
)