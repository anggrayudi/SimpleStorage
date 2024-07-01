package com.anggrayudi.storage.sample.activity

import android.os.Bundle
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.afollestad.materialdialogs.list.listItems
import com.anggrayudi.storage.callback.SingleFileConflictCallback
import com.anggrayudi.storage.file.MimeType
import com.anggrayudi.storage.file.decompressZip
import com.anggrayudi.storage.file.fullName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.result.ZipDecompressionResult
import com.anggrayudi.storage.sample.databinding.ActivityFileDecompressionBinding
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Created on 04/01/22
 * @author Anggrayudi H
 */
class FileDecompressionActivity : BaseActivity() {

    private lateinit var binding: ActivityFileDecompressionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileDecompressionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSimpleStorage()
        binding.btnStartDecompress.setOnClickListener { startDecompress() }
    }

    private fun setupSimpleStorage() {
        storageHelper.onFileSelected = { _, files ->
            val file = files.first()
            binding.layoutDecompressFileSrcZip.tvFilePath.run {
                tag = file
                text = file.fullName
            }
        }
        binding.layoutDecompressFileSrcZip.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(filterMimeTypes = arrayOf(MimeType.ZIP))
        }

        storageHelper.onFolderSelected = { _, folder ->
            binding.layoutDecompressFileDestFolder.tvFilePath.run {
                tag = folder
                text = folder.getAbsolutePath(context)
            }
        }
        binding.layoutDecompressFileDestFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker()
        }
    }

    private fun startDecompress() {
        val zipFile = binding.layoutDecompressFileSrcZip.tvFilePath.tag as? DocumentFile
        if (zipFile == null) {
            Toast.makeText(this, "Please select source ZIP file", Toast.LENGTH_SHORT).show()
            return
        }
        val targetFolder = binding.layoutDecompressFileDestFolder.tvFilePath.tag as? DocumentFile
        if (targetFolder == null) {
            Toast.makeText(this, "Please select destination folder", Toast.LENGTH_SHORT).show()
            return
        }
        ioScope.launch {
            zipFile.decompressZip(applicationContext, targetFolder, onConflict = object : SingleFileConflictCallback<DocumentFile>(uiScope) {
                var actionForAllConflicts: ConflictResolution? = null

                override fun onFileConflict(destinationFile: DocumentFile, action: FileConflictAction) {
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
                            val resolution = ConflictResolution.entries[index]
                            if (doForAll) {
                                actionForAllConflicts = resolution
                            }
                            action.confirmResolution(resolution)
                        }
                        .show()
                }
            }).collect {
                when (it) {
                    is ZipDecompressionResult.Validating -> Timber.d("Validating")
                    is ZipDecompressionResult.Decompressing -> Timber.d("Decompressing")
                    is ZipDecompressionResult.Completed -> uiScope.launch {
                        Toast.makeText(
                            applicationContext,
                            "Decompressed ${it.totalFilesDecompressed} files from ${zipFile.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is ZipDecompressionResult.Error -> uiScope.launch {
                        Toast.makeText(applicationContext, "${it.errorCode}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}