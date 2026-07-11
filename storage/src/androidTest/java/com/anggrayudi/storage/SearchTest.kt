package com.anggrayudi.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 6 - search, on-device regression of the 2.3.0 recursive-duplication fix
 * (V3_TEST_CASES.md TC-50). The bug (`ANALYSIS.md`, "Bug: Duplikasi Hasil `search()` Rekursif")
 * was `fileTree.addAll(walkFileTreeForSearch(fileTree, ...))` duplicating the accumulator on every
 * subfolder visited; the current source no longer does this (see
 * `DocumentFileExt.kt` `walkFileTreeForSearch`), so this is confirming the fix holds on a real
 * device, not just in the JVM simulation the analysis was based on.
 */
@RunWith(AndroidJUnit4::class)
class SearchTest {

  private val context = targetContext()
  private lateinit var playground: File

  @Before
  fun setUp() {
    playground = newPlaygroundDir("tc50")
  }

  @After
  fun tearDown() {
    playground.deleteRecursivelyOrThrow()
  }

  // TC-50: Recursive search, no duplicates
  @Test
  fun tc50_recursiveSearchNoDuplicates() = runBlocking {
    val root = File(playground, "root").apply { mkdirs() }
    File(root, "file1.txt").writeText("1")
    val folderA = File(root, "folderA").apply { mkdirs() }
    File(folderA, "file2.txt").writeText("2")
    val folderB = File(root, "folderB").apply { mkdirs() }
    File(folderB, "file3.txt").writeText("3")

    val rootStorageFile = StorageFile.from(context, root)
    val emissions = rootStorageFile.search(recursive = true).toList()
    val terminal = emissions.last()

    assertEquals(
      "expected exactly 5 unique results, got: ${terminal.map { it.name }}",
      5,
      terminal.size,
    )
    val uniqueUris = terminal.map { it.uri }.toSet()
    assertEquals("results must not contain duplicates", 5, uniqueUris.size)
  }
}
