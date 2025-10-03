package ru.marslab.ide.ride.model

import java.time.Instant
import java.util.UUID

/**
 * Модель сессии чата (пока в памяти).
 */
 data class ChatSession(
     val id: String = UUID.randomUUID().toString(),
     val title: String = "Session",
     val createdAt: Instant = Instant.now(),
     val updatedAt: Instant = Instant.now(),
 )
