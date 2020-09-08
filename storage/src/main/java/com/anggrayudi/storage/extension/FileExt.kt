package com.anggrayudi.storage.extension

import com.anggrayudi.storage.DocumentFileCompat
import java.io.File

/**
 * Created on 07/09/20
 * @author Anggrayudi H
 */

fun File.avoidDuplicateFileNameFor(filename: String): String {
    return if (File(this, filename).isFile) {
        val baseName = filename.substringBeforeLast('.')
        val ext = filename.substringAfterLast('.')
        val prefix = "$baseName ("
        val lastFile = listFiles { _, name ->
            name.startsWith(prefix) && (DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITH_EXTENSION.matches(name)
                    || DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION.matches(name))
        }.orEmpty().filter { it.isFile }.maxOfOrNull { it.name }
        var count = lastFile.orEmpty().substringAfterLast('(').substringBefore(')').toIntOrNull() ?: 0
        "$baseName (${++count}).$ext"
    } else {
        filename
    }
}

fun File.avoidDuplicateFolderNameFor(folderName: String): String {
    return if (File("$path/$folderName").isDirectory) {
        val prefix = "$folderName ("
        val lastFolder = listFiles { _, name ->
            name.startsWith(prefix) && DocumentFileCompat.FILE_NAME_DUPLICATION_REGEX_WITHOUT_EXTENSION.matches(name)
        }.orEmpty().filter { it.isDirectory }.maxOfOrNull { it.name }
        var count = lastFolder.orEmpty().substringAfterLast('(').substringBefore(')').toIntOrNull() ?: 0
        "$folderName (${++count})"
    } else {
        folderName
    }
}