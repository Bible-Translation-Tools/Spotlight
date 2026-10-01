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
import kotlin.test.assertFailsWith
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
        stubBackup(rootDir, "es_glossary")
        stubYaml(rootDir, "content/phrases.yaml", contentYaml)
        stubYaml(rootDir, ".apps/spotlight/pending_phrases.yaml", pendingYaml)
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
            fileSystemProvider.saveSource(Path(rootDir, ".apps/spotlight/source/en_ulb.zip"), "en_ulb.zip")
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
        stubBackup(tempDir, "")
        stubYaml(tempDir, "content/phrases.yaml", contentYaml)
        coEvery { fileSystemProvider.exists(Path(tempDir, ".apps/spotlight/pending_phrases.yaml")) } returns false
        stubResourceAndRepository(tempDir)

        val result = importGlossary(file)

        assertEquals("G1", result.glossary.code)
        coVerify {
            fileSystemProvider.saveSource(Path(tempDir, ".apps/spotlight/source/en_ulb.zip"), "en_ulb.zip")
            repository.batchAddPendingPhrases(emptyList())
        }
    }

    @Test
    fun testImportFailureMissingManifest() = runTest {
        coEvery { fileSystemProvider.readZipEntry(file, any()) } returns null

        assertImportFails<ImportGlossaryException.InvalidBackup>("manifest.yaml not found in zip file")
    }

    @Test
    fun testImportFailureMissingGlossary() = runTest {
        stubBackup(rootDir, "es_glossary", glossary = null)

        assertImportFails<ImportGlossaryException.InvalidBackup>(".apps/spotlight/glossary.yaml not found in zip file")
    }

    @Test
    fun testImportFailureMissingFormatVersion() = runTest {
        stubBackup(rootDir, "es_glossary", glossary = glossaryYaml(formatVersion = ""))

        assertImportFails<ImportGlossaryException.InvalidBackup>("Glossary format version not found in .apps/spotlight/glossary.yaml")
    }

    @Test
    fun testImportFailureNewerFormatVersion() = runTest {
        stubBackup(rootDir, "es_glossary", glossary = glossaryYaml(formatVersion = "format_version: 2"))

        assertImportFails<ImportGlossaryException.NewerFormat>("Glossary format 2 is newer than supported 1, update the app")
    }

    @Test
    fun testImportFailureMalformedYaml() = runTest {
        stubBackup(rootDir, "es_glossary", glossary = "format_version: [")

        assertImportFails<ImportGlossaryException.InvalidBackup>("Invalid .apps/spotlight/glossary.yaml")
    }

    @Test
    fun testImportFailureMissingContent() = runTest {
        stubBackup(rootDir, "es_glossary")
        coEvery { fileSystemProvider.exists(Path(rootDir, "content/phrases.yaml")) } returns false

        assertImportFails<ImportGlossaryException.InvalidBackup>("content/phrases.yaml not found in zip file")
    }

    @Test
    fun testImportFailureMissingSource() = runTest {
        stubBackup(rootDir, "es_glossary", manifest = manifestYaml(source = ""))
        stubYaml(rootDir, "content/phrases.yaml", "[]")
        coEvery { fileSystemProvider.exists(Path(rootDir, ".apps/spotlight/pending_phrases.yaml")) } returns false

        assertImportFails<ImportGlossaryException.InvalidBackup>("Source text not found in manifest.yaml")
    }

    @Test
    fun testImportFailureMissingResourceZip() = runTest {
        stubBackup(rootDir, "es_glossary")
        stubYaml(rootDir, "content/phrases.yaml", "[]")
        coEvery { fileSystemProvider.exists(Path(rootDir, ".apps/spotlight/pending_phrases.yaml")) } returns false
        coEvery { fileSystemProvider.exists(Path(rootDir, ".apps/spotlight/source/en_ulb.zip")) } returns false

        assertImportFails<ImportGlossaryException.SourceText>("en_ulb.zip not found in zip file")
    }

    @Test
    fun testImportFailureMissingLanguage() = runTest {
        stubBackup(rootDir, "es_glossary")
        stubYaml(rootDir, "content/phrases.yaml", "[]")
        coEvery { fileSystemProvider.exists(Path(rootDir, ".apps/spotlight/pending_phrases.yaml")) } returns false
        stubResourceAndRepository(rootDir)
        coEvery { repository.getLanguage("en") } returns null

        val error = assertImportFails<ImportGlossaryException.UnknownLanguage>(
            "Source language not found in database"
        )
        assertEquals("en", error.language)
    }

    @Test
    fun testFindExistingReturnsMatchingGlossary() = runTest {
        val french = Language("fr", "French", "ltr")
        val existing = Glossary(code = "G1", sourceLanguage = english, targetLanguage = spanish, version = 1, id = "g1")
        val otherTarget = Glossary(code = "G1", sourceLanguage = english, targetLanguage = french, version = 1, id = "g2")

        stubZipEntries("es_glossary", manifestYaml(), glossaryYaml())
        coEvery { repository.getGlossaries() } returns listOf(otherTarget, existing)

        assertEquals(existing, importGlossary.findExisting(file))
    }

    @Test
    fun testFindExistingReturnsNullWhenNoMatch() = runTest {
        val existing = Glossary(code = "G1", sourceLanguage = english, targetLanguage = spanish, version = 1, id = "g1")

        stubZipEntries("es_glossary", manifestYaml(), glossaryYaml(code = "G2"))
        coEvery { repository.getGlossaries() } returns listOf(existing)

        assertNull(importGlossary.findExisting(file))
    }

    @Test
    fun testFindExistingFailsOnNewerFormatVersion() = runTest {
        stubZipEntries("es_glossary", manifestYaml(), glossaryYaml(formatVersion = "format_version: 2"))

        val error = assertFailsWith<ImportGlossaryException.NewerFormat> { importGlossary.findExisting(file) }
        assertEquals("Glossary format 2 is newer than supported 1, update the app", error.message)
    }

    @Test
    fun testFindExistingReturnsNullWhenManifestMissing() = runTest {
        coEvery { fileSystemProvider.readZipEntry(file, any()) } returns null

        assertNull(importGlossary.findExisting(file))
    }

    @Test
    fun testFindExistingReturnsNullWhenGlossaryMissing() = runTest {
        stubZipEntries("es_glossary", manifestYaml(), glossary = null)

        assertNull(importGlossary.findExisting(file))
    }

    private fun manifestYaml(
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
        |checking:
        |  checking_entity: []
        |  checking_level: ""
        |projects:
        |  - identifier: "glossary"
        |    sort: 1
        |    path: "./content"
    """.trimMargin()

    private fun glossaryYaml(
        code: String = "G1",
        formatVersion: String = "format_version: 1"
    ) = """
        |$formatVersion
        |code: "$code"
        |id: "remote-g1"
    """.trimMargin()

    /** Zip entries as readZipEntry sees them, matched with the caller's predicate. */
    private fun stubZipEntries(rootEntry: String, manifest: String, glossary: String?) {
        val prefix = if (rootEntry.isEmpty()) "" else "$rootEntry/"
        val entries = buildMap {
            put("${prefix}manifest.yaml", manifest)
            glossary?.let { put("${prefix}.apps/spotlight/glossary.yaml", it) }
        }
        coEvery { fileSystemProvider.readZipEntry(file, any()) } answers {
            val predicate = secondArg<(String) -> Boolean>()
            entries.entries.firstOrNull { predicate(it.key) }?.toPair()
        }
    }

    private fun stubBackup(
        dir: Path,
        rootEntry: String,
        manifest: String = manifestYaml(),
        glossary: String? = glossaryYaml()
    ) {
        stubZipEntries(rootEntry, manifest, glossary)
        coEvery { fileSystemProvider.createTempDir(any()) } returns tempDir
        coEvery { fileSystemProvider.extractZip(any(), any()) } returns Unit
        if (glossary != null) {
            stubYaml(dir, ".apps/spotlight/glossary.yaml", glossary)
        } else {
            coEvery { fileSystemProvider.exists(Path(dir, ".apps/spotlight/glossary.yaml")) } returns false
        }
    }

    private fun stubYaml(dir: Path, name: String, content: String) {
        coEvery { fileSystemProvider.exists(Path(dir, name)) } returns true
        coEvery { fileSystemProvider.readFile(Path(dir, name)) } returns content
    }

    private fun stubResourceAndRepository(dir: Path) {
        coEvery { fileSystemProvider.exists(Path(dir, ".apps/spotlight/source/en_ulb.zip")) } returns true
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

    private suspend inline fun <reified T : ImportGlossaryException> assertImportFails(message: String): T {
        val error = assertFailsWith<T> { importGlossary(file) }
        assertEquals(message, error.message)
        return error
    }
}
