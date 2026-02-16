package Downloads

import android.net.Uri

enum class FileScope {
    APP_PRIVATE,
    MEDIASTORE_PUBLIC
}

data class DownloadedFile(
    val id: String,               // stable pour la clé UI (path ou uri.toString)
    val displayName: String,      // nom visible (avec extension)
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModified: Long?,
    val scope: FileScope,
    val uri: Uri?,                // si MediaStore/SAF
    val absolutePath: String?     // si stockage privé app
)
