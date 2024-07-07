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
import com.anggrayudi.storage.callback.MultipleFilesConflictCallback
import com.anggrayudi.storage.callback.SingleFileConflictCallback
import com.anggrayudi.storage.callback.SingleFolderConflictCallback
import com.anggrayudi.storage.extension.launchOnUiThread
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.changeName
import com.anggrayudi.storage.file.copyFileTo
import com.anggrayudi.storage.file.copyFolderTo
import com.anggrayudi.storage.file.copyTo
import com.anggrayudi.storage.file.fullName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.moveFileTo
import com.anggrayudi.storage.file.moveFolderTo
import com.anggrayudi.storage.file.moveTo
import com.anggrayudi.storage.file.openOutputStream
import com.anggrayudi.storage.permission.ActivityPermissionRequest
import com.anggrayudi.storage.permission.PermissionCallback
import com.anggrayudi.storage.permission.PermissionReport
import com.anggrayudi.storage.permission.PermissionResult
import com.anggrayudi.storage.result.MultipleFilesResult
import com.anggrayudi.storage.result.SingleFileResult
import com.anggrayudi.storage.result.SingleFolderResult
import com.anggrayudi.storage.sample.R
import com.anggrayudi.storage.sample.StorageInfoAdapter
import com.anggrayudi.storage.sample.databinding.ActivityMainBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
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
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.adapter = StorageInfoAdapter(applicationContext, ioScope, uiScope)

        setupSimpleStorage(savedInstanceState)
        setupButtonActions()
        displayOsInfo()
    }

    private fun scrollToView(view: View) {
        view.post(Runnable { binding.scrollView.scrollTo(0, view.top) })
    }

    @SuppressLint("SetTextI18n")
    private fun displayOsInfo() {
        binding.tvOsName.text = "OS name: Android " + Build.VERSION.RELEASE
        binding.tvApiLevel.text = "API level: " + Build.VERSION.SDK_INT
        binding.layoutOsInfo.visibility = View.GONE
    }

    @SuppressLint("NewApi")
    private fun setupButtonActions() {
        binding.layoutBaseOperation.btnRequestStoragePermission.run {
            setOnClickListener { permissionRequest.check() }
            isEnabled = Build.VERSION.SDK_INT in 23..28
        }

        binding.layoutBaseOperation.btnRequestStorageAccess.setOnClickListener { storageHelper.requestStorageAccess(REQUEST_CODE_STORAGE_ACCESS) }

        binding.layoutBaseOperation.btnRequestFullStorageAccess.run {
            isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setOnClickListener { storageHelper.storage.requestFullStorageAccess() }
                true
            } else {
                false
            }
        }

        binding.layoutBaseOperation.btnSelectFolder.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_FOLDER)
        }

        binding.layoutBaseOperation.btnSelectFile.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_FILE, true)
        }

        binding.layoutBaseOperation.btnCreateFile.setOnClickListener {
            storageHelper.createFile("text/plain", "Test create file", requestCode = REQUEST_CODE_CREATE_FILE)
        }

        binding.btnCompressFiles.setOnClickListener {
            startActivity(Intent(this, FileCompressionActivity::class.java))
        }
        binding.btnDecompressFiles.setOnClickListener {
            startActivity(Intent(this, FileDecompressionActivity::class.java))
        }
        binding.layoutBaseOperation.btnRenameFile.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_FILE_FOR_RENAME)
        }
        binding.layoutBaseOperation.btnDeleteFiles.setOnClickListener {
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
                getString(com.anggrayudi.storage.R.string.ss_selecting_root_path_success_without_open_folder_picker, root.getAbsolutePath(this)),
                Toast.LENGTH_SHORT
            ).show()
        }
        storageHelper.onFileSelected = { requestCode, files ->
            val file = files.first()
            when (requestCode) {
                REQUEST_CODE_PICK_SOURCE_FILE_FOR_COPY -> binding.layoutCopySrcFile.tvFilePath.updateFileSelectionView(file)
                REQUEST_CODE_PICK_SOURCE_FILE_FOR_MOVE -> binding.layoutMoveSrcFile.tvFilePath.updateFileSelectionView(file)
                REQUEST_CODE_PICK_SOURCE_FILE_FOR_MULTIPLE_COPY -> binding.layoutCopyMultipleFilesSourceFile.tvFilePath.updateFileSelectionView(file)
                REQUEST_CODE_PICK_SOURCE_FILE_FOR_MULTIPLE_MOVE -> binding.layoutMoveMultipleFilesSourceFile.tvFilePath.updateFileSelectionView(file)
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
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_COPY -> binding.layoutCopyFileTargetFolder.tvFilePath.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_MOVE -> binding.layoutMoveFileTargetFolder.tvFilePath.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_COPY -> binding.layoutCopyFolderSrcFolder.tvFilePath.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_COPY -> binding.layoutCopyFolderTargetFolder.tvFilePath.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MOVE -> binding.layoutMoveFolderSrcFolder.tvFilePath.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_MOVE -> binding.layoutMoveFolderTargetFolder.tvFilePath.updateFolderSelectionView(folder)

                REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MULTIPLE_COPY -> binding.layoutCopyMultipleFilesSourceFolder.tvFilePath.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_MULTIPLE_FILE_COPY -> binding.layoutCopyMultipleFilesTargetFolder.tvFilePath.updateFolderSelectionView(
                    folder
                )

                REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MULTIPLE_MOVE -> binding.layoutMoveMultipleFilesSourceFolder.tvFilePath.updateFolderSelectionView(folder)
                REQUEST_CODE_PICK_TARGET_FOLDER_FOR_MULTIPLE_FILE_MOVE -> binding.layoutMoveMultipleFilesTargetFolder.tvFilePath.updateFolderSelectionView(
                    folder
                )

                else -> {
                    Toast.makeText(baseContext, folder.getAbsolutePath(this), Toast.LENGTH_SHORT).show()
                }
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

    private fun TextView.updateFolderSelectionView(folder: DocumentFile) {
        tag = folder
        text = folder.getAbsolutePath(context)
    }

    private fun TextView.updateFileSelectionView(file: DocumentFile) {
        tag = file
        text = file.fullName
    }

    private fun setupMultipleCopy() {
        binding.layoutCopyMultipleFilesSourceFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MULTIPLE_COPY)
        }
        binding.layoutCopyMultipleFilesSourceFile.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_SOURCE_FILE_FOR_MULTIPLE_COPY)
        }
        binding.layoutCopyMultipleFilesTargetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_MULTIPLE_FILE_COPY)
        }
        binding.btnStartCopyMultipleFiles.setOnClickListener {
            val targetFolder = binding.layoutCopyMultipleFilesTargetFolder.tvFilePath.tag as? DocumentFile
            if (targetFolder == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sourceFolder = binding.layoutCopyMultipleFilesSourceFolder.tvFilePath.tag as? DocumentFile
            val sourceFile = binding.layoutCopyMultipleFilesSourceFile.tvFilePath.tag as? DocumentFile
            val sources = listOfNotNull(sourceFolder, sourceFile)
            if (sources.isEmpty()) {
                Toast.makeText(this, "Please select the source file and/or folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "Copying...", Toast.LENGTH_SHORT).show()
            ioScope.launch {
                sources.copyTo(applicationContext, targetFolder, onConflict = createMultipleFileCallback())
                    .onCompletion {
                        if (it is CancellationException) {
                            Timber.d("Multiple copies is aborted")
                        }
                    }.collect { result ->
                        when (result) {
                            is MultipleFilesResult.Validating -> Timber.d("Validating...")
                            is MultipleFilesResult.Preparing -> Timber.d("Preparing...")
                            is MultipleFilesResult.CountingFiles -> Timber.d("Counting files...")
                            is MultipleFilesResult.DeletingConflictedFiles -> Timber.d("Deleting conflicted files...")
                            is MultipleFilesResult.Starting -> Timber.d("Starting...")
                            is MultipleFilesResult.InProgress -> Timber.d("Progress: ${result.progress.toInt()}% | ${result.fileCount} files")
                            is MultipleFilesResult.Completed -> uiScope.launch {
                                Timber.d("Completed: ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files")
                                Toast.makeText(baseContext, "Copied ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files", Toast.LENGTH_SHORT).show()
                            }

                            is MultipleFilesResult.Error -> uiScope.launch {
                                Timber.e(result.errorCode.name)
                                Toast.makeText(baseContext, "An error has occurred: ${result.errorCode.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            }
        }
    }

    private fun setupMultipleMove() {
        binding.layoutMoveMultipleFilesSourceFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MULTIPLE_MOVE)
        }
        binding.layoutMoveMultipleFilesSourceFile.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_SOURCE_FILE_FOR_MULTIPLE_MOVE)
        }
        binding.layoutMoveMultipleFilesTargetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_MULTIPLE_FILE_MOVE)
        }
        binding.btnStartMoveMultipleFiles.setOnClickListener {
            val targetFolder = binding.layoutMoveMultipleFilesTargetFolder.tvFilePath.tag as? DocumentFile
            if (targetFolder == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sourceFolder = binding.layoutMoveMultipleFilesSourceFolder.tvFilePath.tag as? DocumentFile
            val sourceFile = binding.layoutMoveMultipleFilesSourceFile.tvFilePath.tag as? DocumentFile
            val sources = listOfNotNull(sourceFolder, sourceFile)
            if (sources.isEmpty()) {
                Toast.makeText(this, "Please select the source file and/or folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "Moving...", Toast.LENGTH_SHORT).show()
            ioScope.launch {
                sources.moveTo(applicationContext, targetFolder, onConflict = createMultipleFileCallback())
                    .onCompletion {
                        if (it is CancellationException) {
                            Timber.d("Multiple file moves is aborted")
                        }
                    }.collect { result ->
                        when (result) {
                            is MultipleFilesResult.Validating -> Timber.d("Validating...")
                            is MultipleFilesResult.Preparing -> Timber.d("Preparing...")
                            is MultipleFilesResult.CountingFiles -> Timber.d("Counting files...")
                            is MultipleFilesResult.DeletingConflictedFiles -> Timber.d("Deleting conflicted files...")
                            is MultipleFilesResult.Starting -> Timber.d("Starting...")
                            is MultipleFilesResult.InProgress -> Timber.d("Progress: ${result.progress.toInt()}% | ${result.fileCount} files")
                            is MultipleFilesResult.Completed -> uiScope.launch {
                                Timber.d("Completed: ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files")
                                Toast.makeText(baseContext, "Moved ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files", Toast.LENGTH_SHORT).show()
                            }

                            is MultipleFilesResult.Error -> uiScope.launch {
                                Timber.e(result.errorCode.name)
                                Toast.makeText(baseContext, "An error has occurred: ${result.errorCode.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            }
        }
    }

    private fun createMultipleFileCallback() = object : MultipleFilesConflictCallback(uiScope) {
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
            conflictedFiles: MutableList<SingleFolderConflictCallback.FileConflict>,
            action: SingleFolderConflictCallback.FolderContentConflictAction
        ) {
            handleFolderContentConflict(action, conflictedFiles)
        }
    }

    private fun setupFolderCopy() {
        binding.layoutCopyFolderSrcFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_COPY)
        }
        binding.layoutCopyFolderTargetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_COPY)
        }
        binding.btnStartCopyFolder.setOnClickListener {
            val folder = binding.layoutCopyFolderSrcFolder.tvFilePath.tag as? DocumentFile
            val targetFolder = binding.layoutCopyFolderTargetFolder.tvFilePath.tag as? DocumentFile
            if (folder == null) {
                Toast.makeText(this, "Please select folder to be copied", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (targetFolder == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "Copying...", Toast.LENGTH_SHORT).show()
            ioScope.launch {
                folder.copyFolderTo(applicationContext, targetFolder, false, onConflict = createFolderCallback())
                    .onCompletion {
                        if (it is CancellationException) {
                            Timber.d("Folder copy is aborted")
                        }
                    }.collect { result ->
                        when (result) {
                            is SingleFolderResult.Validating -> Timber.d("Validating...")
                            is SingleFolderResult.Preparing -> Timber.d("Preparing...")
                            is SingleFolderResult.CountingFiles -> Timber.d("Counting files...")
                            is SingleFolderResult.DeletingConflictedFiles -> Timber.d("Deleting conflicted files...")
                            is SingleFolderResult.Starting -> Timber.d("Starting...")
                            is SingleFolderResult.InProgress -> Timber.d("Progress: ${result.progress.toInt()}% | ${result.fileCount} files")
                            is SingleFolderResult.Completed -> uiScope.launch {
                                Timber.d("Completed: ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files")
                                Toast.makeText(baseContext, "Copied ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files", Toast.LENGTH_SHORT).show()
                            }

                            is SingleFolderResult.Error -> uiScope.launch {
                                Timber.e(result.errorCode.name)
                                Toast.makeText(baseContext, "An error has occurred: ${result.errorCode.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            }
        }
    }

    private fun setupFolderMove() {
        binding.layoutMoveFolderSrcFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_SOURCE_FOLDER_FOR_MOVE)
        }
        binding.layoutMoveFolderTargetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FOLDER_MOVE)
        }
        binding.btnStartMoveFolder.setOnClickListener {
            val folder = binding.layoutMoveFolderSrcFolder.tvFilePath.tag as? DocumentFile
            val targetFolder = binding.layoutMoveFolderTargetFolder.tvFilePath.tag as? DocumentFile
            if (folder == null) {
                Toast.makeText(this, "Please select folder to be moved", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (targetFolder == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "Moving...", Toast.LENGTH_SHORT).show()
            ioScope.launch {
                folder.moveFolderTo(applicationContext, targetFolder, false, onConflict = createFolderCallback())
                    .onCompletion {
                        if (it is CancellationException) {
                            Timber.d("Folder move is aborted")
                        }
                    }.collect { result ->
                        when (result) {
                            is SingleFolderResult.Validating -> Timber.d("Validating...")
                            is SingleFolderResult.Preparing -> Timber.d("Preparing...")
                            is SingleFolderResult.CountingFiles -> Timber.d("Counting files...")
                            is SingleFolderResult.DeletingConflictedFiles -> Timber.d("Deleting conflicted files...")
                            is SingleFolderResult.Starting -> Timber.d("Starting...")
                            is SingleFolderResult.InProgress -> Timber.d("Progress: ${result.progress.toInt()}% | ${result.fileCount} files")
                            is SingleFolderResult.Completed -> uiScope.launch {
                                Timber.d("Completed: ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files")
                                Toast.makeText(baseContext, "Moved ${result.totalCopiedFiles} of ${result.totalFilesToCopy} files", Toast.LENGTH_SHORT).show()
                            }

                            is SingleFolderResult.Error -> uiScope.launch {
                                Timber.e(result.errorCode.name)
                                Toast.makeText(baseContext, "An error has occurred: ${result.errorCode.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            }
        }
    }

    private fun createFolderCallback() = object : SingleFolderConflictCallback(uiScope) {
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
    }

    private fun setupFileCopy() {
        binding.layoutCopySrcFile.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_SOURCE_FILE_FOR_COPY)
        }
        binding.layoutCopyFileTargetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_COPY)
        }
        binding.btnStartCopyFile.setOnClickListener {
            if (binding.layoutCopySrcFile.tvFilePath.tag == null) {
                Toast.makeText(this, "Please select file to be copied", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (binding.layoutCopyFileTargetFolder.tvFilePath.tag == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = binding.layoutCopySrcFile.tvFilePath.tag as DocumentFile
            val targetFolder = binding.layoutCopyFileTargetFolder.tvFilePath.tag as DocumentFile
            Toast.makeText(this, "Copying...", Toast.LENGTH_SHORT).show()
            ioScope.launch {
                file.copyFileTo(applicationContext, targetFolder, onConflict = createFileCallback())
                    .onCompletion {
                        if (it is CancellationException) {
                            Timber.d("File copy is aborted")
                        }
                    }.collect {
                        when (it) {
                            is SingleFileResult.Validating -> Timber.d("Validating...")
                            is SingleFileResult.Preparing -> Timber.d("Preparing...")
                            is SingleFileResult.CountingFiles -> Timber.d("Counting files...")
                            is SingleFileResult.DeletingConflictedFile -> Timber.d("Deleting conflicted file...")
                            is SingleFileResult.Starting -> Timber.d("Starting...")
                            is SingleFileResult.InProgress -> Timber.d("Progress: ${it.progress.toInt()}%")
                            is SingleFileResult.Completed -> uiScope.launch {
                                Timber.d("Completed")
                                Toast.makeText(baseContext, "Copied successfully", Toast.LENGTH_SHORT).show()
                            }

                            is SingleFileResult.Error -> uiScope.launch {
                                Timber.e(it.errorCode.name)
                                Toast.makeText(baseContext, "An error has occurred: ${it.errorCode.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            }
        }
    }

    private fun setupFileMove() {
        binding.layoutMoveSrcFile.btnBrowse.setOnClickListener {
            storageHelper.openFilePicker(REQUEST_CODE_PICK_SOURCE_FILE_FOR_MOVE)
        }
        binding.layoutMoveFileTargetFolder.btnBrowse.setOnClickListener {
            storageHelper.openFolderPicker(REQUEST_CODE_PICK_TARGET_FOLDER_FOR_FILE_MOVE)
        }
        binding.btnStartMoveFile.setOnClickListener {
            if (binding.layoutMoveSrcFile.tvFilePath.tag == null) {
                Toast.makeText(this, "Please select file to be moved", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (binding.layoutMoveFileTargetFolder.tvFilePath.tag == null) {
                Toast.makeText(this, "Please select target folder", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = binding.layoutMoveSrcFile.tvFilePath.tag as DocumentFile
            val targetFolder = binding.layoutMoveFileTargetFolder.tvFilePath.tag as DocumentFile
            Toast.makeText(this, "Moving...", Toast.LENGTH_SHORT).show()
            var job: Job? = null
            job = ioScope.launch {
                var dialog: MaterialDialog? = null
                var tvStatus: TextView? = null
                var progressBar: ProgressBar? = null

                file.moveFileTo(applicationContext, targetFolder, onConflict = createFileCallback())
                    .onCompletion {
                        if (it is CancellationException) {
                            Timber.d("File move is aborted")
                        }
                        dialog?.dismiss()
                        dialog = null
                    }.collect { result ->
                        when (result) {
                            is SingleFileResult.Validating -> Timber.d("Validating...")
                            is SingleFileResult.Preparing -> Timber.d("Preparing...")
                            is SingleFileResult.CountingFiles -> Timber.d("Counting files...")
                            is SingleFileResult.DeletingConflictedFile -> Timber.d("Deleting conflicted file...")
                            is SingleFileResult.Starting -> Timber.d("Starting...")
                            is SingleFileResult.InProgress -> uiScope.launch {
                                Timber.d("Progress: ${result.progress.toInt()}%")
                                if (dialog == null) {
                                    dialog = MaterialDialog(this@MainActivity)
                                        .cancelable(false)
                                        .positiveButton(android.R.string.cancel) { job?.cancel() }
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
                                tvStatus?.text = "Copying file: ${result.progress.toInt()}%"
                                progressBar?.isIndeterminate = false
                                progressBar?.progress = result.progress.toInt()
                            }

                            is SingleFileResult.Completed -> uiScope.launch {
                                Timber.d("Completed")
                                Toast.makeText(baseContext, "Moved successfully", Toast.LENGTH_SHORT).show()
                            }

                            is SingleFileResult.Error -> uiScope.launch {
                                Timber.e(result.errorCode.name)
                                Toast.makeText(baseContext, "An error has occurred: ${result.errorCode.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            }
        }
    }

    private fun createFileCallback() = object : SingleFileConflictCallback<DocumentFile>(uiScope) {
        override fun onFileConflict(destinationFile: DocumentFile, action: FileConflictAction) {
            handleFileConflict(action)
        }
    }

    private fun handleFileConflict(action: SingleFileConflictCallback.FileConflictAction) {
        MaterialDialog(this)
            .cancelable(false)
            .title(text = "Conflict Found")
            .message(text = "What do you want to do with the file already exists in destination?")
            .listItems(items = listOf("Replace", "Create New", "Skip Duplicate")) { _, index, _ ->
                val resolution = SingleFileConflictCallback.ConflictResolution.entries[index]
                action.confirmResolution(resolution)
                if (resolution == SingleFileConflictCallback.ConflictResolution.SKIP) {
                    Toast.makeText(this, "Skipped duplicate file", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun handleParentFolderConflict(
        conflictedFolders: MutableList<MultipleFilesConflictCallback.ParentConflict>,
        conflictedFiles: MutableList<MultipleFilesConflictCallback.ParentConflict>,
        action: MultipleFilesConflictCallback.ParentFolderConflictAction
    ) {
        val newSolution = ArrayList<MultipleFilesConflictCallback.ParentConflict>(conflictedFiles.size)
        askFolderSolution(action, conflictedFolders, conflictedFiles, newSolution)
    }

    private fun askFolderSolution(
        action: MultipleFilesConflictCallback.ParentFolderConflictAction,
        conflictedFolders: MutableList<MultipleFilesConflictCallback.ParentConflict>,
        conflictedFiles: MutableList<MultipleFilesConflictCallback.ParentConflict>,
        newSolution: MutableList<MultipleFilesConflictCallback.ParentConflict>
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
                currentSolution.solution = SingleFolderConflictCallback.ConflictResolution.entries[if (!canMerge && index > 0) index + 1 else index]
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
        action: MultipleFilesConflictCallback.ParentFolderConflictAction,
        conflictedFolders: MutableList<MultipleFilesConflictCallback.ParentConflict>,
        conflictedFiles: MutableList<MultipleFilesConflictCallback.ParentConflict>,
        newSolution: MutableList<MultipleFilesConflictCallback.ParentConflict>
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
                currentSolution.solution = SingleFolderConflictCallback.ConflictResolution.entries[if (index > 0) index + 1 else index]
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

    private fun handleParentFolderConflict(
        destinationFolder: DocumentFile,
        action: SingleFolderConflictCallback.ParentFolderConflictAction,
        canMerge: Boolean
    ) {
        MaterialDialog(this)
            .cancelable(false)
            .title(text = "Conflict Found")
            .message(text = "Folder \"${destinationFolder.name}\" already exists in destination. What's your action?")
            .listItems(items = mutableListOf("Replace", "Merge", "Create New", "Skip Duplicate").apply { if (!canMerge) remove("Merge") }) { _, index, _ ->
                val resolution = SingleFolderConflictCallback.ConflictResolution.entries[if (!canMerge && index > 0) index + 1 else index]
                action.confirmResolution(resolution)
                if (resolution == SingleFolderConflictCallback.ConflictResolution.SKIP) {
                    Toast.makeText(this, "Skipped duplicate folders & files", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun handleFolderContentConflict(
        action: SingleFolderConflictCallback.FolderContentConflictAction,
        conflictedFiles: MutableList<SingleFolderConflictCallback.FileConflict>
    ) {
        val newSolution = ArrayList<SingleFolderConflictCallback.FileConflict>(conflictedFiles.size)
        askSolution(action, conflictedFiles, newSolution)
    }

    private fun askSolution(
        action: SingleFolderConflictCallback.FolderContentConflictAction,
        conflictedFiles: MutableList<SingleFolderConflictCallback.FileConflict>,
        newSolution: MutableList<SingleFolderConflictCallback.FileConflict>
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
                currentSolution.solution = SingleFileConflictCallback.ConflictResolution.entries[index]
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

    override fun onNewIntent(intent: Intent) {
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