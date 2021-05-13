package com.anggrayudi.storage.sample.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.list.listItems
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.callback.*
import com.anggrayudi.storage.file.*
import com.anggrayudi.storage.permission.ActivityPermissionRequest
import com.anggrayudi.storage.permission.PermissionCallback
import com.anggrayudi.storage.permission.PermissionReport
import com.anggrayudi.storage.permission.PermissionResult
import com.anggrayudi.storage.sample.R
import com.anggrayudi.storage.sample.StorageInfoAdapter
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.incl_base_operation.*
import kotlinx.android.synthetic.main.view_file_picked.view.*
import kotlinx.coroutines.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private val job = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + job)
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private val permissionRequest by lazy {
        ActivityPermissionRequest.Builder(this, REQUEST_CODE_ASK_PERMISSIONS)
            .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            .withCallback(object : PermissionCallback {
                override fun onPermissionsChecked(result: PermissionResult, fromSystemDialog: Boolean) {
                    val grantStatus = if (result.areAllPermissionsGranted) "granted" else "denied"
                    Toast.makeText(baseContext, "Storage permissions are $grantStatus", Toast.LENGTH_SHORT).show()
                }

                override fun onShouldRedirectToSystemSettings(blockedPermissions: List<PermissionReport>) {
                    openSystemSettings(this@MainActivity)
                }
            })
            .build()
    }

    private lateinit var storageHelper: SimpleStorageHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView.adapter = StorageInfoAdapter(applicationContext, ioScope, uiScope)

        setupSimpleStorage(savedInstanceState)
        setupButtonActions()
    }

    private fun scrollToView(view: View) {
        view.post(Runnable { scrollView?.scrollTo(0, view.top) })
    }

    private fun setupButtonActions() {
        btnRequestStoragePermission.setOnClickListener { permissionRequest.check() }

        btnRequestStorageAccess.setOnClickListener {
            storageHelper.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS)
        }

        btnRequestFullStorageAccess.run {
            isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setOnClickListener { storageHelper.storage.requestFullStorageAccess() }
                true
            } else {
                false
            }
        }

        btnSelectFolder.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_FOLDER)
        }

        btnSelectFile.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_FILE)
        }

        setupFileCopy()
        setupFolderCopy()
        setupFileMove()
        setupFolderMove()
    }

    private fun setupSimpleStorage(savedInstanceState: Bundle?) {
        storageHelper = SimpleStorageHelper(this, savedInstanceState)
        storageHelper.onFileSelected = { requestCode, file ->
            when (requestCode) {
                REQUEST_CODE_PICK_SOURCE_FILE_FOR_COPY -> layoutCopyFromFile.updateFileSelectionView(file)
                REQUEST_CODE_PICK_SOURCE_FILE_FOR_MOVE -> layoutMoveFromFile.updateFileSelectionView(file)
                else -> Toast.makeText(baseContext, "File selected: ${file.fullName}", Toast.LENGTH_SHORT).show()
            }
        }
        storageHelper.onFolderSelected = { requestCode, folder ->
            when (requestCode) {
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_COPY -> layoutCopyFileToFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_MOVE -> layoutMoveFileToFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_COPY -> layoutCopyFolderFromFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_COPY -> layoutCopyFolderToFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MOVE -> layoutMoveFolderFromFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_MOVE -> layoutMoveFolderToFolder.updateFolderSelectionView(folder)
                else -> Toast.makeText(baseContext, folder.absolutePath, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun View.updateFolderSelectionView(folder: DocumentFile) {
        tag = folder
        tvFilePath.text = folder.absolutePath
    }

    private fun View.updateFileSelectionView(file: DocumentFile) {
        tag = file
        tvFilePath.text = file.fullName
    }

    private fun setupFolderCopy() {
        layoutCopyFolderFromFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_COPY)
        }
        layoutCopyFolderToFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_COPY)
        }
        btnStartCopyFolder.setOnClickListener {
            val folder = layoutCopyFolderFromFolder.tag as? DocumentFile
            val targetFolder = layoutCopyFolderToFolder.tag as? DocumentFile
            if (folder == null) {
                Toast.makeText(this, "Please select folder to be copied", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (targetFolder == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ioScope.launch {
                folder.copyFolderTo(applicationContext, targetFolder, false, callback = object : FolderCallback {
                    override fun onPrepare() {
                        // Show notification or progress bar dialog with indeterminate state
                    }

                    override fun onCountingFiles() {
                        // Inform user that the app is counting & calculating files
                    }

                    override fun onStart(folder: DocumentFile, totalFilesToCopy: Int): Long {
                        return 1000 // update progress every 1 second
                    }

                    override fun onParentConflict(destinationFolder: DocumentFile, action: FolderCallback.ParentFolderConflictAction, canMerge: Boolean) {
                        handleParentFolderConflict(destinationFolder, action, canMerge)
                    }

                    override fun onContentConflict(
                        destinationFolder: DocumentFile,
                        conflictedFiles: MutableList<FolderCallback.FileConflict>,
                        action: FolderCallback.FolderContentConflictAction
                    ) {
                        handleFolderContentConflict(action, conflictedFiles)
                    }

                    override fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int, fileCount: Int) {
                        Timber.d("onReport() -> ${progress.toInt()}% | Copied $fileCount files")
                    }

                    override fun onCompleted(folder: DocumentFile, totalFilesToCopy: Int, totalCopiedFiles: Int, success: Boolean) {
                        uiScope.launch {
                            Toast.makeText(it.context, "Copied $totalCopiedFiles of $totalFilesToCopy files", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailed(errorCode: FolderCallback.ErrorCode) {
                        uiScope.launch {
                            Toast.makeText(it.context, "An error has occurred: $errorCode", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }
    }

    private fun setupFolderMove() {
        layoutMoveFolderFromFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MOVE)
        }
        layoutMoveFolderToFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_MOVE)
        }
        btnStartMoveFolder.setOnClickListener {
            val folder = layoutMoveFolderFromFolder.tag as? DocumentFile
            val targetFolder = layoutMoveFolderToFolder.tag as? DocumentFile
            if (folder == null) {
                Toast.makeText(this, "Please select folder to be moved", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (targetFolder == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ioScope.launch {
                folder.moveFolderTo(applicationContext, targetFolder, false, callback = object : FolderCallback {
                    override fun onPrepare() {
                        // Show notification or progress bar dialog with indeterminate state
                    }

                    override fun onCountingFiles() {
                        // Inform user that the app is counting & calculating files
                    }

                    override fun onStart(folder: DocumentFile, totalFilesToCopy: Int): Long {
                        return 1000 // update progress every 1 second
                    }

                    override fun onParentConflict(destinationFolder: DocumentFile, action: FolderCallback.ParentFolderConflictAction, canMerge: Boolean) {
                        handleParentFolderConflict(destinationFolder, action, canMerge)
                    }

                    override fun onContentConflict(
                        destinationFolder: DocumentFile,
                        conflictedFiles: MutableList<FolderCallback.FileConflict>,
                        action: FolderCallback.FolderContentConflictAction
                    ) {
                        handleFolderContentConflict(action, conflictedFiles)
                    }

                    override fun onReport(progress: Float, bytesMoved: Long, writeSpeed: Int, fileCount: Int) {
                        Timber.d("onReport() -> ${progress.toInt()}% | Moved $fileCount files")
                    }

                    override fun onCompleted(folder: DocumentFile, totalFilesToCopy: Int, totalCopiedFiles: Int, success: Boolean) {
                        uiScope.launch {
                            Toast.makeText(it.context, "Moved $totalCopiedFiles of $totalFilesToCopy files", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailed(errorCode: FolderCallback.ErrorCode) {
                        uiScope.launch {
                            Toast.makeText(it.context, "An error has occurred: $errorCode", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }
    }

    private fun setupFileCopy() {
        layoutCopyFromFile.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_SOURCE_FILE_FOR_COPY)
        }
        layoutCopyFileToFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_COPY)
        }
        btnStartCopyFile.setOnClickListener {
            if (layoutCopyFromFile.tag == null) {
                Toast.makeText(this, "Please select file to be copied", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (layoutCopyFileToFolder.tag == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = layoutCopyFromFile.tag as DocumentFile
            val targetFolder = layoutCopyFileToFolder.tag as DocumentFile
            ioScope.launch {
                file.copyFileTo(applicationContext, targetFolder, callback = object : FileCallback {

                    var dialog: MaterialDialog? = null
                    var tvStatus: TextView? = null
                    var progressBar: ProgressBar? = null

                    override fun onConflict(destinationFile: DocumentFile, action: FileCallback.FileConflictAction) {
                        handleFileConflict(action)
                    }

                    override fun onStart(file: Any): Long {
                        // only show dialog if file size greater than 10Mb
                        if ((file as DocumentFile).length() > 10 * FileSize.MB) {
                            val workerThread = Thread.currentThread()
                            uiScope.launch {
                                dialog = MaterialDialog(it.context)
                                    .cancelable(false)
                                    .positiveButton(android.R.string.cancel) { workerThread.interrupt() }
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

                    override fun onFailed(errorCode: FileCallback.ErrorCode) {
                        uiScope.launch {
                            dialog?.dismiss()
                            Toast.makeText(it.context, "Failed copying file: $errorCode", Toast.LENGTH_SHORT).show()
                        }
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
            storageHelper.openFilePicker(REQUEST_CODE_PICK_SOURCE_FILE_FOR_MOVE)
        }
        layoutMoveFileToFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_MOVE)
        }
        btnStartMoveFile.setOnClickListener {
            if (layoutMoveFromFile.tag == null) {
                Toast.makeText(this, "Please select file to be moved", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (layoutMoveFileToFolder.tag == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = layoutMoveFromFile.tag as DocumentFile
            val targetFolder = layoutMoveFileToFolder.tag as DocumentFile
            ioScope.launch {
                file.moveFileTo(applicationContext, targetFolder, callback = object : FileCallback {

                    var dialog: MaterialDialog? = null
                    var tvStatus: TextView? = null
                    var progressBar: ProgressBar? = null

                    override fun onConflict(destinationFile: DocumentFile, action: FileCallback.FileConflictAction) {
                        handleFileConflict(action)
                    }

                    override fun onStart(file: Any): Long {
                        // only show dialog if file size greater than 10Mb
                        if ((file as DocumentFile).length() > 10 * FileSize.MB) {
                            val workerThread = Thread.currentThread()
                            uiScope.launch {
                                dialog = MaterialDialog(it.context)
                                    .cancelable(false)
                                    .positiveButton(android.R.string.cancel) { workerThread.interrupt() }
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

                    override fun onFailed(errorCode: FileCallback.ErrorCode) {
                        uiScope.launch {
                            dialog?.dismiss()
                            Toast.makeText(it.context, "Failed moving file: $errorCode", Toast.LENGTH_SHORT).show()
                        }
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
            .message(text = "What do you want to do with the file already exists in destination?")
            .listItems(items = listOf("Replace", "Create New", "Skip Duplicate")) { _, index, _ ->
                val resolution = FileCallback.ConflictResolution.values()[index]
                action.confirmResolution(resolution)
                if (resolution == FileCallback.ConflictResolution.SKIP) {
                    Toast.makeText(this, "Skipped duplicate file", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun handleParentFolderConflict(destinationFolder: DocumentFile, action: FolderCallback.ParentFolderConflictAction, canMerge: Boolean) {
        MaterialDialog(this)
            .cancelable(false)
            .title(text = "Conflict Found")
            .message(text = "Folder \"${destinationFolder.name}\" already exists in destination. What's your action?")
            .listItems(items = mutableListOf("Replace", "Merge", "Create New", "Skip Duplicate").apply { if (!canMerge) remove("Merge") }) { _, index, _ ->
                val resolution = FolderCallback.ConflictResolution.values()[if (!canMerge && index > 0) index + 1 else index]
                action.confirmResolution(resolution)
                if (resolution == FolderCallback.ConflictResolution.SKIP) {
                    Toast.makeText(this, "Skipped duplicate folders & files", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun handleFolderContentConflict(action: FolderCallback.FolderContentConflictAction, conflictedFiles: MutableList<FolderCallback.FileConflict>) {
        val newSolution = ArrayList<FolderCallback.FileConflict>(conflictedFiles.size)
        askSolution(action, conflictedFiles, newSolution)
    }

    private fun askSolution(
        action: FolderCallback.FolderContentConflictAction,
        conflictedFiles: MutableList<FolderCallback.FileConflict>,
        newSolution: MutableList<FolderCallback.FileConflict>
    ) {
        val currentSolution = conflictedFiles.removeFirstOrNull()
        if (currentSolution == null) {
            action.confirmResolution(newSolution)
            return
        }
        var doForAll = false
        MaterialDialog(this)
            .cancelable(false)
            .title(text = "Conflict Found")
            .message(text = "File \"${currentSolution.target.name}\" already exists in destination. What's your action?")
            .checkBoxPrompt(text = "Apply to all") { doForAll = it }
            .listItems(items = listOf("Replace", "Create New", "Skip")) { _, index, _ ->
                currentSolution.solution = FileCallback.ConflictResolution.values()[index]
                newSolution.add(currentSolution)
                if (doForAll) {
                    conflictedFiles.forEach { it.solution = currentSolution.solution }
                    newSolution.addAll(conflictedFiles)
                    action.confirmResolution(newSolution)
                } else {
                    askSolution(action, conflictedFiles, newSolution)
                }
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        storageHelper.storage.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storageHelper.onRestoreInstanceState(savedInstanceState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        menu.findItem(R.id.action_open_fragment).intent = Intent(this, SampleFragmentActivity::class.java)
        menu.findItem(R.id.action_donate).intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=TGPGSY66LKUMN&source=url")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        menu.findItem(R.id.action_about).intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/anggrayudi/SimpleStorage")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionRequest.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        const val REQUEST_CODE_STORAGE_ACCESS = 1
        const val REQUEST_CODE_PICK_FOLDER = 2
        const val REQUEST_CODE_PICK_FILE = 3

        const val REQUEST_CODE_PICK_SOURCE_FILE_FOR_COPY = 4
        const val REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_COPY = 5

        const val REQUEST_CODE_PICK_SOURCE_FILE_FOR_MOVE = 6
        const val REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_MOVE = 7

        const val REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_COPY = 8
        const val REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_COPY = 9

        const val REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MOVE = 10
        const val REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_MOVE = 11

        const val REQUEST_CODE_ASK_PERMISSIONS = 12

        fun openSystemSettings(context: Context) {
            MaterialDialog(context)
                .message(text = "Permission was disabled permanently. Do you want to enable it from system settings?")
                .positiveButton {
                    val intentSetting = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addCategory(Intent.CATEGORY_DEFAULT)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intentSetting)
                }
                .negativeButton()
                .show()
        }
    }
}