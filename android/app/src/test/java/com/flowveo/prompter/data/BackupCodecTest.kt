package com.flowveo.prompter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    private val a = ScriptEntity(
        id = 7, title = "First", body = "Hello there. //", createdAt = 1, updatedAt = 2,
        lastUsedAt = 3, mode = ScrollMode.AUTO, speedWpm = 160, fontSizeSp = 30,
    )
    private val b = a.copy(id = 8, title = "Second", body = "Bye.", lastUsedAt = null, mode = ScrollMode.VOICE)

    @Test
    fun roundTripsScripts() {
        val back = BackupCodec.decode(BackupCodec.encode(listOf(a, b), exportedAt = 99), now = 1000)
        assertEquals(2, back.size)
        assertEquals(a.copy(id = 0), back[0])
        assertEquals(b.copy(id = 0), back[1])
        assertNull(back[1].lastUsedAt)
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("not json", 0) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("""{"app":"other","format":1}""", 0) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("""{"app":"prompter","format":9}""", 0) }
    }

    @Test
    fun skipsBrokenEntriesAndFillsDefaults() {
        val json = """{"app":"prompter","format":1,"scripts":[{"title":"","body":""},"junk",{"body":"Only body","speedWpm":9999}]}"""
        val back = BackupCodec.decode(json, now = 5)
        assertEquals(1, back.size)
        assertEquals("Only body", back[0].body)
        assertEquals(250, back[0].speedWpm)
        assertEquals(5, back[0].createdAt)
        assertEquals(ScrollMode.VOICE, back[0].mode)
    }
}
