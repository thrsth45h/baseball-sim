package com.example.baseballsim

import android.content.Context
import android.database.Cursor
import com.example.baseballsim.data.PennantDatabase

/** Same six-table/full-BLOB comparison as the legacy browser test, at quiescent boundaries only. */
internal fun targetPennantDatabaseRows(context: Context): Map<String, List<List<String>>> {
    val db = PennantDatabase.open(context.applicationContext)
    return try {
        listOf("working_state", "completed_results", "manual_slot", "operation_receipts", "blocked_diagnostics", "generation_registry")
            .associateWith { table -> db.openHelper.readableDatabase.query("SELECT * FROM $table").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add((0 until cursor.columnCount).map { column -> when (cursor.getType(column)) {
                        Cursor.FIELD_TYPE_NULL -> "null"
                        Cursor.FIELD_TYPE_BLOB -> "blob:" + cursor.getBlob(column).joinToString("") { "%02x".format(it) }
                        Cursor.FIELD_TYPE_INTEGER -> "int:" + cursor.getLong(column)
                        Cursor.FIELD_TYPE_FLOAT -> "float:" + cursor.getDouble(column)
                        else -> "text:" + cursor.getString(column)
                    } })
                }.sortedBy { it.joinToString("\u0000") }
            } }
    } finally { db.close() }
}
