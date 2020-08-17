package com.anggrayudi.storage

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.anggrayudi.storage.callback.StoragePermissionCallback
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener

/**
 * @author Anggrayudi Hardiannico A. (anggrayudi.hardiannico@dana.id)
 * @version SimpleStorage, v 0.0.1 09/08/20 19.08 by Anggrayudi Hardiannico A.
 */
class SimpleStorage(private val activity: Activity) {

    /**
     * It returns an intent to be dispatched via startActivityResult
     */
    fun requireExternalRootAccess(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sm = activity.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.primaryStorageVolume.createOpenDocumentTreeIntent()
        } else {
//            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
//                if (Build.VERSION.SDK_INT >= 26) {
//                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, getStorageRootUri(DocumentFileCompat.PRIMARY))
//                }
//            }
            null
        }
    }

    /**
     * It returns an intent to be dispatched via startActivityResult to access to
     * the first removable no primary storage. This method requires at least Nougat
     * because on previous Android versions there's no reliable way to get the
     * volume/path of SdCard, and no, SdCard != External Storage.
     *
     * @return Null if no storage is found, the intent object otherwise
     */
    @Suppress("DEPRECATION")
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun requireSdCardRootAccess(): Intent? {
        val sm = activity.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        return sm.storageVolumes.firstOrNull { it.isRemovable }?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.createOpenDocumentTreeIntent()
            } else {
                //Access to the entire volume is only available for non-primary volumes
                if (it.isPrimary) {
                    null
                } else {
                    it.createAccessIntent(null)
                }
            }
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?, callback: StoragePermissionCallback) {
        val uri = data?.data ?: return
        val storageId = getStorageId(uri)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && storageId == DocumentFileCompat.PRIMARY) {
            callback.onPathSelected(uri)
            return
        }
        if (isRootUri(uri)) {
            if (!saveUriPermission(uri)) {
                callback.onStoragePermissionDenied()
            }
        } else {
            val rootPath = if (storageId == DocumentFileCompat.PRIMARY) {
                externalStoragePath
            } else {
                "$storageId:"
            }
            callback.onRootPathNotSelected(rootPath)
        }
    }

    private fun saveUriPermission(root: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            activity.contentResolver.takePersistableUriPermission(root, takeFlags)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun isStorageUriPermissionGranted(storageId: String): Boolean {
        val root = DocumentFileCompat.createDocumentUri(storageId)
        return activity.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.isWritePermission && it.uri == root }
    }

    fun requestStoragePermission() {
        Dexter.withContext(activity)
            .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (!report.areAllPermissionsGranted()) {
                        Toast.makeText(activity, "Please grant storage permissions", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>, token: PermissionToken) {
                    // no-op
                }
            })
            .check()
    }

    companion object {

        @Suppress("DEPRECATION")
        val externalStoragePath: String
            get() = Environment.getExternalStorageDirectory().absolutePath

        fun hasStoragePermission(context: Context): Boolean {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        fun isRootUri(uri: Uri): Boolean {
            val path = uri.path ?: return false
            return path.indexOf(':') == path.length - 1
        }

        /**
         * If given [Uri] with path `tree/primary:Downloads/MyVideo.mp4`, then return `primary`
         */
        fun getStorageId(uri: Uri): String = if (uri.scheme == ContentResolver.SCHEME_FILE) {
            DocumentFileCompat.PRIMARY
        } else {
            uri.path!!.substringBefore(':').substringAfter('/')
        }
    }
}