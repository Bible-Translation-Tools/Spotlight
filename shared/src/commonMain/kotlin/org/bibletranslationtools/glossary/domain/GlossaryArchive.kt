package org.bibletranslationtools.glossary.domain

import kotlinx.io.files.Path

/**
 * A glossary backup is a zipped resource container (RC 0.2)
 * Paths below are relative to the RC directory and use '/', as in zip entries.
 */
object GlossaryArchive {
    const val MANIFEST = "manifest.yaml"
    const val LICENSE = "LICENSE.md"
    const val LICENSE_ASSET = "files/glossary/LICENSE.md"
    const val CONTENT_DIR = "content"
    const val PHRASES = "$CONTENT_DIR/phrases.yaml"

    const val APP_DIR = ".apps/spotlight"
    const val GLOSSARY = "$APP_DIR/glossary.yaml"
    const val PENDING = "$APP_DIR/pending_phrases.yaml"
    const val SOURCE_DIR = "$APP_DIR/source"

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

    /** Zip entry name of [path] inside the RC directory [rootDir]. */
    fun entryName(rootDir: String, path: String): String {
        return if (rootDir.isEmpty()) path else "$rootDir/$path"
    }

    /** File at [path] inside the extracted RC directory [rootDir]. */
    fun file(rootDir: Path, path: String): Path {
        return Path(rootDir, *path.split('/').toTypedArray())
    }
}
