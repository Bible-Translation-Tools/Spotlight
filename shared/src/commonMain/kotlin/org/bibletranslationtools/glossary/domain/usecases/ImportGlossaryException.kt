package org.bibletranslationtools.glossary.domain.usecases

/**
 * Why a glossary backup can't be imported. The type tells the user what to do,
 * the message says exactly what is wrong, for logs.
 */
sealed class ImportGlossaryException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause) {

    /** Not a glossary backup, or a damaged one. */
    class InvalidBackup(message: String, cause: Throwable? = null) :
        ImportGlossaryException(message, cause)

    /** Made by a newer app version, in a format this one can't read. */
    class NewerFormat(val formatVersion: Int, supportedVersion: Int) :
        ImportGlossaryException(
            "Glossary format $formatVersion is newer than supported $supportedVersion, update the app"
        )

    /** The source text container is missing or can't be read. */
    class SourceText(message: String) : ImportGlossaryException(message)

    /** A glossary language this app doesn't know. */
    class UnknownLanguage(val language: String, message: String) :
        ImportGlossaryException(message)
}
