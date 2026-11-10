package com.formready.resizer.data.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ExportRecord(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long
) {
    val isPdf: Boolean get() = mimeType == "application/pdf"

    /** Tool label derived from the file name the exporter used. */
    val label: String
        get() = when {
            name.contains("_signature") -> "Signature"
            name.contains("_sheet") -> "Photo + signature"
            name.contains("_merged") -> "Merged PDF"
            name.contains("_pages") -> "Extracted pages"
            name.contains("_page_") -> "PDF page"
            name.contains("_compressed") -> "Compressed PDF"
            isPdf -> "PDF"
            else -> "Photo"
        }
}

/**
 * A short list of what the user has saved, so the app can show it back to them.
 * Only references are kept — a uri, a name and a size. The files themselves stay
 * in the gallery and Documents where the user can find them without this app.
 */
@Singleton
class ExportHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val MAX_RECORDS = 40
    }

    private val key = stringPreferencesKey("export_history")

    val records: Flow<List<ExportRecord>> = context.historyStore.data.map { prefs ->
        parse(prefs[key])
    }

    suspend fun record(uri: String, name: String, mimeType: String, sizeBytes: Long) {
        context.historyStore.edit { prefs ->
            val current = parse(prefs[key]).filterNot { it.uri == uri }
            val updated = (listOf(
                ExportRecord(uri, name, mimeType, sizeBytes, System.currentTimeMillis())
            ) + current).take(MAX_RECORDS)
            prefs[key] = serialise(updated)
        }
    }

    suspend fun remove(uri: String) {
        context.historyStore.edit { prefs ->
            prefs[key] = serialise(parse(prefs[key]).filterNot { it.uri == uri })
        }
    }

    suspend fun clear() {
        context.historyStore.edit { it[key] = "[]" }
    }

    private fun parse(raw: String?): List<ExportRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                ExportRecord(
                    uri = item.optString("uri"),
                    name = item.optString("name"),
                    mimeType = item.optString("mime", "image/jpeg"),
                    sizeBytes = item.optLong("size"),
                    createdAt = item.optLong("at")
                ).takeIf { it.uri.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun serialise(records: List<ExportRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("uri", record.uri)
                    put("name", record.name)
                    put("mime", record.mimeType)
                    put("size", record.sizeBytes)
                    put("at", record.createdAt)
                }
            )
        }
        return array.toString()
    }
}

private val Context.historyStore by preferencesDataStore(name = "formready_history")
