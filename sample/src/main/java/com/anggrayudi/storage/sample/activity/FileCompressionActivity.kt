package com.anggrayudi.storage.sample.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.callback.ZipCompressionCallback
import com.anggrayudi.storage.file.MimeType
import com.anggrayudi.storage.file.compressToZip
import com.anggrayudi.storage.file.fullName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.sample.R
import kotlinx.android.synthetic.main.activity_file_compression.*
import kotlinx.android.synthetic.main.view_file_picked.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Created on 03/01/22
 * @author Anggrayudi H
 */
class FileCompressionActivity : AppCompatActivity() {

    private val job = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + job)
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private val storageHelper = SimpleStorageHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_compression)
        setupSimpleStorage(savedInstanceState)
        btnStartCompress.setOnClickListener { startCompress() }
    }

    private fun setupSimpleStorage(savedInstanceState: Bundle?) {
        savedInstanceState?.let { storageHelper.onRestoreInstanceState(it) }
        storageHelper.onFileCreated = { _, file ->
            layoutCompressFiles_destZipFile.updateFileSelectionView(file)
        }
        layoutCompressFiles_destZipFile.btnBrowse.setOnClickListener {
            storageHelper.createFile(MimeType.ZIP, "Test compress")
        }

        storageHelper.onFileSelected = { requestCode, files ->
            when (requestCode) {
                REQUEST_CODE_PICK_MEDIA_1 -> layoutCompressFiles_srcMedia1.updateFileSelectionView(files)
                REQUEST_CODE_PICK_MEDIA_2 -> layoutCompressFiles_srcMedia2.updateFileSelectionView(files)
            }
        }
        layoutCompressFiles_srcMedia1.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_MEDIA_1, true)
        }
        layoutCompressFiles_srcMedia2.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_MEDIA_2, true)
        }

        storageHelper.onFolderSelected = { requestCode, folder ->
            when (requestCode) {
                REQUEST_CODE_PICK_FOLDER_1 -> layoutCompressFiles_srcFolder1.updateFileSelectionView(folder)
                REQUEST_CODE_PICK_FOLDER_2 -> layoutCompressFiles_srcFolder2.updateFileSelectionView(folder)
            }
        }
        layoutCompressFiles_srcFolder1.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_FOLDER_1)
        }
        layoutCompressFiles_srcFolder2.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_FOLDER_2)
        }
    }

    private fun View.updateFileSelectionView(files: List<DocumentFile>) {
        tag = files
        tvFilePath.text = files.joinToString(", ") { it.fullName }
    }

    private fun View.updateFileSelectionView(file: DocumentFile) {
        tag = file
        tvFilePath.text = file.getAbsolutePath(context)
    }

    @Suppress("UNCHECKED_CAST")
    private fun startCompress() {
        val targetZip = layoutCompressFiles_destZipFile.tag as? DocumentFile
        if (targetZip == null) {
            Toast.makeText(this, "Please select destination ZIP file", Toast.LENGTH_SHORT).show()
            return
        }

        val files = mutableListOf<DocumentFile>()
        (layoutCompressFiles_srcMedia1.tag as? List<DocumentFile>)?.let { files.addAll(it) }
        (layoutCompressFiles_srcMedia2.tag as? List<DocumentFile>)?.let { files.addAll(it) }
        (layoutCompressFiles_srcFolder1.tag as? DocumentFile)?.let { files.add(it) }
        (layoutCompressFiles_srcFolder2.tag as? DocumentFile)?.let { files.add(it) }

        ioScope.launch {
            files.compressToZip(applicationContext, targetZip, callback = object : ZipCompressionCallback(uiScope) {
                override fun onCountingFiles() {
                    // show a notification or dialog with indeterminate progress bar
                }

                override fun onStart(files: List<DocumentFile>, workerThread: Thread): Long = 500

                override fun onReport(report: Report) {
                    Timber.d("onReport() -> ${report.progress.toInt()}% | Compressed ${report.fileCount} files")
                }

                override fun onCompleted(zipFile: DocumentFile, bytesCompressed: Long, totalFilesCompressed: Int, compressionRate: Float) {
                    Timber.d("onCompleted() -> Compressed $totalFilesCompressed with compression rate %.2f", compressionRate)
                    Toast.makeText(applicationContext, "Successfully compressed $totalFilesCompressed files", Toast.LENGTH_SHORT).show()
                }

                override fun onFailed(errorCode: ErrorCode, message: String?) {
                    Timber.d("onFailed() -> $errorCode: $message")
                    Toast.makeText(applicationContext, "Error compressing files: $errorCode", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storageHelper.onRestoreInstanceState(savedInstanceState)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_PICK_MEDIA_1 = 5
        private const val REQUEST_CODE_PICK_MEDIA_2 = 6
        private const val REQUEST_CODE_PICK_FOLDER_1 = 7
        private const val REQUEST_CODE_PICK_FOLDER_2 = 8
    }
}