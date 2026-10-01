package org.bibletranslationtools.glossary.domain

/**
 * Layout of a glossary backup zip:
 *
 * ```
 * manifest.yml   glossary info (code, languages, version, resource)
 * content.yml    approved phrases, sorted by phrase
 * pending.yml    pending phrases, sorted by phrase
 * en_ulb.zip     source text resource container
 * ```
 */
object GlossaryArchive {
    const val MANIFEST = "manifest.yml"
    const val CONTENT = "content.yml"
    const val PENDING = "pending.yml"
}
