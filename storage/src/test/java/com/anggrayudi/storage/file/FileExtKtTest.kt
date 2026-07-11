package com.anggrayudi.storage.file

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Created on 12/16/20
 *
 * @author Anggrayudi H
 */
class FileExtKtTest {

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun autoIncrementFileName() {
    val videoFolder = tempFolder.newFolder("Video")

    val existingFiles =
      listOf(
        "Action",
        "Horror",
        "Horror (1)",
        "Bunny.mp4",
        "Bunny (3).mp4",
        "MyMovie.mp4",
        "Papa Nakal.mp4",
        "Collections (24).gz",
        "Book",
        "Book (10)",
      )
    existingFiles.forEach { File(videoFolder, it).createNewFile() }

    val filesToCreate =
      listOf(
        "Sci-Fi",
        "Horror",
        "Bunny.mp4",
        "Bunny (2).mp4",
        "Collections (24).gz",
        "Collections (28).gz",
        "Book",
      )
    val actual = filesToCreate.map { videoFolder.autoIncrementFileName(it) }

    val expected =
      listOf(
        "Sci-Fi",
        "Horror (2)",
        "Bunny (4).mp4",
        "Bunny (2).mp4",
        "Collections (24) (1).gz",
        "Collections (28).gz",
        "Book (11)",
      )

    assertEquals(expected, actual)
  }
}
