package com.anggrayudi.storage.sample.activity

import android.os.Bundle
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.afollestad.materialdialogs.list.listItems
import com.anggrayudi.storage.callback.FileCallback
import com.anggrayudi.storage.callback.ZipDecompressionCallback
import com.anggrayudi.storage.file.MimeType
import com.anggrayudi.storage.file.decompressZip
import com.anggrayudi.storage.file.fullName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.sample.R
import kotlinx.android.synthetic.main.activity_file_decompression.*
import kotlinx.android.synthetic.main.view_file_picked.view.*
import kotlinx.coroutines.launch

/**
 * Created on 04/01/22
 * @author Anggrayudi H
 */
class FileDecompressionActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_decompression)
        setupSimpleStorage()
        btnStartDecompress.setOnClickListener { startDecompress() }
    }

    private fun setupSimpleStorage() {
        storageHelper.onFileSelected = { _, files ->
            val file = files.first()
            layoutDecompressFile_srcZip.run {
                tag = file
                tvFilePath.text = file.fullName
            }
        }
        layoutDecompressFile_srcZip.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(filterMimeTypes = arrayOf(MimeType.ZIP))
        }

        storageHelper.onFolderSelected = { _, folder ->
            layoutDecompressFile_destFolder.run {
                tag = folder
                tvFilePath.text = folder.getAbsolutePath(context)
            }
        }
        layoutDecompressFile_destFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker()
        }
    }

    private fun startDecompress() {
        val zipFile = layoutDecompressFile_srcZip.tag as? DocumentFile
        if (zipFile == null) {
            Toast.makeText(this, "Please select source ZIP file", Toast.LENGTH_SHORT).show()
            return
        }
        val targetFolder = layoutDecompressFile_destFolder.tag as? DocumentFile
        if (targetFolder == null) {
            Toast.makeText(this, "Please select destination folder", Toast.LENGTH_SHORT).show()
            return
        }
        ioScope.launch {
            zipFile.decompressZip(applicationContext, targetFolder, object : ZipDecompressionCallback<DocumentFile>(uiScope) {
                var actionForAllConflicts: FileCallback.ConflictResolution? = null

                override fun onFileConflict(destinationFile: DocumentFile, action: FileCallback.FileConflictAction) {
                    actionForAllConflicts?.let {
                        action.confirmResolution(it)
                        return
                    }

                    var doForAll = false
                    MaterialDialog(this@FileDecompressionActivity)
                        .cancelable(false)
                        .title(text = "Conflict Found")
                        .message(text = "File \"${destinationFile.name}\" already exists in destination. What's your action?")
                        .checkBoxPrompt(text = "Apply to all") { doForAll = it }
                        .listItems(items = mutableListOf("Replace", "Create New", "Skip Duplicate")) { _, index, _ ->
                            val resolution = FileCallback.ConflictResolution.values()[index]
                            if (doForAll) {
                                actionForAllConflicts = resolution
                            }
                            action.confirmResolution(resolution)
                        }
                        .show()
                }

                override fun onCompleted(
                    zipFile: DocumentFile,
                    targetFolder: DocumentFile,
                    decompressionInfo: DecompressionInfo
                ) {
                    Toast.makeText(
                        applicationContext,
                        "Decompressed ${decompressionInfo.totalFilesDecompressed} files from ${zipFile.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onFailed(errorCode: ErrorCode) {
                    Toast.makeText(applicationContext, "$errorCode", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}