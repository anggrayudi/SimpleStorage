package com.anggrayudi.storage.sample.fragment

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.anggrayudi.storage.SimpleStorageHelper
import com.anggrayudi.storage.file.absolutePath
import com.anggrayudi.storage.file.fullName
import com.anggrayudi.storage.permission.*
import com.anggrayudi.storage.sample.R
import com.anggrayudi.storage.sample.activity.MainActivity
import kotlinx.android.synthetic.main.incl_base_operation.*

/**
 * Created on 13/05/21
 * @author Anggrayudi H
 */
class SampleFragment : Fragment() {

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
                MainActivity.openSystemSettings(requireContext())
            }
        })
        .build()

    private lateinit var storageHelper: SimpleStorageHelper

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.incl_base_operation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSimpleStorage(savedInstanceState)

        btnRequestStoragePermission.setOnClickListener { permissionRequest.check() }

        btnRequestStorageAccess.setOnClickListener {
            storageHelper.requestStorageAccess(MainActivity.REQUEST_CODE_STORAGE_ACCESS)
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
            storageHelper.openFolderPicker(MainActivity.REQUEST_CODE_PICK_FOLDER)
        }

        btnSelectFile.setOnClickListener {
            storageHelper.openFilePicker(MainActivity.REQUEST_CODE_PICK_FILE)
        }
    }

    private fun setupSimpleStorage(savedInstanceState: Bundle?) {
        storageHelper = SimpleStorageHelper(this, savedInstanceState)
        storageHelper.onFileSelected = { requestCode, file ->
            Toast.makeText(requireContext(), "File selected: ${file.fullName}", Toast.LENGTH_SHORT).show()
        }
        storageHelper.onFolderSelected = { requestCode, folder ->
            Toast.makeText(requireContext(), folder.absolutePath, Toast.LENGTH_SHORT).show()
        }
    }
}