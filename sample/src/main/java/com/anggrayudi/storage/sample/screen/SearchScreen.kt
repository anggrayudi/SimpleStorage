package com.anggrayudi.storage.sample.screen

import com.anggrayudi.storage.file.DocumentFileType
import com.anggrayudi.storage.sample.SampleScreen
import com.anggrayudi.storage.search
import kotlinx.coroutines.flow.collect

/** `search` emits snapshots while it walks, so long searches can update the UI as they go. */
class SearchScreen : SampleScreen() {

  override val screenTitle = "Search"

  override val screenSummary = "Recursive search over the app's own folder, filtered by name."

  override fun SampleScreen.buildScreen() {
    val root = playground(this@SearchScreen)

    button("Find every *.txt") {
      root
        .search(recursive = true, documentType = DocumentFileType.FILE, regex = Regex(".*\\.txt"))
        .collect { log("${it.size} match(es): ${it.take(10).map { file -> file.name }}") }
    }

    button("Find folders only") {
      root.search(recursive = true, documentType = DocumentFileType.FOLDER).collect {
        log("${it.size} folder(s): ${it.take(10).map { file -> file.name }}")
      }
    }

    button("Find by name: report.txt") {
      root.search(recursive = true, name = "report.txt").collect {
        log("Found: ${it.map { f -> f.absolutePath }}")
      }
    }
  }
}
