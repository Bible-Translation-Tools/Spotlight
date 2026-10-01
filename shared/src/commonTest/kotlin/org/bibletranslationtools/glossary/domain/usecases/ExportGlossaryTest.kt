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
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import kotlin.test.BeforeTest
import kotlin.test.Test

class ExportGlossaryTest {

    private val repository: GlossaryRepository = mockk()
    private val fileSystemProvider: FileSystemProvider = mockk()
    private lateinit var exportGlossary: ExportGlossary

    @BeforeTest
    fun setUp() {
        exportGlossary = ExportGlossary(repository, fileSystemProvider)
    }

    @Test
    fun testExportSuccess() = runTest {
        val glossaryId = "g1"
        val sourceLang = Language("en", "English", "ltr")
        val targetLang = Language("es", "Spanish", "ltr")
        val glossary = Glossary(
            id = glossaryId,
            code = "G1",
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            version = 1,
            resourceId = 1L,
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            updatedAt = LocalDateTime(2024, 1, 1, 0, 0)
        )
        val resource = Resource(
            id = 1L,
            lang = "en",
            type = "ulb",
            version = "1",
            format = "usfm",
            url = "",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0)
        )
        val phrases = emptyList<Phrase>()
        val targetFile: PlatformFile = mockk()
        val tempDir = Path("/tmp/glossary")

        coEvery { repository.getResource(1L) } returns resource
        coEvery { repository.getPhrases(glossaryId) } returns phrases
        coEvery { repository.getPendingPhrases(glossaryId) } returns phrases
        coEvery { fileSystemProvider.createTempDir(any()) } returns tempDir
        every { fileSystemProvider.sources } returns Path("/sources")
        
        coEvery { fileSystemProvider.copyFileToDir(any(), any()) } returns Unit
        coEvery { fileSystemProvider.zipDirectory(any(), any()) } returns Unit
        
        coEvery { fileSystemProvider.writeFile(any<String>(), any()) } returns Unit
        coEvery { fileSystemProvider.createDirectories(any()) } returns Unit
        coEvery { fileSystemProvider.exists(any()) } returns true

        exportGlossary(glossary, targetFile)

        coVerify {
            repository.getResource(1L)
            repository.getPhrases(glossaryId)
            fileSystemProvider.zipDirectory(tempDir, targetFile)
        }
    }

    @Test
    fun testExportFailureMissingResource() = runTest {
        val glossaryId = "g1"
        val sourceLang = Language("en", "English", "ltr")
        val targetLang = Language("es", "Spanish", "ltr")
        val glossary = Glossary(
            id = glossaryId,
            code = "G1",
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            version = 1,
            resourceId = 1L,
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            updatedAt = LocalDateTime(2024, 1, 1, 0, 0)
        )
        val targetFile: PlatformFile = mockk()

        coEvery { repository.getResource(1L) } returns null

        try {
            exportGlossary(glossary, targetFile)
            kotlin.test.fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            kotlin.test.assertEquals("Resource not found", e.message)
        }
    }

    @Test
    fun testExportFailureMissingResourceFile() = runTest {
        val glossaryId = "g1"
        val sourceLang = Language("en", "English", "ltr")
        val targetLang = Language("es", "Spanish", "ltr")
        val glossary = Glossary(
            id = glossaryId,
            code = "G1",
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            version = 1,
            resourceId = 1L,
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            updatedAt = LocalDateTime(2024, 1, 1, 0, 0)
        )
        val resource = Resource(
            id = 1L,
            lang = "en",
            type = "ulb",
            version = "1",
            format = "usfm",
            url = "",
            filename = "en_ulb.zip",
            createdAt = LocalDateTime(2024, 1, 1, 0, 0),
            modifiedAt = LocalDateTime(2024, 1, 1, 0, 0)
        )
        val phrases = emptyList<Phrase>()
        val targetFile: PlatformFile = mockk()
        val tempDir = Path("/tmp/glossary")
        val resourceFile = Path("/sources", resource.filename)

        coEvery { repository.getResource(1L) } returns resource
        coEvery { repository.getPhrases(glossaryId) } returns phrases
        coEvery { repository.getPendingPhrases(glossaryId) } returns phrases
        coEvery { fileSystemProvider.createTempDir(any()) } returns tempDir
        every { fileSystemProvider.sources } returns Path("/sources")
        
        coEvery { fileSystemProvider.writeFile(any<String>(), any()) } returns Unit
        coEvery { fileSystemProvider.createDirectories(any()) } returns Unit
        coEvery { fileSystemProvider.exists(resourceFile) } returns false

        try {
            exportGlossary(glossary, targetFile)
            kotlin.test.fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            kotlin.test.assertEquals("Resource file not found", e.message)
        }
    }
}
