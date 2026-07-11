package com.anggrayudi.storage.file

import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Created on 7/11/26
 *
 * @author Anggrayudi H
 */
class DocumentFileSearchTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var root: DocumentFile

  /**
   * Directory tree under test:
   * ```
   * root/
   * ├── a.txt
   * └── sub1/
   *     ├── b.txt
   *     └── sub2/
   *         └── c.txt
   * ```
   */
  @Before
  fun setUp() {
    val rootFolder = tempFolder.newFolder("root")
    File(rootFolder, "a.txt").createNewFile()
    val sub1 = File(rootFolder, "sub1").apply { mkdirs() }
    File(sub1, "b.txt").createNewFile()
    val sub2 = File(sub1, "sub2").apply { mkdirs() }
    File(sub2, "c.txt").createNewFile()
    root = DocumentFile.fromFile(rootFolder)
  }

  private fun search(
    recursive: Boolean = true,
    documentType: DocumentFileType = DocumentFileType.ANY,
    name: String = "",
    regex: Regex? = null,
  ): List<String> = runBlocking {
    root.search(recursive, documentType, name = name, regex = regex).first().map {
      it.name.orEmpty()
    }
  }

  @Test
  fun `recursive search emits each entry exactly once`() {
    val results = search()
    // Guards against results being appended to themselves while walking sub folders,
    // which used to duplicate all entries collected so far on every sub folder visit.
    assertEquals(5, results.size)
    assertEquals(setOf("a.txt", "sub1", "b.txt", "sub2", "c.txt"), results.toSet())
  }

  @Test
  fun `recursive search with FILE type returns files only`() {
    val results = search(documentType = DocumentFileType.FILE)
    assertEquals(3, results.size)
    assertEquals(setOf("a.txt", "b.txt", "c.txt"), results.toSet())
  }

  @Test
  fun `recursive search with FOLDER type returns folders only`() {
    val results = search(documentType = DocumentFileType.FOLDER)
    assertEquals(2, results.size)
    assertEquals(setOf("sub1", "sub2"), results.toSet())
  }

  @Test
  fun `recursive search by exact name finds nested file once`() {
    assertEquals(listOf("c.txt"), search(name = "c.txt"))
  }

  @Test
  fun `recursive search by regex matches nested entries once`() {
    val results = search(regex = Regex("^.*sub.*$"))
    assertEquals(2, results.size)
    assertEquals(setOf("sub1", "sub2"), results.toSet())
  }

  @Test
  fun `non-recursive search returns direct children only`() {
    val results = search(recursive = false)
    assertEquals(2, results.size)
    assertEquals(setOf("a.txt", "sub1"), results.toSet())
  }
}
