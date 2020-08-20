package com.anggrayudi.storage.extension

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