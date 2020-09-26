package com.anggrayudi.storage

import androidx.fragment.app.Fragment
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Created on 26/09/20
 *
 * @author Anggrayudi H
 */
class SimpleStorageTest {

    lateinit var storage: SimpleStorage

    @Before
    fun setUp() {
        storage = SimpleStorage(mock<Fragment>())
    }

    @Test
    fun findSubFolders() {
        val uris = mutableListOf(
            "a/b/c",
            "y/x",
            "y/x",
            "a/b/z",
            "a/b/d",
            "a/b/d/g",
            "x/a",
            "y",
            "a/b",
        )

        val expected = setOf(
            "a/b/d/g",
            "a/b/c",
            "a/b/z",
            "a/b/d",
            "y/x"
        )

        assertEquals(storage.findSubFolders(uris), expected)
    }
}