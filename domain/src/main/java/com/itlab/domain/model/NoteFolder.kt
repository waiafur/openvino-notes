package com.itlab.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

data class NoteFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val metadata: Map<String, String> = emptyMap(),
)
