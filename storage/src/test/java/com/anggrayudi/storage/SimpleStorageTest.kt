package com.anggrayudi.storage

import android.content.ContentResolver
import android.content.UriPermission
import android.net.Uri
import android.os.Build
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Created on 26/09/20
 *
 * @author Anggrayudi H
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.O_MR1])
class SimpleStorageTest {

    @Ignore("Still don't know how to test thread { } with Mockito answer.")
    @Test
    fun cleanupRedundantUriPermissions() {
        val persistedUris = listOf(
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/c",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/cc",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/cbd",
            "content://com.android.externalstorage.documents/tree/primary%3A y/x",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/z",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/d",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/d/g",
            "content://com.android.externalstorage.documents/tree/primary%3A x/a",
            "content://com.android.externalstorage.documents/tree/primary%3A y",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b",
        ).map { Uri.parse(it) }

        val persistedUriPermissions = persistedUris.map {
            val uriPermission = mock<UriPermission>()
            whenever(uriPermission.isReadPermission).thenReturn(true)
            whenever(uriPermission.isWritePermission).thenReturn(true)
            whenever(uriPermission.uri).thenReturn(it)
            uriPermission
        }
        val resolver = mock<ContentResolver>()
        whenever(resolver.persistedUriPermissions).thenReturn(persistedUriPermissions)

        val revokedUris = setOf(
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/c",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/cc",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/cbd",
            "content://com.android.externalstorage.documents/tree/primary%3A y/x",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/z",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/d",
            "content://com.android.externalstorage.documents/tree/primary%3A a/b/d/g",
        ).map { Uri.parse(it) }

        SimpleStorage.cleanupRedundantUriPermissions(resolver)

        val uriCaptor = argumentCaptor<Uri>()
        verify(resolver, times(revokedUris.size)).releasePersistableUriPermission(uriCaptor.capture(), any())
        assertEquals(revokedUris, uriCaptor.allValues)
    }
}