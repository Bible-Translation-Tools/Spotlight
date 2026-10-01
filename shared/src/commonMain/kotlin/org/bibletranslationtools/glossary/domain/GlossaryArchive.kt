package org.bibletranslationtools.glossary.domain

/**
 * A glossary backup is a zipped resource container (RC 0.2), so WA tools
 * built on kotlin-resource-container can load it
 */
object GlossaryArchive {
    const val MANIFEST = "manifest.yaml"
    const val LICENSE = "LICENSE.md"
    const val LICENSE_ASSET = "files/glossary/LICENSE.md"
    const val CONTENT_DIR = "content"
    const val PHRASES = "phrases.yaml"
    const val PENDING = "pending_phrases.yaml"

    const val FORMAT_VERSION = 1

    const val CONFORMS_TO = "rc0.2"
    const val TYPE = "dict"
    const val FORMAT = "text/yaml"
    const val IDENTIFIER = "glossary"
    const val SUBJECT = "Glossary"
    const val RIGHTS = "CC BY-SA 4.0"

    fun rootDirName(targetLanguage: String) = "${targetLanguage}_$IDENTIFIER"

    /** manifest.yaml at the zip root, or inside a single top-level RC directory. */
    fun isManifestEntry(entryName: String): Boolean {
        return entryName == MANIFEST ||
                entryName.count { it == '/' } == 1 && entryName.endsWith("/$MANIFEST")
    }

    /** RC directory of a manifest entry: "" at the zip root, otherwise e.g. "es_glossary". */
    fun rootDirOf(manifestEntry: String): String {
        return manifestEntry.removeSuffix(MANIFEST).removeSuffix("/")
    }
}
