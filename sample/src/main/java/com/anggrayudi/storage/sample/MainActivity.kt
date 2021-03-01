package com.anggrayudi.storage.sample

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.list.listItems
import com.anggrayudi.storage.SimpleStorage
import com.anggrayudi.storage.callback.*
import com.anggrayudi.storage.file.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.listener.multi.BaseMultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.view_file_picked.view.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val job = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + job)
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var storage: SimpleStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView.adapter = StorageInfoAdapter(applicationContext, ioScope, uiScope)

        setupSimpleStorage()
        setupFolderPickerCallback()
        setupFilePickerCallback()
        setupButtonActions()
    }

    private fun setupButtonActions() {
        btnRequestStoragePermission.setOnClickListener {
            Dexter.withContext(this)
                .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(object : BaseMultiplePermissionsListener() {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        val grantStatus = if (report.areAllPermissionsGranted()) "granted" else "denied"
                        Toast.makeText(baseContext, "Storage permissions are $grantStatus", Toast.LENGTH_SHORT).show()
                    }
                }).check()
        }

        btnRequestStorageAccess.setOnClickListener {
            storage.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS)
        }

        btnRequestFullStorageAccess.run {
            isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setOnClickListener { storage.requestFullStorageAccess() }
                true
            } else {
                false
            }
        }

        btnSelectFolder.setOnClickListener {
            storage.openFolderPicker(REQUEST_CODE_PICK_FOLDER)
        }

        btnSelectFile.setOnClickListener {
            storage.openFilePicker(REQUEST_CODE_PICK_FILE)
        }

        setupFileCopy()
        setupFileMove()
    }

    private fun setupSimpleStorage() {
        storage = SimpleStorage(this)
        storage.storageAccessCallback = object : StorageAccessCallback {
            override fun onRootPathNotSelected(requestCode: Int, rootPath: String, rootStorageType: StorageType, uri: Uri) {
                MaterialDialog(this@MainActivity)
                    .message(text = "Please select $rootPath")
                    .negativeButton(android.R.string.cancel)
                    .positiveButton {
                        storage.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS, rootStorageType)
                    }.show()
            }

            override fun onCancelledByUser(requestCode: Int) {
                Toast.makeText(baseContext, "Cancelled by user", Toast.LENGTH_SHORT).show()
            }

            override fun onStoragePermissionDenied(requestCode: Int) {
                requestStoragePermission()
            }

            override fun onRootPathPermissionGranted(requestCode: Int, root: DocumentFile) {
                Toast.makeText(baseContext, "Storage access has been granted for ${root.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestStoragePermission() {
        Dexter.withContext(this)
            .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            .withListener(object : BaseMultiplePermissionsListener() {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        storage.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS)
                    } else {
                        Toast.makeText(baseContext, "Please grant storage permissions", Toast.LENGTH_SHORT).show()
                    }
                }
            }).check()
    }

    private fun setupFolderPickerCallback() {
        storage.folderPickerCallback = object : FolderPickerCallback {
            override fun onStoragePermissionDenied(requestCode: Int) {
                requestStoragePermission()
            }

            override fun onStorageAccessDenied(requestCode: Int, folder: DocumentFile?, storageType: StorageType?) {
                if (storageType == null) {
                    requestStoragePermission()
                    return
                }
                MaterialDialog(this@MainActivity)
                    .message(
                        text = "You have no write access to this storage, thus selecting this folder is useless." +
                                "\nWould you like to grant access to this folder?"
                    )
                    .negativeButton(android.R.string.cancel)
                    .positiveButton {
                        storage.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS, storageType)
                    }.show()
            }

            override fun onFolderSelected(requestCode: Int, folder: DocumentFile) {
                when (requestCode) {
                    REQUEST_CODE_PICK_FOLDER_TARGET_FOR_COPY -> layoutCopyToFolder.run {
                        tag = folder
                        tvFilePath.text = folder.absolutePath
                    }
                    REQUEST_CODE_PICK_FOLDER_TARGET_FOR_MOVE -> layoutMoveToFolder.run {
                        tag = folder
                        tvFilePath.text = folder.absolutePath
                    }
                    else -> Toast.makeText(baseContext, folder.absolutePath, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelledByUser(requestCode: Int) {
                Toast.makeText(baseContext, "Folder picker cancelled by user", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFilePickerCallback() {
        storage.filePickerCallback = object : FilePickerCallback {
            override fun onCancelledByUser(requestCode: Int) {
                Toast.makeText(baseContext, "File picker cancelled by user", Toast.LENGTH_SHORT).show()
            }

            override fun onStoragePermissionDenied(requestCode: Int, file: DocumentFile?) {
                requestStoragePermission()
            }

            override fun onFileSelected(requestCode: Int, file: DocumentFile) {
                when (requestCode) {
                    REQUEST_CODE_PICK_FILE_FOR_COPY -> layoutCopyFromFile.run {
                        tag = file
                        tvFilePath.text = file.name
                    }
                    REQUEST_CODE_PICK_FILE_FOR_MOVE -> layoutMoveFromFile.run {
                        tag = file
                        tvFilePath.text = file.name
                    }
                    else -> Toast.makeText(baseContext, "File selected: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupFileCopy() {
        layoutCopyFromFile.btnBrowse.setOnClickListener {
            storage.openFilePicker(REQUEST_CODE_PICK_FILE_FOR_COPY)
        }
        layoutCopyToFolder.btnBrowse.setOnClickListener {
            storage.openFolderPicker(REQUEST_CODE_PICK_FOLDER_TARGET_FOR_COPY)
        }
        btnStartCopyFile.setOnClickListener {
            if (layoutCopyFromFile.tag == null) {
                Toast.makeText(this, "Please select file to be copied", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (layoutCopyToFolder.tag == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = layoutCopyFromFile.tag as DocumentFile
            val targetFolder = layoutCopyToFolder.tag as DocumentFile
            ioScope.launch {
                file.copyTo(it.context, targetFolder, callback = object : FileCallback {

                    var dialog: MaterialDialog? = null
                    var tvStatus: TextView? = null
                    var progressBar: ProgressBar? = null

                    override fun onCheckFreeSpace(freeSpace: Long, fileSize: Long): Boolean {
                        return fileSize + 100 * FileSize.MB < freeSpace // Give tolerant 100MB
                    }

                    override fun onConflict(destinationFile: DocumentFile, action: FileCallback.FileConflictAction): Long {
                        handleFileConflict(action)
                        return FileCallback.FileConflictAction.DEFAULT_CONFIRMATION_TIMEOUT
                    }

                    override fun onStart(file: Any): Long {
                        // only show dialog if file size greater than 10Mb
                        if ((file as DocumentFile).length() > 10 * FileSize.MB) {
                            uiScope.launch {
                                dialog = MaterialDialog(it.context)
                                    .cancelable(false)
                                    .positiveButton(android.R.string.cancel) {
                                        // TODO: 20/08/20 Interrupt thread and cancel copy
                                    }
                                    .customView(R.layout.dialog_copy_progress).apply {
                                        tvStatus = getCustomView().findViewById<TextView>(R.id.tvProgressStatus).apply {
                                            text = "Copying file: 0%"
                                        }

                                        progressBar = getCustomView().findViewById<ProgressBar>(R.id.progressCopy).apply {
                                            isIndeterminate = true
                                        }
                                        show()
                                    }
                            }
                        }
                        return 500 // 0.5 second
                    }

                    override fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int) {
                        uiScope.launch {
                            tvStatus?.text = "Copying file: ${progress.toInt()}%"
                            progressBar?.isIndeterminate = false
                            progressBar?.progress = progress.toInt()
                        }
                    }

                    override fun onFailed(errorCode: ErrorCode) {
                        uiScope.launch { dialog?.dismiss() }
                    }

                    override fun onCompleted(file: Any) {
                        uiScope.launch {
                            dialog?.dismiss()
                            Toast.makeText(it.context, "File copied successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }
    }

    private fun setupFileMove() {
        layoutMoveFromFile.btnBrowse.setOnClickListener {
            storage.openFilePicker(REQUEST_CODE_PICK_FILE_FOR_MOVE)
        }
        layoutMoveToFolder.btnBrowse.setOnClickListener {
            storage.openFolderPicker(REQUEST_CODE_PICK_FOLDER_TARGET_FOR_MOVE)
        }
        btnStartMoveFile.setOnClickListener {
            if (layoutMoveFromFile.tag == null) {
                Toast.makeText(this, "Please select file to be moved", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (layoutMoveToFolder.tag == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = layoutMoveFromFile.tag as DocumentFile
            val targetFolder = layoutMoveToFolder.tag as DocumentFile
            ioScope.launch {
                file.moveTo(it.context, targetFolder, callback = object : FileCallback {

                    var dialog: MaterialDialog? = null
                    var tvStatus: TextView? = null
                    var progressBar: ProgressBar? = null

                    override fun onCheckFreeSpace(freeSpace: Long, fileSize: Long): Boolean {
                        return fileSize + 100 * FileSize.MB < freeSpace // Give tolerant 100MB
                    }

                    override fun onConflict(destinationFile: DocumentFile, action: FileCallback.FileConflictAction): Long {
                        handleFileConflict(action)
                        return FileCallback.FileConflictAction.DEFAULT_CONFIRMATION_TIMEOUT
                    }

                    override fun onStart(file: Any): Long {
                        // only show dialog if file size greater than 10Mb
                        if ((file as DocumentFile).length() > 10 * FileSize.MB) {
                            uiScope.launch {
                                dialog = MaterialDialog(it.context)
                                    .cancelable(false)
                                    .positiveButton(android.R.string.cancel) {
                                        // TODO: 20/08/20 Interrupt thread and cancel copy
                                    }
                                    .customView(R.layout.dialog_copy_progress).apply {
                                        tvStatus = getCustomView().findViewById<TextView>(R.id.tvProgressStatus).apply {
                                            text = "Moving file: 0%"
                                        }

                                        progressBar = getCustomView().findViewById<ProgressBar>(R.id.progressCopy).apply {
                                            isIndeterminate = true
                                        }
                                        show()
                                    }
                            }
                        }
                        return 500 // 0.5 second
                    }

                    override fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int) {
                        uiScope.launch {
                            tvStatus?.text = "Moving file: ${progress.toInt()}%"
                            progressBar?.isIndeterminate = false
                            progressBar?.progress = progress.toInt()
                        }
                    }

                    override fun onFailed(errorCode: ErrorCode) {
                        uiScope.launch { dialog?.dismiss() }
                    }

                    override fun onCompleted(file: Any) {
                        uiScope.launch {
                            dialog?.dismiss()
                            Toast.makeText(it.context, "File moved successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }
    }

    private fun handleFileConflict(action: FileCallback.FileConflictAction) {
        MaterialDialog(this)
            .cancelable(false)
            .title(text = "Conflict Found")
            .message(text = "What do you want to do with the file already exist in destination?")
            .listItems(items = listOf("Replace", "Create New", "Skip Duplicate")) { _, index, _ ->
                val resolution = FileCallback.ConflictResolution.values()[index]
                action.confirmResolution(resolution)
                if (resolution == FileCallback.ConflictResolution.SKIP_DUPLICATE) {
                    Toast.makeText(this, "Skipped duplicate file", Toast.LENGTH_SHORT).show()
                }
                /*
                When confirmation timeout has reached and the user does not confirm, calling action.confirmResolution(resolution) will be useless.
                Read FileCallback.onConflict()
                 */
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        storage.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storage.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storage.onRestoreInstanceState(savedInstanceState)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        const val REQUEST_CODE_STORAGE_ACCESS = 1
        const val REQUEST_CODE_PICK_FOLDER = 2
        const val REQUEST_CODE_PICK_FILE = 3

        const val REQUEST_CODE_PICK_FILE_FOR_COPY = 4
        const val REQUEST_CODE_PICK_FOLDER_TARGET_FOR_COPY = 5

        const val REQUEST_CODE_PICK_FILE_FOR_MOVE = 6
        const val REQUEST_CODE_PICK_FOLDER_TARGET_FOR_MOVE = 7
    }
}