package org.bibletranslationtools.glossary.domain.usecases

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.io.files.Path
import org.bibletranslationtools.glossary.Utils
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.api.ManifestGlossary
import org.bibletranslationtools.glossary.data.api.ManifestPhrase
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.GlossaryArchive
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.logE
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.toLocalDateTime
import org.wycliffeassociates.resourcecontainer.entity.Manifest
import org.wycliffeassociates.resourcecontainer.entity.Source

class ImportGlossary(
    private val glossaryRepository: GlossaryRepository,
    private val fileSystemProvider: FileSystemProvider,
    private val resourceContainerAccessor: ResourceContainerAccessor
) {
    data class Result(
        val glossary: Glossary,
        val resource: Resource
    )

    private data class Backup(
        val manifest: Manifest,
        val glossary: ManifestGlossary,
        val phrases: List<ManifestPhrase>,
        val pendingPhrases: List<ManifestPhrase>
    )

    /**
     * Returns the glossary already stored on the device that importing
     * [file] would overwrite, or null if there is none.
     *
     * @throws ImportGlossaryException if [file] is in a format this app can't import
     */
    suspend fun findExisting(file: PlatformFile): Glossary? {
        val manifestYaml = fileSystemProvider.readZipEntry(file, GlossaryArchive.MANIFEST)
            ?: return null
        val glossaryYaml = fileSystemProvider.readZipEntry(file, GlossaryArchive.GLOSSARY)
            ?: return null

        val manifest = parseYaml<Manifest>(manifestYaml, GlossaryArchive.MANIFEST)
        val manifestGlossary = parseYaml<ManifestGlossary>(glossaryYaml, GlossaryArchive.GLOSSARY)
        checkFormat(manifestGlossary)

        return glossaryRepository.getGlossaries().firstOrNull {
            it.code == manifestGlossary.code &&
                    it.sourceLanguage.slug == manifest.source.language &&
                    it.targetLanguage.slug == manifest.dublinCore.language.identifier
        }
    }

    suspend operator fun invoke(file: PlatformFile): Result {

        val tempDir = fileSystemProvider.createTempDir("glossary")
        fileSystemProvider.extractZip(file, tempDir)

        val backup = readBackup(tempDir)
        val source = backup.manifest.source

        val resourceId = "${source.language}_${source.identifier}"
        val resourceFile = GlossaryArchive.file(tempDir, "${GlossaryArchive.SOURCE_DIR}/$resourceId.zip")

        if (!fileSystemProvider.exists(resourceFile)) {
            throw ImportGlossaryException.SourceText("$resourceId.zip not found in zip file")
        }

        val resource = resourceContainerAccessor.read(resourceFile)?.let { resource ->
            try {
                glossaryRepository.addResource(resource)
            } catch (e: Exception) {
                this.logE("Failed to add resource: ${resource.id}", e)
            }
            val dbResource = glossaryRepository.getResource(source.language, source.identifier) ?: throw ImportGlossaryException.SourceText("Failed to register resource")

            resource.copy(id = dbResource.id, url = dbResource.url)
        } ?: throw ImportGlossaryException.SourceText("Resource is corrupted")

        fileSystemProvider.saveSource(resourceFile, "$resourceId.zip")

        val glossary = mapGlossary(backup, resource)
        val glossaryId = glossaryRepository.addGlossary(glossary)

        val phrasesToInsert = mutableListOf<Phrase>()
        val pendingPhrasesToInsert = mutableListOf<Phrase>()

        backup.phrases.forEach { phrase ->
            val dbPhrase = mapPhrase(phrase, glossaryId)
            phrasesToInsert.add(dbPhrase)
        }

        backup.pendingPhrases.forEach { phrase ->
            val dbPhrase = mapPhrase(phrase, glossaryId)
            pendingPhrasesToInsert.add(dbPhrase)
        }

        glossaryRepository.batchAddPhrases(phrasesToInsert)
        glossaryRepository.batchAddPendingPhrases(pendingPhrasesToInsert)

        return Result(
            glossary = glossary.copy(id = glossaryId),
            resource = resource
        )
    }

    private fun checkFormat(glossary: ManifestGlossary) {
        val formatVersion = glossary.formatVersion
            ?: throw ImportGlossaryException.InvalidBackup(
                "Glossary format version not found in ${GlossaryArchive.GLOSSARY}"
            )
        if (formatVersion > GlossaryArchive.FORMAT_VERSION) {
            throw ImportGlossaryException.NewerFormat(formatVersion, GlossaryArchive.FORMAT_VERSION)
        }
        if (formatVersion < 1) {
            throw ImportGlossaryException.InvalidBackup("Invalid glossary format version: $formatVersion")
        }
        // Migrations from older formats go here once FORMAT_VERSION > 1
    }

    private suspend fun readBackup(rootDir: Path): Backup {
        val manifest = readYaml(rootDir, GlossaryArchive.MANIFEST)
            ?: throw ImportGlossaryException.InvalidBackup("${GlossaryArchive.MANIFEST} not found in zip file")
        val glossary = readYaml(rootDir, GlossaryArchive.GLOSSARY)
            ?: throw ImportGlossaryException.InvalidBackup("${GlossaryArchive.GLOSSARY} not found in zip file")
        val manifestGlossary = parseYaml<ManifestGlossary>(glossary, GlossaryArchive.GLOSSARY)
        checkFormat(manifestGlossary)

        val phrases = readYaml(rootDir, GlossaryArchive.PHRASES)
            ?: throw ImportGlossaryException.InvalidBackup("${GlossaryArchive.PHRASES} not found in zip file")
        // Server downloads carry no pending phrases
        val pendingPhrases = readYaml(rootDir, GlossaryArchive.PENDING) ?: "[]"

        return Backup(
            manifest = parseYaml<Manifest>(manifest, GlossaryArchive.MANIFEST),
            glossary = manifestGlossary,
            phrases = parseYaml<List<ManifestPhrase>>(phrases, GlossaryArchive.PHRASES),
            pendingPhrases = parseYaml<List<ManifestPhrase>>(pendingPhrases, GlossaryArchive.PENDING)
        )
    }

    private inline fun <reified T> parseYaml(yaml: String, path: String): T {
        return try {
            Utils.Yaml.readValue<T>(yaml)
        } catch (e: JacksonException) {
            throw ImportGlossaryException.InvalidBackup("Invalid $path", e)
        }
    }

    private suspend fun readYaml(rootDir: Path, path: String): String? {
        val file = GlossaryArchive.file(rootDir, path)
        if (!fileSystemProvider.exists(file)) return null
        return fileSystemProvider.readFile(file)
            ?: throw ImportGlossaryException.InvalidBackup("Failed to read $path")
    }

    private suspend fun mapGlossary(
        backup: Backup,
        resource: Resource
    ): Glossary {
        val dublinCore = backup.manifest.dublinCore
        val sourceLanguage = glossaryRepository.getLanguage(backup.manifest.source.language)
        val targetLanguage = glossaryRepository.getLanguage(dublinCore.language.identifier)

        if (sourceLanguage == null) {
            throw ImportGlossaryException.UnknownLanguage(
                backup.manifest.source.language,
                "Source language not found in database"
            )
        }

        if (targetLanguage == null) {
            throw ImportGlossaryException.UnknownLanguage(
                dublinCore.language.identifier,
                "Target language not found in database"
            )
        }

        val version = dublinCore.version.toIntOrNull()
            ?: throw ImportGlossaryException.InvalidBackup("Invalid glossary version: ${dublinCore.version}")

        return Glossary(
            code = backup.glossary.code,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            version = version,
            resourceId = resource.id,
            createdAt = dublinCore.issued.toLocalDateTime(),
            updatedAt = dublinCore.modified.toLocalDateTime(),
            remoteId = backup.glossary.id
        )
    }

    /** Source text the glossary is built on; the glossary's source language is its language. */
    private val Manifest.source: Source
        get() = dublinCore.source.firstOrNull()
            ?: throw ImportGlossaryException.InvalidBackup("Source text not found in ${GlossaryArchive.MANIFEST}")

    private fun mapPhrase(phrase: ManifestPhrase, glossaryId: String): Phrase {
        return Phrase(
            phrase = phrase.phrase,
            spelling = phrase.spelling,
            description = phrase.description,
            audio = phrase.audio,
            createdAt = phrase.createdAt.toLocalDateTime(),
            updatedAt = phrase.updatedAt.toLocalDateTime(),
            glossaryId = glossaryId
        )
    }
}
