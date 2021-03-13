package com.anggrayudi.storage.file

import android.os.Environment
import com.anggrayudi.storage.file.DocumentFileCompat.PRIMARY
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Created on 12/1/20
 *
 * @author Anggrayudi H
 */
@Suppress("DEPRECATION")
class DocumentFileCompatTest {

    @Before
    fun setUp() {
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } answers { File("/storage/emulated/0") }
    }

    @Test
    fun getStorageId() {
        assertEquals(PRIMARY, DocumentFileCompat.getStorageId("/storage/emulated/0"))
        assertEquals(PRIMARY, DocumentFileCompat.getStorageId("/storage/emulated/0/Music"))
        assertEquals(PRIMARY, DocumentFileCompat.getStorageId("primary:Music"))
        assertEquals("AAAA-BBBB", DocumentFileCompat.getStorageId("/storage/AAAA-BBBB/Music"))
        assertEquals("AAAA-BBBB", DocumentFileCompat.getStorageId("AAAA-BBBB:Music"))
    }

    @Test
    fun getBasePath() {
        assertEquals("", DocumentFileCompat.getBasePath("/storage/emulated/0"))
        assertEquals("", DocumentFileCompat.getBasePath("AAAA-BBBB:"))
        assertEquals("Music", DocumentFileCompat.getBasePath("/storage/emulated/0/Music"))
        assertEquals("Music", DocumentFileCompat.getBasePath("primary:Music"))
        assertEquals("Music/Pop", DocumentFileCompat.getBasePath("/storage/AAAA-BBBB//Music///Pop/"))
        assertEquals("Music", DocumentFileCompat.getBasePath("AAAA-BBBB:Music"))
    }

    @Test
    fun findUniqueParents() {
        val folderPaths = listOf(
            "/storage/9016-4EF8/Downloads",
            "/storage/9016-4EF8/Downloads/Archive",
            "/storage/9016-4EF8/Video",
            "/storage/9016-4EF8/Music",
            "/storage/9016-4EF8/Music/Favorites/Pop",
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Music Indo",
            "/folder/subFolder/b/c",
            "/folder/subFolder/b",
            "/folder/subFolder/b/d",
            "primary:Alarm/Morning",
            "primary:Alarm",
        )

        val expected = listOf(
            "/storage/9016-4EF8/Downloads",
            "/storage/9016-4EF8/Video",
            "/storage/9016-4EF8/Music",
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Music Indo",
            "/folder/subFolder/b",
            "/storage/emulated/0/Alarm",
        )

        val results = DocumentFileCompat.findUniqueParents(folderPaths)
        assertEquals(expected, results)
    }

    @Test
    fun findUniqueDeepestSubFolders() {
        val folderPaths = listOf(
            "/storage/9016-4EF8/Downloads",
            "/storage/9016-4EF8/Downloads/Archive",
            "/storage/9016-4EF8/Video",
            "/storage/9016-4EF8/Music",
            "/storage/9016-4EF8/Music/Favorites/Pop",
            "/storage/emulated/0/Music",
            "/tree/primary/b/c",
            "/tree/primary/b",
            "/tree/primary/b/d",
            "primary:Alarm/Morning",
            "primary:Alarm",
        )

        val expected = listOf(
            "/storage/9016-4EF8/Downloads/Archive",
            "/storage/9016-4EF8/Video",
            "/storage/9016-4EF8/Music/Favorites/Pop",
            "/storage/emulated/0/Music",
            "/tree/primary/b/c",
            "/tree/primary/b/d",
            "/storage/emulated/0/Alarm/Morning",
        )

        val results = DocumentFileCompat.findUniqueDeepestSubFolders(folderPaths)
        assertEquals(expected, results)
    }
}