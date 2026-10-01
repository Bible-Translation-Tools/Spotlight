package org.bibletranslationtools.glossary.domain.usecases

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.io.files.Path

import org.bibletranslationtools.glossary.Utils
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.api.ManifestGlossary
import org.bibletranslationtools.glossary.data.api.ManifestPhrase
import org.bibletranslationtools.glossary.data.api.ManifestResource
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.GlossaryArchive
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository

class ExportGlossary(
    private val glossaryRepository: GlossaryRepository,
    private val fileSystemProvider: FileSystemProvider
) {
    suspend operator fun invoke(glossary: Glossary, target: PlatformFile) {
        val resource = glossaryRepository.getResource(glossary.resourceId)
            ?: throw IllegalArgumentException("Resource not found")
        val phrases = glossaryRepository.getPhrases(glossary.id)
        val pendingPhrases = glossaryRepository.getPendingPhrases(glossary.id)

        val manifest = ManifestGlossary(
            id = glossary.remoteId,
            code = glossary.code,
            sourceLanguage = glossary.sourceLanguage.slug,
            targetLanguage = glossary.targetLanguage.slug,
            version = glossary.version,
            createdAt = glossary.createdAt.toString(),
            updatedAt = glossary.updatedAt.toString(),
            resource = ManifestResource(
                language = resource.lang,
                type = resource.type,
                version = resource.version
            )
        )

        val tempDir = fileSystemProvider.createTempDir("glossary")

        writeYaml(manifest, Path(tempDir, GlossaryArchive.MANIFEST))
        writeYaml(phrases.toManifest(), Path(tempDir, GlossaryArchive.CONTENT))
        writeYaml(pendingPhrases.toManifest(), Path(tempDir, GlossaryArchive.PENDING))

        val resourceFile = Path(fileSystemProvider.sources, resource.filename)
        if (!fileSystemProvider.exists(resourceFile)) {
            throw IllegalArgumentException("Resource file not found")
        }
        fileSystemProvider.copyFileToDir(resourceFile, tempDir)

        fileSystemProvider.zipDirectory(tempDir, target)
    }

    private suspend fun writeYaml(value: Any, file: Path) {
        fileSystemProvider.writeFile(Utils.Yaml.writeValueAsString(value), file)
    }

    // Sorted, so the same glossary always produces the same file
    private fun List<Phrase>.toManifest(): List<ManifestPhrase> {
        return sortedBy { it.phrase }.map { phrase ->
            ManifestPhrase(
                phrase = phrase.phrase,
                spelling = phrase.spelling,
                description = phrase.description,
                audio = phrase.audio,
                createdAt = phrase.createdAt.toString(),
                updatedAt = phrase.updatedAt.toString()
            )
        }
    }
}
