package com.flowveo.prompter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** How the overlay moves the script. Voice is the default; Auto is the fallback. */
enum class ScrollMode { VOICE, AUTO }

/** One script. Each script remembers its own mode, speed and font size. */
@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Full text; `//` is a pause marker. */
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Null until the script has been prompted once. */
    val lastUsedAt: Long? = null,
    val mode: ScrollMode,
    val speedWpm: Int,
    val fontSizeSp: Int,
    /** Word index for resuming (not used yet). */
    val lastPosition: Int = 0,
)
