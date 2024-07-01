package com.anggrayudi.storage.sample.activity

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.MimeType
import com.anggrayudi.storage.file.compressToZip
import com.anggrayudi.storage.file.fullName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.result.ZipCompressionResult
import com.anggrayudi.storage.sample.databinding.ActivityFileCompressionBinding
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Created on 03/01/22
 * @author Anggrayudi H
 */
class FileCompressionActivity : BaseActivity() {

    private lateinit var binding: ActivityFileCompressionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileCompressionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSimpleStorage()
        binding.btnStartCompress.setOnClickListener { startCompress() }
    }

    private fun setupSimpleStorage() {
        storageHelper.onFileCreated = { _, file ->
            binding.layoutCompressFilesDestZipFile.tvFilePath.updateFileSelectionView(file)
        }
        binding.layoutCompressFilesDestZipFile.btnBrowse.setOnClickListener {
            storageHelper.createFile(MimeType.ZIP, "Test compress")
        }

        storageHelper.onFileSelected = { requestCode, files ->
            when (requestCode) {
                REQUEST_CODE_PICK_MEDIA_1 -> binding.layoutCompressFilesSrcMedia1.tvFilePath.updateFileSelectionView(files)
                REQUEST_CODE_PICK_MEDIA_2 -> binding.layoutCompressFilesSrcMedia2.tvFilePath.updateFileSelectionView(files)
            }
        }
        binding.layoutCompressFilesSrcMedia1.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_MEDIA_1, true)
        }
        binding.layoutCompressFilesSrcMedia2.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_MEDIA_2, true)
        }

        storageHelper.onFolderSelected = { requestCode, folder ->
            when (requestCode) {
                REQUEST_CODE_PICK_FOLDER_1 -> binding.layoutCompressFilesSrcFolder1.tvFilePath.updateFileSelectionView(folder)
                REQUEST_CODE_PICK_FOLDER_2 -> binding.layoutCompressFilesSrcFolder2.tvFilePath.updateFileSelectionView(folder)
            }
        }
        binding.layoutCompressFilesSrcFolder1.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_FOLDER_1)
        }
        binding.layoutCompressFilesSrcFolder2.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_FOLDER_2)
        }
    }

    private fun TextView.updateFileSelectionView(files: List<DocumentFile>) {
        tag = files
        text = files.joinToString(", ") { it.fullName }
    }

    private fun TextView.updateFileSelectionView(file: DocumentFile) {
        tag = file
        text = file.getAbsolutePath(context)
    }

    @Suppress("UNCHECKED_CAST")
    private fun startCompress() {
        val targetZip = binding.layoutCompressFilesDestZipFile.tvFilePath.tag as? DocumentFile
        if (targetZip == null) {
            Toast.makeText(this, "Please select destination ZIP file", Toast.LENGTH_SHORT).show()
            return
        }

        val files = mutableListOf<DocumentFile>()
        (binding.layoutCompressFilesSrcMedia1.tvFilePath.tag as? List<DocumentFile>)?.let { files.addAll(it) }
        (binding.layoutCompressFilesSrcMedia2.tvFilePath.tag as? List<DocumentFile>)?.let { files.addAll(it) }
        (binding.layoutCompressFilesSrcFolder1.tvFilePath.tag as? DocumentFile)?.let { files.add(it) }
        (binding.layoutCompressFilesSrcFolder2.tvFilePath.tag as? DocumentFile)?.let { files.add(it) }

        ioScope.launch {
            files.compressToZip(applicationContext, targetZip)
                .collect { result ->
                    when (result) {
                        is ZipCompressionResult.CountingFiles -> {
                            // show a notification or dialog with indeterminate progress bar
                        }

                        is ZipCompressionResult.Compressing -> {
                            Timber.d("onReport() -> ${result.progress.toInt()}% | Compressed ${result.fileCount} files")
                        }

                        is ZipCompressionResult.Completed -> uiScope.launch {
                            Timber.d("onCompleted() -> Compressed ${result.totalFilesCompressed} with compression rate %.2f", result.compressionRate)
                            Toast.makeText(applicationContext, "Successfully compressed ${result.totalFilesCompressed} files", Toast.LENGTH_SHORT).show()
                        }

                        is ZipCompressionResult.DeletingEntryFiles -> {
                            // show a notification or dialog with indeterminate progress bar
                        }

                        is ZipCompressionResult.Error -> uiScope.launch {
                            Timber.d("onFailed() -> ${result.errorCode}: ${result.message}")
                            Toast.makeText(applicationContext, "Error compressing files: ${result.errorCode}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
    }

    companion object {
        private const val REQUEST_CODE_PICK_MEDIA_1 = 5
        private const val REQUEST_CODE_PICK_MEDIA_2 = 6
        private const val REQUEST_CODE_PICK_FOLDER_1 = 7
        private const val REQUEST_CODE_PICK_FOLDER_2 = 8
    }
}