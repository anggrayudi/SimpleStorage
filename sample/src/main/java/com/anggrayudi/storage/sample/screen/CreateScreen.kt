package com.anggrayudi.storage.sample.screen

import com.anggrayudi.storage.file.CreateMode
import com.anggrayudi.storage.sample.SampleScreen

/** `createFile`/`createFolder` are the programmatic counterpart of the SAF create dialog. */
class CreateScreen : SampleScreen() {

  override val screenTitle = "Create files & folders"

  override val screenSummary =
    "Names may be nested: parents are created as needed, and CreateMode applies to the last segment."

  override fun SampleScreen.buildScreen() {
    val folder = playground(this@CreateScreen)

    button("createFile(\"report.txt\")") {
      val file = folder.createFile("report.txt", "text/plain")
      file?.openOutputStream()?.use { it.write("hello from the sample".toByteArray()) }
      log("Created ${file?.name} at ${file?.absolutePath}")
    }

    button("createFile(\"docs/2026/invoice.txt\") — nested") {
      val file = folder.createFile("docs/2026/invoice.txt", "text/plain")
      log("Created at ${file?.absolutePath}")
    }

    button("createFile(REPLACE) on an existing name") {
      val file = folder.createFile("report.txt", "text/plain", CreateMode.REPLACE)
      log("Replaced, length is now ${file?.length}")
    }

    button("createFolder(\"archive/2026\")") {
      log("Created ${folder.createFolder("archive/2026")?.absolutePath}")
    }

    button("List the playground") {
      log(folder.list().joinToString("\n") { "${if (it.isDirectory) "[dir] " else ""}${it.name}" })
    }
  }
}
