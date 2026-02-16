package Downloads

import Downloads.FileItem
import android.content.Context
import android.net.Uri

class NewFileStore(
    private val context: Context,
    private val treeUri: Uri
) : FileStore {

    override fun listFiles(): List<FileItem> {
        // Exemple : même résultat que Legacy pour garantir la parité.
        // (Tu peux optimiser l'accès, mettre un cache, etc. tant que le résultat reste identique.)
        return LegacyFileStore(context, treeUri).listFiles()
    }

    override fun delete(item: FileItem): DeleteResult {
        // Exemple : instrumentation / telemetry puis délégation.
        // Remplace progressivement par ta nouvelle logique.
        return LegacyFileStore(context, treeUri).delete(item)
    }
}
