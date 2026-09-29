package org.bibletranslationtools.glossary.domain

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.bibletranslationtools.glossary.BaseTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSystemProviderTest : BaseTest() {

    private lateinit var fileSystemProvider: FileSystemProvider

    @BeforeTest
    override fun setUp() {
        super.setUp()
        fileSystemProvider = FileSystemProviderImpl()
    }

    @Test
    fun testRootDir() {
        val actual = fileSystemProvider.rootDir
        // rootDir is canonical (e.g. macOS /var -> /private/var), so compare resolved paths
        val expected = SystemFileSystem.resolve(Path(testRootDir, "WaGlossary"))
        assertEquals(expected.toString(), actual.toString())
    }

    @Test
    fun testWriteAndReadFile() = runTest {
        val testFile = Path(fileSystemProvider.rootDir, "test_file.txt")
        val content = "Hello DirectoryProvider!"
        fileSystemProvider.writeFile(content, testFile)

        assertTrue(fileSystemProvider.exists(testFile))
        val readContent = fileSystemProvider.readFile(testFile)
        assertEquals(content, readContent)
    }

    @Test
    fun testWriteAndReadBytes() = runTest {
        val testFile = Path(fileSystemProvider.rootDir, "test_bytes.bin")
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        fileSystemProvider.writeFile(bytes, testFile)

        assertTrue(fileSystemProvider.exists(testFile))
        assertTrue(SystemFileSystem.exists(testFile))
    }

    @Test
    fun testDeleteFile() = runTest {
        val testFile = Path(fileSystemProvider.rootDir, "test_delete.txt")
        fileSystemProvider.writeFile("to be deleted", testFile)
        assertTrue(fileSystemProvider.exists(testFile))

        fileSystemProvider.deleteFile(testFile)
        assertFalse(fileSystemProvider.exists(testFile))
    }

    @Test
    fun testCopyFileToDir() = runTest {
        val sourceFile = Path(fileSystemProvider.rootDir, "source.txt")
        fileSystemProvider.writeFile("copy content", sourceFile)

        val destDir = fileSystemProvider.tempDir
        fileSystemProvider.copyFileToDir(sourceFile, destDir)

        val copiedFile = Path(destDir, "source.txt")
        assertTrue(fileSystemProvider.exists(copiedFile))
        assertEquals("copy content", fileSystemProvider.readFile(copiedFile))
    }

    @Test
    fun testCreateTempFileAndDir() = runTest {
        val tempFile = fileSystemProvider.createTempFile("pref", ".suf")
        assertTrue(fileSystemProvider.exists(tempFile))
        assertTrue(tempFile.name.startsWith("pref"))
        assertTrue(tempFile.name.endsWith(".suf"))

        val tempDir = fileSystemProvider.createTempDir("dir_pref")
        assertTrue(fileSystemProvider.exists(tempDir))
        assertTrue(tempDir.name.startsWith("dir_pref"))
    }

    @Test
    fun testClearTempDir() = runTest {
        val tempFile = fileSystemProvider.createTempFile("pref", ".suf")
        assertTrue(fileSystemProvider.exists(tempFile))

        fileSystemProvider.clearTempDir()
        assertFalse(fileSystemProvider.exists(tempFile))
        assertTrue(fileSystemProvider.exists(fileSystemProvider.tempDir))
    }
}
