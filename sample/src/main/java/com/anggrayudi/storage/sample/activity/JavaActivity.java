package com.anggrayudi.storage.sample.activity;

import com.anggrayudi.storage.SimpleStorageHelper;
import com.anggrayudi.storage.file.DocumentFileUtils;
import com.anggrayudi.storage.permission.ActivityPermissionRequest;
import com.anggrayudi.storage.permission.PermissionCallback;
import com.anggrayudi.storage.permission.PermissionReport;
import com.anggrayudi.storage.permission.PermissionResult;
import com.anggrayudi.storage.sample.R;

import org.jetbrains.annotations.NotNull;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import static com.anggrayudi.storage.sample.activity.MainActivity.REQUEST_CODE_CREATE_FILE;
import static com.anggrayudi.storage.sample.activity.MainActivity.REQUEST_CODE_PICK_FILE;
import static com.anggrayudi.storage.sample.activity.MainActivity.REQUEST_CODE_PICK_FOLDER;

/**
 * Created on 17/07/21
 *
 * @author Anggrayudi H
 */
public class JavaActivity extends AppCompatActivity {

    private final ActivityPermissionRequest permissionRequest = new ActivityPermissionRequest.Builder(this)
            .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            .withCallback(new PermissionCallback() {
                @Override
                public void onPermissionsChecked(@NotNull PermissionResult result, boolean fromSystemDialog) {
                    String grantStatus = result.getAreAllPermissionsGranted() ? "granted" : "denied";
                    Toast.makeText(getBaseContext(), "Storage permissions are " + grantStatus, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onShouldRedirectToSystemSettings(@NotNull List<PermissionReport> blockedPermissions) {
                    SimpleStorageHelper.redirectToSystemSettings(JavaActivity.this);
                }
            })
            .build();

    private final SimpleStorageHelper storageHelper = new SimpleStorageHelper(this);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupSimpleStorage(savedInstanceState);
        setupButtonActions();
    }

    private void setupButtonActions() {
        findViewById(R.id.btnRequestStoragePermission).setOnClickListener(v -> permissionRequest.check());
        findViewById(R.id.btnRequestStoragePermission).setEnabled(Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28);
        findViewById(R.id.btnSelectFolder).setOnClickListener(v -> storageHelper.openFolderPicker(REQUEST_CODE_PICK_FOLDER));
        findViewById(R.id.btnSelectFile).setOnClickListener(v -> storageHelper.openFilePicker(REQUEST_CODE_PICK_FILE));
        findViewById(R.id.btnCreateFile).setOnClickListener(v -> storageHelper.createFile("text/plain", "File name", null, REQUEST_CODE_CREATE_FILE));
    }

    private void setupSimpleStorage(Bundle savedState) {
        if (savedState != null) {
            storageHelper.onRestoreInstanceState(savedState);
        }
        storageHelper.setOnStorageAccessGranted((requestCode, root) -> {
            String absolutePath = DocumentFileUtils.getAbsolutePath(root, getBaseContext());
            Toast.makeText(
                    getBaseContext(),
                    getString(com.anggrayudi.storage.R.string.ss_selecting_root_path_success_without_open_folder_picker, absolutePath),
                    Toast.LENGTH_SHORT
            ).show();
            return null;
        });
        storageHelper.setOnFileSelected((requestCode, files) -> {
            String message = "File selected: " + DocumentFileUtils.getFullName(files.get(0));
            Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
            return null;
        });
        storageHelper.setOnFolderSelected((requestCode, folder) -> {
            String message = "Folder selected: " + DocumentFileUtils.getAbsolutePath(folder, getBaseContext());
            Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
            return null;
        });
        storageHelper.setOnFileCreated((requestCode, file) -> {
            String message = "File created: " + file.getName();
            Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
            return null;
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        storageHelper.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        storageHelper.onRestoreInstanceState(savedInstanceState);
    }
}
