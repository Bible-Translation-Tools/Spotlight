package org.bibletranslationtools.glossary.domain.usecases

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
        val manifest: ManifestGlossary,
        val phrases: List<ManifestPhrase>,
        val pendingPhrases: List<ManifestPhrase>
    )

    /**
     * Returns the glossary already stored on the device that importing
     * [file] would overwrite, or null if there is none.
     */
    suspend fun findExisting(file: PlatformFile): Glossary? {
        val (_, yaml) = fileSystemProvider.readZipEntry(file, GlossaryArchive::isManifestEntry)
            ?: return null
        val manifest = Utils.Yaml.readValue<ManifestGlossary>(yaml)

        return glossaryRepository.getGlossaries().firstOrNull {
            it.code == manifest.glossary.code &&
                    it.sourceLanguage.slug == manifest.source.language &&
                    it.targetLanguage.slug == manifest.dublinCore.language.identifier
        }
    }

    suspend operator fun invoke(file: PlatformFile): Result {

        val (manifestEntry, manifestYaml) = fileSystemProvider.readZipEntry(
            file,
            GlossaryArchive::isManifestEntry
        ) ?: throw IllegalArgumentException("${GlossaryArchive.MANIFEST} not found in zip file")

        val tempDir = fileSystemProvider.createTempDir("glossary")
        fileSystemProvider.extractZip(file, tempDir)

        val rootDir = GlossaryArchive.rootDirOf(manifestEntry)
            .let { if (it.isEmpty()) tempDir else Path(tempDir, it) }
        val backup = readBackup(rootDir, manifestYaml)
        val glossaryDict = backup.manifest
        val source = glossaryDict.source

        val resourceId = "${source.language}_${source.identifier}"
        val resourceFile = Path(rootDir, "$resourceId.zip")

        if (!fileSystemProvider.exists(resourceFile)) {
            throw IllegalArgumentException("$resourceId.zip not found in zip file")
        }

        val resource = resourceContainerAccessor.read(resourceFile)?.let { resource ->
            try {
                glossaryRepository.addResource(resource)
            } catch (e: Exception) {
                this.logE("Failed to add resource: ${resource.id}", e)
            }
            val dbResource = glossaryRepository.getResource(source.language, source.identifier) ?: throw IllegalArgumentException("Failed to register resource")
            
            resource.copy(id = dbResource.id, url = dbResource.url)
        } ?: throw IllegalArgumentException("Resource is corrupted")

        fileSystemProvider.saveSource(resourceFile, "$resourceId.zip")

        val glossary = mapGlossary(glossaryDict, resource)
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

    private suspend fun readBackup(rootDir: Path, manifest: String): Backup {
        val contentFile = "${GlossaryArchive.CONTENT_DIR}/${GlossaryArchive.PHRASES}"
        val phrases = readYaml(Path(rootDir, GlossaryArchive.CONTENT_DIR, GlossaryArchive.PHRASES))
            ?: throw IllegalArgumentException("$contentFile not found in zip file")
        // Server downloads carry no pending phrases
        val pendingPhrases = readYaml(Path(rootDir, GlossaryArchive.PENDING_DIR, GlossaryArchive.PHRASES))
            ?: "[]"

        return Backup(
            manifest = Utils.Yaml.readValue<ManifestGlossary>(manifest),
            phrases = Utils.Yaml.readValue<List<ManifestPhrase>>(phrases),
            pendingPhrases = Utils.Yaml.readValue<List<ManifestPhrase>>(pendingPhrases)
        )
    }

    private suspend fun readYaml(file: Path): String? {
        if (!fileSystemProvider.exists(file)) return null
        return fileSystemProvider.readFile(file)
            ?: throw IllegalArgumentException("Failed to read ${file.name}")
    }

    private suspend fun mapGlossary(
        glossary: ManifestGlossary,
        resource: Resource
    ): Glossary {
        val sourceLanguage = glossaryRepository.getLanguage(glossary.source.language)
        val targetLanguage = glossaryRepository.getLanguage(glossary.dublinCore.language.identifier)

        if (sourceLanguage == null) {
            throw IllegalArgumentException("Source language not found in database")
        }

        if (targetLanguage == null) {
            throw IllegalArgumentException("Target language not found in database")
        }

        val version = glossary.dublinCore.version.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid glossary version: ${glossary.dublinCore.version}")

        return Glossary(
            code = glossary.glossary.code,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            version = version,
            resourceId = resource.id,
            createdAt = glossary.dublinCore.issued.toLocalDateTime(),
            updatedAt = glossary.dublinCore.modified.toLocalDateTime(),
            remoteId = glossary.glossary.id
        )
    }

    /** Source text the glossary is built on; the glossary's source language is its language. */
    private val ManifestGlossary.source: Source
        get() = dublinCore.source.firstOrNull()
            ?: throw IllegalArgumentException("Source text not found in ${GlossaryArchive.MANIFEST}")

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
