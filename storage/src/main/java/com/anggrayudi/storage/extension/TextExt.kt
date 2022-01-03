@file:JvmName("TextUtils")

package com.anggrayudi.storage.extension

import com.anggrayudi.storage.file.DocumentFileCompat.removeForbiddenCharsFromFilename

/**
 * Created on 19/08/20
 * @author Anggrayudi H
 */

/**
 * Given the following text `abcdeabcjklab` and count how many `abc`, then should return 2.
 */
fun String.count(text: String): Int {
    var index = indexOf(text)
    if (text.isEmpty() || index == -1) {
        return 0
    }
    var count = 0
    do {
        count++
        index = indexOf(text, startIndex = index + text.length)
    } while (index in 1 until length)
    return count
}

fun String.trimFileName() = trim { it <= ' ' || it == '/' }.trimEnd('.')

fun String.normalizeFileName() = removeForbiddenCharsFromFilename().trimFileName()

fun String.trimFileSeparator() = trim('/')

fun String.trimWhiteSpace() = trim { it <= ' ' }

fun String.replaceCompletely(match: String, replaceWith: String) = let {
    var path = it
    do {
        path = path.replace(match, replaceWith)
    } while (path.isNotEmpty() && path.contains(match))
    path
}

fun String.hasParent(parentPath: String): Boolean {
    val parentTree = parentPath.split('/')
        .map { it.trimFileSeparator() }
        .filter { it.isNotEmpty() }

    val subFolderTree = split('/')
        .map { it.trimFileSeparator() }
        .filter { it.isNotEmpty() }

    return parentTree.size <= subFolderTree.size && subFolderTree.take(parentTree.size) == parentTree
}

fun String.childOf(parentPath: String): Boolean {
    val parentTree = parentPath.split('/')
        .map { it.trimFileSeparator() }
        .filter { it.isNotEmpty() }

    val subFolderTree = split('/')
        .map { it.trimFileSeparator() }
        .filter { it.isNotEmpty() }

    return subFolderTree.size > parentTree.size && subFolderTree.take(parentTree.size) == parentTree
}