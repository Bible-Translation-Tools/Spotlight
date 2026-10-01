package org.bibletranslationtools.glossary.domain

/**
 * A glossary backup is a zipped resource container (RC 0.2), so WA tools
 * built on kotlin-resource-container can load it
 */
object GlossaryArchive {
    const val MANIFEST = "manifest.yaml"
    const val LICENSE = "LICENSE.md"
    const val CONTENT_DIR = "content"
    const val PENDING_DIR = "pending"
    const val PHRASES = "phrases.yaml"

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

    val LICENSE_TEXT = """
        |# Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)
        |
        |This glossary is licensed under the Creative Commons Attribution-ShareAlike 4.0
        |International License. To view a copy of this license, visit
        |https://creativecommons.org/licenses/by-sa/4.0/
        |
        |This is a human-readable summary of (and not a substitute for) the license.
        |
        |## You are free to:
        |
        |* **Share** — copy and redistribute the material in any medium or format.
        |* **Adapt** — remix, transform, and build upon the material for any purpose, even commercially.
        |
        |The licensor cannot revoke these freedoms as long as you follow the license terms.
        |
        |## Under the following conditions:
        |
        |* **Attribution** — You must give appropriate credit, provide a link to the license,
        |  and indicate if changes were made.
        |* **ShareAlike** — If you remix, transform, or build upon the material, you must
        |  distribute your contributions under the same license as the original.
        |* **No additional restrictions** — You may not apply legal terms or technological
        |  measures that legally restrict others from doing anything the license permits.
        |
    """.trimMargin()
}
