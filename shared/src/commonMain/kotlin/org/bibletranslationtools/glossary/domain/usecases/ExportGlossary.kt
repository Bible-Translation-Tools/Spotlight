package org.bibletranslationtools.glossary.domain.usecases

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.io.files.Path

import org.bibletranslationtools.glossary.Utils
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.api.ManifestGlossary
import org.bibletranslationtools.glossary.data.api.ManifestGlossaryInfo
import org.bibletranslationtools.glossary.data.api.ManifestPhrase
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.GlossaryArchive
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.wycliffeassociates.resourcecontainer.entity.DublinCore
import org.wycliffeassociates.resourcecontainer.entity.Language
import org.wycliffeassociates.resourcecontainer.entity.Project
import org.wycliffeassociates.resourcecontainer.entity.Source

class ExportGlossary(
    private val glossaryRepository: GlossaryRepository,
    private val fileSystemProvider: FileSystemProvider
) {
    suspend operator fun invoke(glossary: Glossary, target: PlatformFile) {
        val resource = glossaryRepository.getResource(glossary.resourceId)
            ?: throw IllegalArgumentException("Resource not found")
        val phrases = glossaryRepository.getPhrases(glossary.id)
        val pendingPhrases = glossaryRepository.getPendingPhrases(glossary.id)

        val tempDir = fileSystemProvider.createTempDir("glossary")
        val rootDir = Path(tempDir, GlossaryArchive.rootDirName(glossary.targetLanguage.slug))
        val contentDir = Path(rootDir, GlossaryArchive.CONTENT_DIR)
        val pendingDir = Path(rootDir, GlossaryArchive.PENDING_DIR)
        fileSystemProvider.createDirectories(contentDir)
        fileSystemProvider.createDirectories(pendingDir)

        writeYaml(manifest(glossary, resource), Path(rootDir, GlossaryArchive.MANIFEST))
        fileSystemProvider.writeFile(GlossaryArchive.LICENSE_TEXT, Path(rootDir, GlossaryArchive.LICENSE))
        writeYaml(phrases.toManifest(), Path(contentDir, GlossaryArchive.PHRASES))
        writeYaml(pendingPhrases.toManifest(), Path(pendingDir, GlossaryArchive.PHRASES))

        val resourceFile = Path(fileSystemProvider.sources, resource.filename)
        if (!fileSystemProvider.exists(resourceFile)) {
            throw IllegalArgumentException("Resource file not found")
        }
        fileSystemProvider.copyFileToDir(resourceFile, rootDir)

        fileSystemProvider.zipDirectory(tempDir, target)
    }

    private fun manifest(glossary: Glossary, resource: Resource): ManifestGlossary {
        val targetLanguage = glossary.targetLanguage
        return ManifestGlossary(
            dublinCore = DublinCore(
                conformsTo = GlossaryArchive.CONFORMS_TO,
                type = GlossaryArchive.TYPE,
                format = GlossaryArchive.FORMAT,
                identifier = GlossaryArchive.IDENTIFIER,
                title = "${GlossaryArchive.SUBJECT} ${glossary.code}",
                subject = GlossaryArchive.SUBJECT,
                language = Language(
                    direction = targetLanguage.direction,
                    identifier = targetLanguage.slug,
                    title = targetLanguage.name
                ),
                // The resource is always in the glossary's source language
                source = mutableListOf(
                    Source(
                        identifier = resource.type,
                        language = resource.lang,
                        version = resource.version
                    )
                ),
                relation = mutableListOf("${resource.lang}/${resource.type}"),
                rights = GlossaryArchive.RIGHTS,
                issued = glossary.createdAt.toString(),
                modified = glossary.updatedAt.toString(),
                version = glossary.version.toString()
            ),
            projects = listOf(
                Project(
                    identifier = GlossaryArchive.IDENTIFIER,
                    title = GlossaryArchive.SUBJECT,
                    sort = 1,
                    path = "./${GlossaryArchive.CONTENT_DIR}"
                )
            ),
            glossary = ManifestGlossaryInfo(
                code = glossary.code,
                id = glossary.remoteId
            )
        )
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
