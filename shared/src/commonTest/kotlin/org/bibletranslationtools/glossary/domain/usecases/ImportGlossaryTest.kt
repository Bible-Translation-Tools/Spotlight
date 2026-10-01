package org.bibletranslationtools.glossary.domain.usecases

import io.github.vinceglb.filekit.PlatformFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.io.files.Path

import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportGlossaryTest {

    private val repository: GlossaryRepository = mockk()
    private val fileSystemProvider: FileSystemProvider = mockk()
    private val resourceContainerAccessor: ResourceContainerAccessor = mockk()
    private lateinit var importGlossary: ImportGlossary

    private val file: PlatformFile = mockk()
    private val tempDir = Path("/tmp/glossary")
    private val rootDir = Path(tempDir, "es_glossary")

    private val english = Language("en", "English", "ltr")
    private val spanish = Language("es", "Spanish", "ltr")

    private val resource = Resource(
        id = 1L,
        lang = "en",
        type = "ulb",
        version = "1",
        format = "usfm",
        url = "http://example.com",
        filename = "en_ulb.zip",
        createdAt = LocalDateTime(2024, 1, 1, 0, 0),
        modifiedAt = LocalDateTime(2024, 1, 1, 0, 0),
        books = emptyList()
    )

    private val contentYaml = """
        - phrase: "God"
          spelling: "Dios"
          description: "line 1"
          createdAt: "2024-01-01T00:00:00"
          updatedAt: "2024-01-01T00:00:00"
        - phrase: "Holy Spirit"
          spelling: ""
          description: |-
            line 1
            line 2
          audio: ""
          createdAt: "2024-01-01T00:00:00"
          updatedAt: "2024-01-01T00:00:00"
        - phrase: "god"
          spelling: ""
          description: ""
          createdAt: "2024-01-01T00:00:00"
          updatedAt: "2024-01-01T00:00:00"
    """.trimIndent()

    private val pendingYaml = """
        - phrase: "grace"
          spelling: ""
          description: ""
          createdAt: "2024-01-01T00:00:00"
          updatedAt: "2024-01-01T00:00:00"
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        importGlossary = ImportGlossary(repository, fileSystemProvider, resourceContainerAccessor)
    }

    @Test
    fun testImportSuccess() = runTest {
        stubManifest("es_glossary/manifest.yaml", manifestYaml())
        stubYaml(rootDir, "content/phrases.yaml", contentYaml)
        stubYaml(rootDir, "content/pending_phrases.yaml", pendingYaml)
        stubResourceAndRepository(rootDir)

        val result = importGlossary(file)

        assertEquals("g1", result.glossary.id)
        assertEquals(resource.id, result.resource.id)
        with(result.glossary) {
            assertEquals("G1", code)
            assertEquals("remote-g1", remoteId)
            assertEquals(3, version)
            assertEquals(english, sourceLanguage)
            assertEquals(spanish, targetLanguage)
            assertEquals(LocalDateTime(2024, 1, 1, 0, 0), createdAt)
            assertEquals(LocalDateTime(2024, 2, 1, 0, 0), updatedAt)
        }

        coVerify {
            fileSystemProvider.extractZip(file, tempDir)
            fileSystemProvider.saveSource(Path(rootDir, "en_ulb.zip"), "en_ulb.zip")
            repository.getResource("en", "ulb")
            repository.batchAddPhrases(match { phrases ->
                phrases.map { it.phrase } == listOf("God", "Holy Spirit", "god") &&
                        phrases[1].description == "line 1\nline 2" &&
                        phrases.all { it.glossaryId == "g1" }
            })
            repository.batchAddPendingPhrases(match { phrases ->
                phrases.map { it.phrase } == listOf("grace")
            })
        }
    }

    @Test
    fun testImportFromZipRoot() = runTest {
        stubManifest("manifest.yaml", manifestYaml())
        stubYaml(tempDir, "content/phrases.yaml", contentYaml)
        coEvery { fileSystemProvider.exists(Path(tempDir, "content/pending_phrases.yaml")) } returns false
        stubResourceAndRepository(tempDir)

        val result = importGlossary(file)

        assertEquals("G1", result.glossary.code)
        coVerify {
            fileSystemProvider.saveSource(Path(tempDir, "en_ulb.zip"), "en_ulb.zip")
            repository.batchAddPendingPhrases(emptyList())
        }
    }

    @Test
    fun testImportFailureMissingManifest() = runTest {
        coEvery { fileSystemProvider.readZipEntry(file, any()) } returns null

        assertImportFails("manifest.yaml not found in zip file")
    }

    @Test
    fun testImportFailureMissingFormatVersion() = runTest {
        stubManifest("es_glossary/manifest.yaml", manifestYaml(formatVersion = ""))

        assertImportFails("Glossary format version not found in manifest.yaml")
    }

    @Test
    fun testImportFailureNewerFormatVersion() = runTest {
        stubManifest("es_glossary/manifest.yaml", manifestYaml(formatVersion = "  format_version: 2"))

        assertImportFails("Glossary format 2 is newer than supported 1, update the app")
    }

    @Test
    fun testImportFailureMissingContent() = runTest {
        stubManifest("es_glossary/manifest.yaml", manifestYaml())
        coEvery { fileSystemProvider.exists(Path(rootDir, "content/phrases.yaml")) } returns false

        assertImportFails("content/phrases.yaml not found in zip file")
    }

    @Test
    fun testImportFailureMissingSource() = runTest {
        stubManifest("es_glossary/manifest.yaml", manifestYaml(source = ""))
        stubYaml(rootDir, "content/phrases.yaml", "[]")
        coEvery { fileSystemProvider.exists(Path(rootDir, "content/pending_phrases.yaml")) } returns false

        assertImportFails("Source text not found in manifest.yaml")
    }

    @Test
    fun testImportFailureMissingResourceZip() = runTest {
        stubManifest("es_glossary/manifest.yaml", manifestYaml())
        stubYaml(rootDir, "content/phrases.yaml", "[]")
        coEvery { fileSystemProvider.exists(Path(rootDir, "content/pending_phrases.yaml")) } returns false
        coEvery { fileSystemProvider.exists(Path(rootDir, "en_ulb.zip")) } returns false

        assertImportFails("en_ulb.zip not found in zip file")
    }

    @Test
    fun testImportFailureMissingLanguage() = runTest {
        stubManifest("es_glossary/manifest.yaml", manifestYaml())
        stubYaml(rootDir, "content/phrases.yaml", "[]")
        coEvery { fileSystemProvider.exists(Path(rootDir, "content/pending_phrases.yaml")) } returns false
        stubResourceAndRepository(rootDir)
        coEvery { repository.getLanguage("en") } returns null

        assertImportFails("Source language not found in database")
    }

    @Test
    fun testFindExistingReturnsMatchingGlossary() = runTest {
        val french = Language("fr", "French", "ltr")
        val existing = Glossary(code = "G1", sourceLanguage = english, targetLanguage = spanish, version = 1, id = "g1")
        val otherTarget = Glossary(code = "G1", sourceLanguage = english, targetLanguage = french, version = 1, id = "g2")

        coEvery { fileSystemProvider.readZipEntry(file, any()) } returns ("es_glossary/manifest.yaml" to manifestYaml())
        coEvery { repository.getGlossaries() } returns listOf(otherTarget, existing)

        assertEquals(existing, importGlossary.findExisting(file))
    }

    @Test
    fun testFindExistingReturnsNullWhenNoMatch() = runTest {
        val existing = Glossary(code = "G1", sourceLanguage = english, targetLanguage = spanish, version = 1, id = "g1")

        coEvery { fileSystemProvider.readZipEntry(file, any()) } returns ("es_glossary/manifest.yaml" to manifestYaml(code = "G2"))
        coEvery { repository.getGlossaries() } returns listOf(existing)

        assertNull(importGlossary.findExisting(file))
    }

    @Test
    fun testFindExistingReturnsNullWhenManifestMissing() = runTest {
        coEvery { fileSystemProvider.readZipEntry(file, any()) } returns null

        assertNull(importGlossary.findExisting(file))
    }

    private fun manifestYaml(
        code: String = "G1",
        formatVersion: String = "  format_version: 1",
        source: String = """
            |  source:
            |    - identifier: "ulb"
            |      language: "en"
            |      version: "1"
        """.trimMargin()
    ) = """
        |dublin_core:
        |  conformsto: "rc0.2"
        |  type: "dict"
        |  format: "text/yaml"
        |  identifier: "glossary"
        |  language:
        |    direction: "ltr"
        |    identifier: "es"
        |    title: "Español"
        |$source
        |  issued: "2024-01-01T00:00:00"
        |  modified: "2024-02-01T00:00:00"
        |  version: "3"
        |projects:
        |  - identifier: "glossary"
        |    sort: 1
        |    path: "./content"
        |glossary:
        |$formatVersion
        |  code: "$code"
        |  id: "remote-g1"
    """.trimMargin()

    private fun stubManifest(entryName: String, yaml: String) {
        coEvery { fileSystemProvider.readZipEntry(file, any()) } returns (entryName to yaml)
        coEvery { fileSystemProvider.createTempDir(any()) } returns tempDir
        coEvery { fileSystemProvider.extractZip(any(), any()) } returns Unit
    }

    private fun stubYaml(dir: Path, name: String, content: String) {
        coEvery { fileSystemProvider.exists(Path(dir, name)) } returns true
        coEvery { fileSystemProvider.readFile(Path(dir, name)) } returns content
    }

    private fun stubResourceAndRepository(dir: Path) {
        coEvery { fileSystemProvider.exists(Path(dir, "en_ulb.zip")) } returns true
        coEvery { fileSystemProvider.saveSource(any<Path>(), any()) } returns Path("/sources/en_ulb.zip")
        every { resourceContainerAccessor.read(any<Path>()) } returns resource
        coEvery { repository.getLanguage("en") } returns english
        coEvery { repository.getLanguage("es") } returns spanish
        coEvery { repository.addResource(any()) } returns Unit
        coEvery { repository.getResource("en", "ulb") } returns resource
        coEvery { repository.addGlossary(any()) } returns "g1"
        coEvery { repository.batchAddPhrases(any()) } returns Unit
        coEvery { repository.batchAddPendingPhrases(any()) } returns Unit
    }

    private suspend fun assertImportFails(message: String) {
        try {
            importGlossary(file)
            kotlin.test.fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals(message, e.message)
        }
    }
}
