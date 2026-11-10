package com.formready.resizer.core.storage

import android.content.Intent
import android.net.Uri

/**
 * Plain intent builders. Screens use these directly instead of constructing a FileExporter,
 * which is a repository and belongs in the ViewModel layer.
 */
object ShareIntents {

    fun share(uri: Uri, mimeType: String = "image/jpeg"): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }

    fun share(uris: List<Uri>, mimeType: String = "image/jpeg"): Intent {
        if (uris.size == 1) return share(uris.first(), mimeType)
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }

    /** Opens a saved file in whatever app handles that type. */
    fun open(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
