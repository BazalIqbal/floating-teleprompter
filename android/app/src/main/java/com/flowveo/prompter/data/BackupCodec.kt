package com.flowveo.prompter.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON backup of all scripts.
 * Settings and the API key are never included. Pure, unit-tested.
 */
object BackupCodec {

    private const val APP = "prompter"
    private const val FORMAT = 1

    fun encode(scripts: List<ScriptEntity>, exportedAt: Long): String {
        val arr = JSONArray()
        scripts.forEach { s ->
            arr.put(
                JSONObject()
                    .put("title", s.title)
                    .put("body", s.body)
                    .put("createdAt", s.createdAt)
                    .put("updatedAt", s.updatedAt)
                    .put("lastUsedAt", s.lastUsedAt ?: JSONObject.NULL)
                    .put("mode", s.mode.name)
                    .put("speedWpm", s.speedWpm)
                    .put("fontSizeSp", s.fontSizeSp),
            )
        }
        return JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("exportedAt", exportedAt)
            .put("scripts", arr)
            .toString(2)
    }

    /**
     * Reads a backup. Throws [IllegalArgumentException] if it isn't a Prompter backup;
     * individual broken entries are skipped. Returned scripts have id 0 (new rows).
     */
    fun decode(json: String, now: Long): List<ScriptEntity> {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("This file isn't a Prompter backup.")
        }
        require(root.optString("app") == APP) { "This file isn't a Prompter backup." }
        require(root.optInt("format", -1) in 1..FORMAT) { "This backup was made by a newer version of Prompter." }
        val arr = root.optJSONArray("scripts") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val body = o.optString("body", "")
            val title = o.optString("title", "")
            if (body.isBlank() && title.isBlank()) return@mapNotNull null
            ScriptEntity(
                title = title,
                body = body,
                createdAt = o.optLong("createdAt", now),
                updatedAt = o.optLong("updatedAt", now),
                lastUsedAt = if (o.isNull("lastUsedAt")) null else o.optLong("lastUsedAt"),
                mode = ScrollMode.entries.firstOrNull { it.name == o.optString("mode") } ?: ScrollMode.VOICE,
                speedWpm = o.optInt("speedWpm", 140).coerceIn(AppSettings.WPM_RANGE),
                fontSizeSp = o.optInt("fontSizeSp", 26).coerceIn(AppSettings.FONT_RANGE),
            )
        }
    }
}
