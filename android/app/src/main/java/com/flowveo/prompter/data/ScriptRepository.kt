package com.flowveo.prompter.data

import com.flowveo.prompter.scroll.SampleScript
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

data class ImportResult(val added: Int, val skipped: Int)

/** All script reads and writes go through here. Scripts live only on the phone. */
@Singleton
class ScriptRepository @Inject constructor(
    private val dao: ScriptDao,
    private val settings: SettingsRepository,
) {
    fun observeAll(): Flow<List<ScriptEntity>> = dao.observeAll()

    fun observe(id: Long): Flow<ScriptEntity?> = dao.observe(id)

    suspend fun get(id: Long): ScriptEntity? = dao.get(id)

    /** New script with the current default mode, speed and font size. */
    suspend fun create(title: String? = null, body: String = ""): Long {
        val s = settings.current()
        val now = System.currentTimeMillis()
        return dao.insert(
            ScriptEntity(
                title = if (title == null && body.isBlank()) "" else ScriptStats.titleFor(title, body),
                body = body,
                createdAt = now,
                updatedAt = now,
                mode = s.defaultMode,
                speedWpm = s.autoWpm,
                fontSizeSp = s.fontSizeSp,
            ),
        )
    }

    suspend fun save(script: ScriptEntity) = dao.update(script.copy(updatedAt = System.currentTimeMillis()))

    suspend fun duplicate(id: Long): Long? {
        val s = dao.get(id) ?: return null
        val now = System.currentTimeMillis()
        return dao.insert(s.copy(id = 0, title = "${s.title} (copy)", createdAt = now, updatedAt = now, lastUsedAt = null))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun markUsed(id: Long) = dao.markUsed(id, System.currentTimeMillis())

    /** All scripts as a JSON backup. */
    suspend fun exportJson(): String = BackupCodec.encode(dao.getAll(), System.currentTimeMillis())

    /** Restores a backup; scripts already on the phone (same title and text) are skipped. */
    suspend fun importJson(json: String): ImportResult {
        val incoming = BackupCodec.decode(json, System.currentTimeMillis())
        val existing = dao.getAll().map { it.title to it.body }.toHashSet()
        var added = 0
        incoming.forEach { s ->
            if (existing.add(s.title to s.body)) {
                dao.insert(s)
                added++
            }
        }
        return ImportResult(added = added, skipped = incoming.size - added)
    }

    /** On the very first launch, add the sample script so the library isn't empty. */
    suspend fun seedSampleIfFirstRun() {
        if (settings.consumeFirstRun() && dao.count() == 0) {
            create(SampleScript.TITLE, SampleScript.TEXT)
        }
    }
}
