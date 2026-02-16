package Downloads

/** Conserve l’extension du nom source si l’utilisateur n’en fournit pas. */
fun String.withKeptExtensionFrom(sourceName: String): String {
    if (this.contains('.')) return this
    val ext = sourceName.substringAfterLast('.', missingDelimiterValue = "")
    return if (ext.isNotEmpty()) "$this.$ext" else this
}

/** Nettoie un nom de fichier (caractères interdits, espaces, longueur). */
fun String.sanitizeFileName(): String =
    this.replace(Regex("[\\n\\r\\t/]"), " ")
        .trim()
        .take(255)
