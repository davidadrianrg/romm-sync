package com.davidadrianrg.atril.core.localgames

/** Deduce un título legible a partir del nombre de fichero de una ROM. */
object LocalGameNames {

    private val extension = Regex("""\.[A-Za-z0-9]{1,5}$""")
    private val tags = Regex("""\s*[\(\[][^)\]]*[\)\]]""")
    private val separators = Regex("""[_]+""")
    private val spaces = Regex("""\s{2,}""")

    fun titleFromFileName(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
            .replace(extension, "")
            .replace(separators, " ")
            .replace(tags, "")
            .replace(spaces, " ")
            .trim(' ', '-', '.')
        return base.ifBlank { fileName.substringBeforeLast('.').ifBlank { fileName } }
    }
}
