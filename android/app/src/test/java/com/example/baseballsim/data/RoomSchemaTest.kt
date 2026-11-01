package com.example.baseballsim.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RoomSchemaTest {
    @Test fun generatedRoomSchemaMatchesTheTrackedSchema() {
        val tracked = File("schemas/com.example.baseballsim.data.PennantDatabase/1.json")
        val generated = File("build/reports/room-schemas/com.example.baseballsim.data.PennantDatabase/1.json")
        assertTrue("Tracked schema is required", tracked.isFile)
        assertTrue("The current compiler must export the schema", generated.isFile)
        assertArrayEquals(tracked.readBytes(), generated.readBytes())
    }
    @Test fun bothBackupRuleFormatsExcludeThePrototypeDatabase() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("@xml/pennant_backup_rules"))
        assertTrue(manifest.contains("@xml/pennant_data_extraction_rules"))
        val legacy = File("src/main/res/xml/pennant_backup_rules.xml").readText()
        val modern = File("src/main/res/xml/pennant_data_extraction_rules.xml").readText()
        assertTrue(legacy.contains("domain=\"database\" path=\".\""))
        assertTrue(modern.contains("<cloud-backup>")); assertTrue(modern.contains("<device-transfer>"))
        assertEquals(2, Regex("domain=\"database\" path=\".\"").findAll(modern).count())
    }
}
