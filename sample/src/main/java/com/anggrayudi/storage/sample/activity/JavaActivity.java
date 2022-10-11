package com.anggrayudi.storage.sample.activity;

import static com.anggrayudi.storage.sample.activity.MainActivity.REQUEST_CODE_CREATE_FILE;
import static com.anggrayudi.storage.sample.activity.MainActivity.REQUEST_CODE_PICK_FILE;
import static com.anggrayudi.storage.sample.activity.MainActivity.REQUEST_CODE_PICK_FOLDER;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.anggrayudi.storage.SimpleStorageHelper;
import com.anggrayudi.storage.callback.FileCallback;
import com.anggrayudi.storage.file.DocumentFileUtils;
import com.anggrayudi.storage.media.MediaFile;
import com.anggrayudi.storage.permission.ActivityPermissionRequest;
import com.anggrayudi.storage.permission.PermissionCallback;
import com.anggrayudi.storage.permission.PermissionReport;
import com.anggrayudi.storage.permission.PermissionResult;
import com.anggrayudi.storage.sample.R;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import timber.log.Timber;

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
                    getString(R.string.ss_selecting_root_path_success_without_open_folder_picker, absolutePath),
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

    private void moveFile(DocumentFile source, DocumentFile destinationFolder) {
        DocumentFileUtils.moveFileTo(source, getApplicationContext(), destinationFolder, null, new FileCallback() {
            @Override
            public void onConflict(@NotNull DocumentFile destinationFile, @NotNull FileCallback.FileConflictAction action) {
                // do stuff
            }

            @Override
            public void onCompleted(@NotNull Object result) {
                if (result instanceof DocumentFile) {
                    // do stuff
                } else if (result instanceof MediaFile) {
                    // do stuff
                }
            }

            @Override
            public void onReport(Report report) {
                Timber.d("%s", report.getProgress());
            }

            @Override
            public void onFailed(ErrorCode errorCode) {
                Timber.d("Error: %s", errorCode.toString());
            }
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
