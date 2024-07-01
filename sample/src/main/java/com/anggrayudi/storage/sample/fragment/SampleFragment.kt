package com.anggrayudi.storage.sample.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.file.fullName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.permission.FragmentPermissionRequest
import com.anggrayudi.storage.permission.PermissionCallback
import com.anggrayudi.storage.permission.PermissionReport
import com.anggrayudi.storage.permission.PermissionRequest
import com.anggrayudi.storage.permission.PermissionResult
import com.anggrayudi.storage.sample.R
import com.anggrayudi.storage.sample.activity.MainActivity
import com.anggrayudi.storage.sample.databinding.InclBaseOperationBinding

/**
 * Created on 13/05/21
 * @author Anggrayudi H
 */
class SampleFragment : Fragment(R.layout.incl_base_operation) {

    // In Fragment, build permissionRequest before onCreate() is called
    private val permissionRequest = FragmentPermissionRequest.Builder(this)
        .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        .withCallback(object : PermissionCallback {
            override fun onPermissionsChecked(result: PermissionResult, fromSystemDialog: Boolean) {
                val grantStatus = if (result.areAllPermissionsGranted) "granted" else "denied"
                Toast.makeText(requireContext(), "Storage permissions are $grantStatus", Toast.LENGTH_SHORT).show()
            }

            override fun onDisplayConsentDialog(request: PermissionRequest) {
                MaterialDialog(requireContext())
                    .message(text = "Please allow Simple Storage to access your storage.")
                    .positiveButton { request.continueToPermissionRequest() }
                    .negativeButton()
                    .show()
            }

            override fun onShouldRedirectToSystemSettings(blockedPermissions: List<PermissionReport>) {
                SimpleStorageHelper.redirectToSystemSettings(requireContext())
            }
        })
        .build()

    private lateinit var storageHelper: SimpleStorageHelper

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("NewApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSimpleStorage(savedInstanceState)

        val binding = InclBaseOperationBinding.bind(view)
        binding.btnRequestStoragePermission.run {
            setOnClickListener { permissionRequest.check() }
            isEnabled = Build.VERSION.SDK_INT in 23..28
        }

        binding.btnRequestStorageAccess.setOnClickListener { storageHelper.requestStorageAccess(MainActivity.REQUEST_CODE_STORAGE_ACCESS) }

        binding.btnRequestFullStorageAccess.run {
            isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setOnClickListener { storageHelper.storage.requestFullStorageAccess() }
                true
            } else {
                false
            }
        }

        binding.btnSelectFolder.setOnClickListener {
            storageHelper.openFolderPicker(MainActivity.REQUEST_CODE_PICK_FOLDER)
        }

        binding.btnSelectFile.setOnClickListener {
            storageHelper.openFilePicker(MainActivity.REQUEST_CODE_PICK_FILE)
        }

        binding.btnCreateFile.setOnClickListener {
            storageHelper.createFile("text/plain", "Test create file", requestCode = MainActivity.REQUEST_CODE_CREATE_FILE)
        }
    }

    private fun setupSimpleStorage(savedInstanceState: Bundle?) {
        storageHelper = SimpleStorageHelper(this, savedInstanceState)
        storageHelper.onFileSelected = { requestCode, files ->
            Toast.makeText(requireContext(), "File selected: ${files.first().fullName}", Toast.LENGTH_SHORT).show()
        }
        storageHelper.onFolderSelected = { requestCode, folder ->
            Toast.makeText(requireContext(), folder.getAbsolutePath(requireContext()), Toast.LENGTH_SHORT).show()
        }
        storageHelper.onFileCreated = { requestCode, file ->
            MainActivity.writeTestFile(requireContext().applicationContext, requestCode, file)
        }
    }
}