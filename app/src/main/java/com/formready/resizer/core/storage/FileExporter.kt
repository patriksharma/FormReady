package com.formready.resizer.core.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.formready.resizer.data.history.ExportHistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val history: ExportHistoryRepository
) {

    /** Pictures/FormReady via MediaStore — no storage permission needed on API 29+. */
    suspend fun saveImage(bytes: ByteArray, displayName: String): Uri =
        insert(bytes, displayName, "image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES)

    /** Documents/FormReady via MediaStore. */
    suspend fun savePdf(bytes: ByteArray, displayName: String): Uri =
        insert(bytes, displayName, "application/pdf", MediaStore.Files.getContentUri("external"), Environment.DIRECTORY_DOCUMENTS)

    private suspend fun insert(
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
        collection: Uri,
        directory: String
    ): Uri = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/FormReady")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("Could not create the file.")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write the file.")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        history.record(uri.toString(), displayName, mimeType, bytes.size.toLong())
        uri
    }


    /** Cache + FileProvider so the bytes can go out through the system share sheet. */
    suspend fun shareableUri(bytes: ByteArray, displayName: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000 }?.forEach { it.delete() }
        val file = File(dir, displayName)
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Saves a batch of images and returns their MediaStore uris, in order. */
    suspend fun saveImages(pages: List<ByteArray>, prefix: String): List<Uri> =
        pages.mapIndexed { index, bytes ->
            saveImage(bytes, "${prefix}_${"%03d".format(index + 1)}.jpg")
        }

    /** Cache copies for a batch, ready for ACTION_SEND_MULTIPLE. */
    suspend fun shareableUris(pages: List<ByteArray>, prefix: String): List<Uri> =
        pages.mapIndexed { index, bytes ->
            shareableUri(bytes, "${prefix}_${"%03d".format(index + 1)}.jpg")
        }

    fun shareIntent(uri: Uri, mimeType: String = "image/jpeg"): Intent = ShareIntents.share(uri, mimeType)

    fun shareIntent(uris: List<Uri>, mimeType: String = "image/jpeg"): Intent = ShareIntents.share(uris, mimeType)

    suspend fun sizeOf(uri: Uri): Long = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }

    fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    fun timestampedName(prefix: String, extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$stamp.$extension"
    }
}
