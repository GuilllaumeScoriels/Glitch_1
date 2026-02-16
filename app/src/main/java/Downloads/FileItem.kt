package Downloads

import android.net.Uri

data class FileItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long?,
    val lastModified: Long?
)

sealed class DeleteResult {
    object Deleted : DeleteResult()
    data class NeedsConsent(val intentSenderRequest: androidx.activity.result.IntentSenderRequest) : DeleteResult()
    data class Failed(val reason: String? = null) : DeleteResult()
}

interface FileStore {
    fun listFiles(): List<FileItem>
    fun delete(item: FileItem): DeleteResult
}
