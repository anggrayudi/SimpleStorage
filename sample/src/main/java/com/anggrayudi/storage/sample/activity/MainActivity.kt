package com.anggrayudi.storage.sample.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
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
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItems
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.callback.FileCallback
import com.anggrayudi.storage.callback.FolderCallback
import com.anggrayudi.storage.callback.MultipleFileCallback
import com.anggrayudi.storage.extension.launchOnUiThread
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
import java.io.IOException
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val job = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + job)
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private val permissionRequest = ActivityPermissionRequest.Builder(this)
        .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        .withCallback(object : PermissionCallback {
            override fun onPermissionsChecked(result: PermissionResult, fromSystemDialog: Boolean) {
                val grantStatus = if (result.areAllPermissionsGranted) "granted" else "denied"
                Toast.makeText(baseContext, "Storage permissions are $grantStatus", Toast.LENGTH_SHORT).show()
            }

            override fun onShouldRedirectToSystemSettings(blockedPermissions: List<PermissionReport>) {
                SimpleStorageHelper.redirectToSystemSettings(this@MainActivity)
            }
        })
        .build()

    private val storageHelper = SimpleStorageHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView.adapter = StorageInfoAdapter(applicationContext, ioScope, uiScope)

        setupSimpleStorage(savedInstanceState)
        setupButtonActions()
        displayOsInfo()
    }

    private fun scrollToView(view: View) {
        view.post(Runnable { scrollView?.scrollTo(0, view.top) })
    }

    @SuppressLint("SetTextI18n")
    private fun displayOsInfo() {
        tvOsName.text = "OS name: Android " + Build.VERSION.RELEASE
        tvApiLevel.text = "API level: " + Build.VERSION.SDK_INT
        layoutOsInfo.visibility = View.VISIBLE
    }

    @SuppressLint("NewApi")
    private fun setupButtonActions() {
        btnRequestStoragePermission.run {
            setOnClickListener { permissionRequest.check() }
            isEnabled = Build.VERSION.SDK_INT in 23..28
        }

        btnRequestStorageAccess.run {
            isEnabled = Build.VERSION.SDK_INT >= 21
            setOnClickListener { storageHelper.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS) }
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
            storageHelper.openFilePicker(REQUEST_CODE_PICK_FILE, true)
        }

        btnCreateFile.setOnClickListener {
            storageHelper.createFile("text/plain", "Test create file", requestCode = REQUEST_CODE_CREATE_FILE)
        }

        btnCompressFiles.setOnClickListener {
            startActivity(Intent(this, FileCompressionActivity::class.java))
        }
        btnDecompressFiles.setOnClickListener {
            startActivity(Intent(this, FileDecompressionActivity::class.java))
        }
        btnRenameFile.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_FILE_FOR_RENAME)
        }
        btnDeleteFiles.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_FILE_FOR_DELETE, allowMultiple = true)
        }

        setupFileCopy()
        setupFolderCopy()
        setupFileMove()
        setupFolderMove()
        setupMultipleCopy()
        setupMultipleMove()
    }

    private fun setupSimpleStorage(savedInstanceState: Bundle?) {
        savedInstanceState?.let { storageHelper.onRestoreInstanceState(it) }
        storageHelper.onStorageAccessGranted = { _, root ->
            Toast.makeText(
                this,
                getString(R.string.ss_selecting_root_path_success_without_open_folder_picker, root.getAbsolutePath(this)),
                Toast.LENGTH_SHORT
            ).show()
        }
        storageHelper.onFileSelected = { requestCode, files ->
            val file = files.first()
            when (requestCode) {
                REQUEST_CODE_PICK_SOURCE_FILE_FOR_COPY -> layoutCopy_srcFile.updateFileSelectionView(file)
                REQUEST_CODE_PICK_SOURCE_FILE_FOR_MOVE -> layoutMove_srcFile.updateFileSelectionView(file)
                REQUEST_CODE_PICK_SOURCE_FILE_FOR_MULTIPLE_COPY -> layoutCopyMultipleFiles_sourceFile.updateFileSelectionView(file)
                REQUEST_CODE_PICK_SOURCE_FILE_FOR_MULTIPLE_MOVE -> layoutMoveMultipleFiles_sourceFile.updateFileSelectionView(file)
                REQUEST_CODE_PICK_FILE_FOR_RENAME -> renameFile(file)
                REQUEST_CODE_PICK_FILE_FOR_DELETE -> deleteFiles(files)
                else -> {
                    val names = files.joinToString(", ") { it.fullName }
                    Toast.makeText(baseContext, "File selected: $names", Toast.LENGTH_SHORT).show()
                }
            }
        }
        storageHelper.onFolderSelected = { requestCode, folder ->
            when (requestCode) {
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_COPY -> layoutCopyFile_targetFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_MOVE -> layoutMoveFile_targetFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_COPY -> layoutCopyFolder_srcFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_COPY -> layoutCopyFolder_targetFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MOVE -> layoutMoveFolder_srcFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_MOVE -> layoutMoveFolder_targetFolder.updateFolderSelectionView(folder)

                REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MULTIPLE_COPY -> layoutCopyMultipleFiles_sourceFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_MULTIPLE_FILE_COPY -> layoutCopyMultipleFiles_targetFolder.updateFolderSelectionView(folder)

                REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MULTIPLE_MOVE -> layoutMoveMultipleFiles_sourceFolder.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_MULTIPLE_FILE_MOVE -> layoutMoveMultipleFiles_targetFolder.updateFolderSelectionView(folder)

                else -> Toast.makeText(baseContext, folder.getAbsolutePath(this), Toast.LENGTH_SHORT).show()
            }
        }
        storageHelper.onFileCreated = { requestCode, file ->
            writeTestFile(applicationContext, requestCode, file)
        }
        storageHelper.onFileReceived = object : SimpleStorageHelper.OnFileReceived {
            override fun onFileReceived(files: List<DocumentFile>) {
                val names = files.joinToString(", ") { it.fullName }
                Toast.makeText(baseContext, "File received: $names", Toast.LENGTH_SHORT).show()
            }

            override fun onNonFileReceived(intent: Intent) {
                Toast.makeText(baseContext, "Non-file is received", Toast.LENGTH_SHORT).show()
            }
        }
        if (savedInstanceState == null) {
            storageHelper.storage.checkIfFileReceived(intent)
        }
    }

    private fun renameFile(file: DocumentFile) {
        MaterialDialog(this)
            .title(text = "Rename file")
            .input(prefill = file.baseName, hint = "New name", callback = { _, text ->
                ioScope.launch {
                    val newName = file.changeName(baseContext, text.toString())?.name
                    uiScope.launch {
                        val message = if (newName != null) "File renamed to $newName" else "Failed to rename ${file.fullName}"
                        Toast.makeText(baseContext, message, Toast.LENGTH_SHORT).show()
                    }
                }
            })
            .negativeButton(android.R.string.cancel)
            .show()
    }

    private fun deleteFiles(files: List<DocumentFile>) {
        ioScope.launch {
            val deleted = files.count { it.delete() }
            uiScope.launch {
                Toast.makeText(baseContext, "Deleted $deleted of ${files.size} files", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun View.updateFolderSelectionView(folder: DocumentFile) {
        tag = folder
        tvFilePath.text = folder.getAbsolutePath(context)
    }

    private fun View.updateFileSelectionView(file: DocumentFile) {
        tag = file
        tvFilePath.text = file.fullName
    }

    private fun setupMultipleCopy() {
        layoutCopyMultipleFiles_sourceFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MULTIPLE_COPY)
        }
        layoutCopyMultipleFiles_sourceFile.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_SOURCE_FILE_FOR_MULTIPLE_COPY)
        }
        layoutCopyMultipleFiles_targetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_MULTIPLE_FILE_COPY)
        }
        btnStartCopyMultipleFiles.setOnClickListener {
            val targetFolder = layoutCopyMultipleFiles_targetFolder.tag as? DocumentFile
            if (targetFolder == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sourceFolder = layoutCopyMultipleFiles_sourceFolder.tag as? DocumentFile
            val sourceFile = layoutCopyMultipleFiles_sourceFile.tag as? DocumentFile
            val sources = listOfNotNull(sourceFolder, sourceFile)
            if (sources.isEmpty()) {
                Toast.makeText(this, "Please select the source file and/or folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ioScope.launch {
                sources.copyTo(applicationContext, targetFolder, callback = createMultipleFileCallback(false))
            }
        }
    }

    private fun setupMultipleMove() {
        layoutMoveMultipleFiles_sourceFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MULTIPLE_MOVE)
        }
        layoutMoveMultipleFiles_sourceFile.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_SOURCE_FILE_FOR_MULTIPLE_MOVE)
        }
        layoutMoveMultipleFiles_targetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_MULTIPLE_FILE_MOVE)
        }
        btnStartCopyMultipleFiles.setOnClickListener {
            val targetFolder = layoutMoveMultipleFiles_targetFolder.tag as? DocumentFile
            if (targetFolder == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sourceFolder = layoutMoveMultipleFiles_sourceFolder.tag as? DocumentFile
            val sourceFile = layoutMoveMultipleFiles_sourceFile.tag as? DocumentFile
            val sources = listOfNotNull(sourceFolder, sourceFile)
            if (sources.isEmpty()) {
                Toast.makeText(this, "Please select the source file and/or folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ioScope.launch {
                sources.moveTo(applicationContext, targetFolder, callback = createMultipleFileCallback(true))
            }
        }
    }

    private fun createMultipleFileCallback(isMoveFileMode: Boolean) = object : MultipleFileCallback(uiScope) {
        val mode = if (isMoveFileMode) "Moved" else "Copied"

        override fun onStart(files: List<DocumentFile>, totalFilesToCopy: Int, workerThread: Thread): Long {
            return 1000 // update progress every 1 second
        }

        override fun onParentConflict(
            destinationParentFolder: DocumentFile,
            conflictedFolders: MutableList<ParentConflict>,
            conflictedFiles: MutableList<ParentConflict>,
            action: ParentFolderConflictAction
        ) {
            handleParentFolderConflict(conflictedFolders, conflictedFiles, action)
        }

        override fun onContentConflict(
            destinationParentFolder: DocumentFile,
            conflictedFiles: MutableList<FolderCallback.FileConflict>,
            action: FolderCallback.FolderContentConflictAction
        ) {
            handleFolderContentConflict(action, conflictedFiles)
        }

        override fun onReport(report: Report) {
            Timber.d("onReport() -> ${report.progress.toInt()}% | $mode ${report.fileCount} files")
        }

        override fun onCompleted(result: Result) {
            Toast.makeText(baseContext, "$mode ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files", Toast.LENGTH_SHORT).show()
        }

        override fun onFailed(errorCode: ErrorCode) {
            Toast.makeText(baseContext, "An error has occurred: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFolderCopy() {
        layoutCopyFolder_srcFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_COPY)
        }
        layoutCopyFolder_targetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_COPY)
        }
        btnStartCopyFolder.setOnClickListener {
            val folder = layoutCopyFolder_srcFolder.tag as? DocumentFile
            val targetFolder = layoutCopyFolder_targetFolder.tag as? DocumentFile
            if (folder == null) {
                Toast.makeText(this, "Please select folder to be copied", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (targetFolder == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ioScope.launch {
                folder.copyFolderTo(applicationContext, targetFolder, false, callback = createFolderCallback(false))
            }
        }
    }

    private fun setupFolderMove() {
        layoutMoveFolder_srcFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MOVE)
        }
        layoutMoveFolder_targetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_MOVE)
        }
        btnStartMoveFolder.setOnClickListener {
            val folder = layoutMoveFolder_srcFolder.tag as? DocumentFile
            val targetFolder = layoutMoveFolder_targetFolder.tag as? DocumentFile
            if (folder == null) {
                Toast.makeText(this, "Please select folder to be moved", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (targetFolder == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ioScope.launch {
                folder.moveFolderTo(applicationContext, targetFolder, false, callback = createFolderCallback(true))
            }
        }
    }

    private fun createFolderCallback(isMoveFileMode: Boolean) = object : FolderCallback(uiScope) {
        val mode = if (isMoveFileMode) "Moved" else "Copied"

        override fun onPrepare() {
            // Show notification or progress bar dialog with indeterminate state
        }

        override fun onCountingFiles() {
            // Inform user that the app is counting & calculating files
        }

        override fun onStart(folder: DocumentFile, totalFilesToCopy: Int, workerThread: Thread): Long {
            return 1000 // update progress every 1 second
        }

        override fun onParentConflict(destinationFolder: DocumentFile, action: ParentFolderConflictAction, canMerge: Boolean) {
            handleParentFolderConflict(destinationFolder, action, canMerge)
        }

        override fun onContentConflict(
            destinationFolder: DocumentFile,
            conflictedFiles: MutableList<FileConflict>,
            action: FolderContentConflictAction
        ) {
            handleFolderContentConflict(action, conflictedFiles)
        }

        override fun onReport(report: Report) {
            Timber.d("onReport() -> ${report.progress.toInt()}% | $mode ${report.fileCount} files")
        }

        override fun onCompleted(result: Result) {
            Toast.makeText(baseContext, "$mode ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files", Toast.LENGTH_SHORT).show()
        }

        override fun onFailed(errorCode: ErrorCode) {
            Toast.makeText(baseContext, "An error has occurred: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFileCopy() {
        layoutCopy_srcFile.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_SOURCE_FILE_FOR_COPY)
        }
        layoutCopyFile_targetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_COPY)
        }
        btnStartCopyFile.setOnClickListener {
            if (layoutCopy_srcFile.tag == null) {
                Toast.makeText(this, "Please select file to be copied", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (layoutCopyFile_targetFolder.tag == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = layoutCopy_srcFile.tag as DocumentFile
            val targetFolder = layoutCopyFile_targetFolder.tag as DocumentFile
            ioScope.launch {
                file.copyFileTo(applicationContext, targetFolder, callback = createFileCallback())
            }
        }
    }

    private fun setupFileMove() {
        layoutMove_srcFile.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_SOURCE_FILE_FOR_MOVE)
        }
        layoutMoveFile_targetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_MOVE)
        }
        btnStartMoveFile.setOnClickListener {
            if (layoutMove_srcFile.tag == null) {
                Toast.makeText(this, "Please select file to be moved", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (layoutMoveFile_targetFolder.tag == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = layoutMove_srcFile.tag as DocumentFile
            val targetFolder = layoutMoveFile_targetFolder.tag as DocumentFile
            ioScope.launch {
                file.moveFileTo(applicationContext, targetFolder, callback = createFileCallback())
            }
        }
    }

    private fun createFileCallback() = object : FileCallback(uiScope) {

        var dialog: MaterialDialog? = null
        var tvStatus: TextView? = null
        var progressBar: ProgressBar? = null

        override fun onConflict(destinationFile: DocumentFile, action: FileConflictAction) {
            handleFileConflict(action)
        }

        override fun onStart(file: Any, workerThread: Thread): Long {
            // only show dialog if file size greater than 10Mb
            if ((file as DocumentFile).length() > 10 * FileSize.MB) {
                dialog = MaterialDialog(this@MainActivity)
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
            return 500 // 0.5 second
        }

        override fun onReport(report: Report) {
            tvStatus?.text = "Copying file: ${report.progress.toInt()}%"
            progressBar?.isIndeterminate = false
            progressBar?.progress = report.progress.toInt()
        }

        override fun onFailed(errorCode: ErrorCode) {
            dialog?.dismiss()
            Toast.makeText(baseContext, "Failed copying file: $errorCode", Toast.LENGTH_SHORT).show()
        }

        override fun onCompleted(result: Any) {
            dialog?.dismiss()
            Toast.makeText(baseContext, "File copied successfully", Toast.LENGTH_SHORT).show()
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

    private fun handleParentFolderConflict(
        conflictedFolders: MutableList<MultipleFileCallback.ParentConflict>,
        conflictedFiles: MutableList<MultipleFileCallback.ParentConflict>,
        action: MultipleFileCallback.ParentFolderConflictAction
    ) {
        val newSolution = ArrayList<MultipleFileCallback.ParentConflict>(conflictedFiles.size)
        askFolderSolution(action, conflictedFolders, conflictedFiles, newSolution)
    }

    private fun askFolderSolution(
        action: MultipleFileCallback.ParentFolderConflictAction,
        conflictedFolders: MutableList<MultipleFileCallback.ParentConflict>,
        conflictedFiles: MutableList<MultipleFileCallback.ParentConflict>,
        newSolution: MutableList<MultipleFileCallback.ParentConflict>
    ) {
        val currentSolution = conflictedFolders.removeFirstOrNull()
        if (currentSolution == null) {
            askFileSolution(action, conflictedFolders, conflictedFiles, newSolution)
            return
        }
        var doForAll = false
        val canMerge = currentSolution.canMerge
        MaterialDialog(this)
            .cancelable(false)
            .title(text = "Conflict Found")
            .message(text = "Folder \"${currentSolution.target.name}\" already exists in destination. What's your action?")
            .checkBoxPrompt(text = "Apply to all") { doForAll = it }
            .listItems(items = mutableListOf("Replace", "Merge", "Create New", "Skip Duplicate").apply { if (!canMerge) remove("Merge") }) { _, index, _ ->
                currentSolution.solution = FolderCallback.ConflictResolution.values()[if (!canMerge && index > 0) index + 1 else index]
                newSolution.add(currentSolution)
                if (doForAll) {
                    conflictedFolders.forEach { it.solution = currentSolution.solution }
                    newSolution.addAll(conflictedFolders)
                    askFileSolution(action, conflictedFolders, conflictedFiles, newSolution)
                } else {
                    askFolderSolution(action, conflictedFolders, conflictedFiles, newSolution)
                }
            }
            .show()
    }

    private fun askFileSolution(
        action: MultipleFileCallback.ParentFolderConflictAction,
        conflictedFolders: MutableList<MultipleFileCallback.ParentConflict>,
        conflictedFiles: MutableList<MultipleFileCallback.ParentConflict>,
        newSolution: MutableList<MultipleFileCallback.ParentConflict>
    ) {
        val currentSolution = conflictedFiles.removeFirstOrNull()
        if (currentSolution == null) {
            action.confirmResolution(newSolution.plus(conflictedFolders))
            return
        }
        var doForAll = false
        MaterialDialog(this)
            .cancelable(false)
            .title(text = "Conflict Found")
            .message(text = "File \"${currentSolution.target.name}\" already exists in destination. What's your action?")
            .checkBoxPrompt(text = "Apply to all") { doForAll = it }
            .listItems(items = mutableListOf("Replace", "Create New", "Skip Duplicate")) { _, index, _ ->
                currentSolution.solution = FolderCallback.ConflictResolution.values()[if (index > 0) index + 1 else index]
                newSolution.add(currentSolution)
                if (doForAll) {
                    conflictedFiles.forEach { it.solution = currentSolution.solution }
                    newSolution.addAll(conflictedFiles)
                    action.confirmResolution(newSolution.plus(conflictedFolders))
                } else {
                    askFolderSolution(action, conflictedFolders, conflictedFiles, newSolution)
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        storageHelper.storage.checkIfFileReceived(intent)
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
        menu.findItem(R.id.action_pref_save_location).intent = Intent(this, SettingsActivity::class.java)
        menu.findItem(R.id.action_settings).intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        menu.findItem(R.id.action_about).intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/anggrayudi/SimpleStorage")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_donate) {
            MaterialDialog(this)
                .title(text = "Donation")
                .message(text = "Select your donation method.")
                .listItems(items = listOf("PayPal", "Saweria")) { _, index, _ ->
                    val url = when (index) {
                        0 -> "https://www.paypal.com/paypalme/hardiannicko"
                        else -> "https://saweria.co/hardiannicko"
                    }
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                .show()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        const val REQUEST_CODE_STORAGE_ACCESS = 1
        const val REQUEST_CODE_PICK_FOLDER = 2
        const val REQUEST_CODE_PICK_FILE = 3
        const val REQUEST_CODE_CREATE_FILE = 4

        const val REQUEST_CODE_PICK_SOURCE_FILE_FOR_COPY = 5
        const val REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_COPY = 6

        const val REQUEST_CODE_PICK_SOURCE_FILE_FOR_MOVE = 7
        const val REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_MOVE = 8

        const val REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_COPY = 9
        const val REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_COPY = 10

        const val REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MOVE = 11
        const val REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_MOVE = 12

        const val REQUEST_CODE_PICK_SOURCE_FILE_FOR_MULTIPLE_COPY = 13
        const val REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MULTIPLE_COPY = 14
        const val REQUEST_CODE_PICK_TARGET_FOLDER_FOR_MULTIPLE_FILE_COPY = 15

        const val REQUEST_CODE_PICK_SOURCE_FILE_FOR_MULTIPLE_MOVE = 16
        const val REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MULTIPLE_MOVE = 17
        const val REQUEST_CODE_PICK_TARGET_FOLDER_FOR_MULTIPLE_FILE_MOVE = 18

        const val REQUEST_CODE_PICK_FILE_FOR_RENAME = 19
        const val REQUEST_CODE_PICK_FILE_FOR_DELETE = 20

        fun writeTestFile(context: Context, requestCode: Int, file: DocumentFile) {
            thread {
                file.openOutputStream(context)?.use {
                    try {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        it.write("Welcome to SimpleStorage!\nRequest code: $requestCode\nTime: ${System.currentTimeMillis()}".toByteArray())
                        launchOnUiThread { Toast.makeText(context, "Successfully created file \"${file.name}\"", Toast.LENGTH_SHORT).show() }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}