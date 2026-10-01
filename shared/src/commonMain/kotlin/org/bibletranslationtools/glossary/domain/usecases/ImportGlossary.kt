package org.bibletranslationtools.glossary.domain.usecases

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.io.files.Path
import org.bibletranslationtools.glossary.Utils
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.api.ManifestGlossary
import org.bibletranslationtools.glossary.data.api.ManifestPhrase
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.logE
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.toLocalDateTime

class ImportGlossary(
    private val glossaryRepository: GlossaryRepository,
    private val fileSystemProvider: FileSystemProvider,
    private val resourceContainerAccessor: ResourceContainerAccessor
) {
    data class Result(
        val glossary: Glossary,
        val resource: Resource
    )

    /**
     * Returns the glossary already stored on the device that importing
     * [file] would overwrite, or null if there is none.
     */
    suspend fun findExisting(file: PlatformFile): Glossary? {
        val json = fileSystemProvider.readZipEntry(file, "glossary.json") ?: return null
        val glossaryDict: ManifestGlossary = Utils.JsonLenient.decodeFromString(json)

        return glossaryRepository.getGlossaries().firstOrNull {
            it.code == glossaryDict.code &&
                    it.sourceLanguage.slug == glossaryDict.sourceLanguage &&
                    it.targetLanguage.slug == glossaryDict.targetLanguage
        }
    }

    suspend operator fun invoke(file: PlatformFile): Result {

        val tempDir = fileSystemProvider.createTempDir("glossary")
        fileSystemProvider.extractZip(file, tempDir)

        val glossaryFile = Path(tempDir, "glossary.json")

        if (!fileSystemProvider.exists(glossaryFile)) {
            throw IllegalArgumentException("glossary.json not found in zip file")
        }

        val json = fileSystemProvider.readFile(glossaryFile)
            ?: throw IllegalArgumentException("Failed to read glossary.json")

        val glossaryDict: ManifestGlossary = Utils.JsonLenient.decodeFromString(json)

        val resourceId = glossaryDict.resource.toString()
        val resourceFile = Path(tempDir, "$resourceId.zip")

        if (!fileSystemProvider.exists(resourceFile)) {
            throw IllegalArgumentException("$resourceId.zip not found in zip file")
        }

        val resource = resourceContainerAccessor.read(resourceFile)?.let { resource ->
            try {
                glossaryRepository.addResource(resource)
            } catch (e: Exception) {
                this.logE("Failed to add resource: ${resource.id}", e)
            }
            val dbResource = glossaryRepository.getResource(
                glossaryDict.resource.language,
                glossaryDict.resource.type
            ) ?: throw IllegalArgumentException("Failed to register resource")
            
            resource.copy(id = dbResource.id, url = dbResource.url)
        } ?: throw IllegalArgumentException("Resource is corrupted")

        fileSystemProvider.saveSource(resourceFile, "$resourceId.zip")

        val glossary = mapGlossary(glossaryDict, resource)
        val glossaryId = glossaryRepository.addGlossary(glossary)

        val phrasesToInsert = mutableListOf<Phrase>()
        val pendingPhrasesToInsert = mutableListOf<Phrase>()

        glossaryDict.phrases.forEach { phrase ->
            val dbPhrase = mapPhrase(phrase, glossaryId)
            phrasesToInsert.add(dbPhrase)
        }

        glossaryDict.pendingPhrases.forEach { phrase ->
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

    private suspend fun mapGlossary(
        glossary: ManifestGlossary,
        resource: Resource
    ): Glossary {
        val sourceLanguage = glossaryRepository.getLanguage(glossary.sourceLanguage)
        val targetLanguage = glossaryRepository.getLanguage(glossary.targetLanguage)

        if (sourceLanguage == null) {
            throw IllegalArgumentException("Source language not found in database")
        }

        if (targetLanguage == null) {
            throw IllegalArgumentException("Target language not found in database")
        }

        return Glossary(
            code = glossary.code,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            version = glossary.version,
            resourceId = resource.id,
            createdAt = glossary.createdAt.toLocalDateTime(),
            updatedAt = glossary.updatedAt.toLocalDateTime(),
            remoteId = glossary.id
        )
    }

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
