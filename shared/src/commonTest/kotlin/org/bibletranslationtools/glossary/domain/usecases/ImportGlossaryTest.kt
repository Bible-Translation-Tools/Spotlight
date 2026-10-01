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

    @BeforeTest
    fun setUp() {
        importGlossary = ImportGlossary(repository, fileSystemProvider, resourceContainerAccessor)
    }

    @Test
    fun testImportSuccess() = runTest {
        val file: PlatformFile = mockk()
        val tempDir = Path("/tmp/glossary")
        val glossaryJsonPath = Path(tempDir, "glossary.json")
        val resourceZipPath = Path(tempDir, "en_ulb.zip")
        
        val glossaryJson = """
            {
                "id": "remote-g1",
                "code": "G1",
                "sourceLanguage": "en",
                "targetLanguage": "es",
                "version": 1,
                "createdAt": "2024-01-01T00:00:00",
                "updatedAt": "2024-01-01T00:00:00",
                "resource": {
                    "language": "en",
                    "type": "ulb",
                    "version": "1"
                },
                "phrases": [],
                "pendingPhrases": []
            }
        """.trimIndent()
        
        coEvery { fileSystemProvider.exists(glossaryJsonPath) } returns true
        coEvery { fileSystemProvider.readFile(glossaryJsonPath) } returns glossaryJson
        coEvery { fileSystemProvider.exists(resourceZipPath) } returns true
        
        val sourceLang = Language("en", "English", "ltr")
        val targetLang = Language("es", "Spanish", "ltr")
        val resource = Resource(
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

        coEvery { fileSystemProvider.createTempDir(any()) } returns tempDir
        coEvery { fileSystemProvider.extractZip(any(), any()) } returns Unit
        
        coEvery { repository.getLanguage("en") } returns sourceLang
        coEvery { repository.getLanguage("es") } returns targetLang
        coEvery { repository.addResource(any()) } returns Unit
        coEvery { repository.getResource("en", "ulb") } returns resource
        coEvery { repository.addGlossary(any()) } returns "g1"
        coEvery { repository.batchAddPhrases(any()) } returns Unit
        coEvery { repository.batchAddPendingPhrases(any()) } returns Unit
        coEvery { fileSystemProvider.saveSource(any<Path>(), any()) } returns Path("/sources/en_ulb.zip")
        every { resourceContainerAccessor.read(any<Path>()) } returns resource

        val result = importGlossary(file)

        assertEquals("g1", result.glossary.id)
        assertEquals(resource.id, result.resource.id)

        coVerify {
            fileSystemProvider.createTempDir(any())
            fileSystemProvider.extractZip(file, tempDir)
            repository.addGlossary(any())
            repository.batchAddPhrases(any())
        }
    }

    @Test
    fun testImportFailureMissingGlossaryJson() = runTest {
        val file: PlatformFile = mockk()
        val tempDir = Path("/tmp/glossary")
        val glossaryJsonPath = Path(tempDir, "glossary.json")

        coEvery { fileSystemProvider.createTempDir(any()) } returns tempDir
        coEvery { fileSystemProvider.extractZip(any(), any()) } returns Unit
        coEvery { fileSystemProvider.exists(glossaryJsonPath) } returns false

        try {
            importGlossary(file)
            kotlin.test.fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("glossary.json not found in zip file", e.message)
        }
    }

    @Test
    fun testImportFailureMissingResourceZip() = runTest {
        val file: PlatformFile = mockk()
        val tempDir = Path("/tmp/glossary")
        val glossaryJsonPath = Path(tempDir, "glossary.json")
        val resourceZipPath = Path(tempDir, "en_ulb.zip")
        
        val glossaryJson = """
            {
                "id": "remote-g1",
                "code": "G1",
                "sourceLanguage": "en",
                "targetLanguage": "es",
                "version": 1,
                "createdAt": "2024-01-01T00:00:00",
                "updatedAt": "2024-01-01T00:00:00",
                "resource": {
                    "language": "en",
                    "type": "ulb",
                    "version": "1"
                },
                "phrases": [],
                "pendingPhrases": []
            }
        """.trimIndent()

        coEvery { fileSystemProvider.createTempDir(any()) } returns tempDir
        coEvery { fileSystemProvider.extractZip(any(), any()) } returns Unit
        coEvery { fileSystemProvider.exists(glossaryJsonPath) } returns true
        coEvery { fileSystemProvider.readFile(glossaryJsonPath) } returns glossaryJson
        coEvery { fileSystemProvider.exists(resourceZipPath) } returns false

        try {
            importGlossary(file)
            kotlin.test.fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("en_ulb.zip not found in zip file", e.message)
        }
    }

    @Test
    fun testImportFailureMissingLanguage() = runTest {
        val file: PlatformFile = mockk()
        val tempDir = Path("/tmp/glossary")
        val glossaryJsonPath = Path(tempDir, "glossary.json")
        val resourceZipPath = Path(tempDir, "en_ulb.zip")
        
        val glossaryJson = """
            {
                "id": "remote-g1",
                "code": "G1",
                "sourceLanguage": "en",
                "targetLanguage": "es",
                "version": 1,
                "createdAt": "2024-01-01T00:00:00",
                "updatedAt": "2024-01-01T00:00:00",
                "resource": {
                    "language": "en",
                    "type": "ulb",
                    "version": "1"
                },
                "phrases": [],
                "pendingPhrases": []
            }
        """.trimIndent()

        val resource = Resource(
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

        coEvery { fileSystemProvider.createTempDir(any()) } returns tempDir
        coEvery { fileSystemProvider.extractZip(any(), any()) } returns Unit
        coEvery { fileSystemProvider.exists(glossaryJsonPath) } returns true
        coEvery { fileSystemProvider.readFile(glossaryJsonPath) } returns glossaryJson
        coEvery { fileSystemProvider.exists(resourceZipPath) } returns true
        every { resourceContainerAccessor.read(any<Path>()) } returns resource
        coEvery { repository.addResource(any()) } returns Unit
        coEvery { repository.getResource("en", "ulb") } returns resource
        coEvery { fileSystemProvider.saveSource(any<Path>(), any()) } returns Path("/sources/en_ulb.zip")

        // Mock database languages lookup returning null to trigger failure
        coEvery { repository.getLanguage("en") } returns null
        coEvery { repository.getLanguage("es") } returns Language("es", "Spanish", "ltr")

        try {
            importGlossary(file)
            kotlin.test.fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Source language not found in database", e.message)
        }
    }

    @Test
    fun testFindExistingReturnsMatchingGlossary() = runTest {
        val file: PlatformFile = mockk()
        val glossaryJson = """
            {
                "code": "G1",
                "sourceLanguage": "en",
                "targetLanguage": "es",
                "version": 1,
                "createdAt": "2024-01-01T00:00:00",
                "updatedAt": "2024-01-01T00:00:00",
                "resource": {
                    "language": "en",
                    "type": "ulb",
                    "version": "1"
                },
                "phrases": []
            }
        """.trimIndent()

        val english = Language("en", "English", "ltr")
        val spanish = Language("es", "Spanish", "ltr")
        val french = Language("fr", "French", "ltr")
        val existing = Glossary(code = "G1", sourceLanguage = english, targetLanguage = spanish, version = 1, id = "g1")
        val otherTarget = Glossary(code = "G1", sourceLanguage = english, targetLanguage = french, version = 1, id = "g2")

        coEvery { fileSystemProvider.readZipEntry(file, "glossary.json") } returns glossaryJson
        coEvery { repository.getGlossaries() } returns listOf(otherTarget, existing)

        assertEquals(existing, importGlossary.findExisting(file))
    }

    @Test
    fun testFindExistingReturnsNullWhenNoMatch() = runTest {
        val file: PlatformFile = mockk()
        val glossaryJson = """
            {
                "code": "G2",
                "sourceLanguage": "en",
                "targetLanguage": "es",
                "version": 1,
                "createdAt": "2024-01-01T00:00:00",
                "updatedAt": "2024-01-01T00:00:00",
                "resource": {
                    "language": "en",
                    "type": "ulb",
                    "version": "1"
                },
                "phrases": []
            }
        """.trimIndent()

        val english = Language("en", "English", "ltr")
        val spanish = Language("es", "Spanish", "ltr")
        val existing = Glossary(code = "G1", sourceLanguage = english, targetLanguage = spanish, version = 1, id = "g1")

        coEvery { fileSystemProvider.readZipEntry(file, "glossary.json") } returns glossaryJson
        coEvery { repository.getGlossaries() } returns listOf(existing)

        assertNull(importGlossary.findExisting(file))
    }

    @Test
    fun testFindExistingReturnsNullWhenGlossaryJsonMissing() = runTest {
        val file: PlatformFile = mockk()

        coEvery { fileSystemProvider.readZipEntry(file, "glossary.json") } returns null

        assertNull(importGlossary.findExisting(file))
    }
}
