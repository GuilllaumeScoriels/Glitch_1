package Downloads

import Downloads.DeleteResult
import Downloads.FileItem
import Downloads.FileStore
import android.content.Context
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.documentfile.provider.DocumentFile
import android.os.Build
import android.provider.MediaStore

class LegacyFileStore(
    private val context: Context,
    private val treeUri: Uri
) : FileStore {

    override fun listFiles(): List<FileItem> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return root.listFiles()
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
            .map { df ->
                FileItem(
                    uri = df.uri,
                    name = df.name ?: "Sans nom",
                    sizeBytes = runCatching { df.length() }.getOrNull(),
                    lastModified = runCatching { df.lastModified() }.getOrNull()
                )
            }
    }

    override fun delete(item: FileItem): DeleteResult {
        val df = DocumentFile.fromSingleUri(context, item.uri)
        if (df != null && runCatching { df.delete() }.getOrDefault(false)) {
            return DeleteResult.Deleted
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
                DeleteResult.NeedsConsent(IntentSenderRequest.Builder(pi.intentSender).build())
            }.getOrElse { e -> DeleteResult.Failed(e.message) }
        } else {
            DeleteResult.Failed("Suppression refusée par le système.")
        }
    }
}
