package com.anggrayudi.storage.sample.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.anggrayudi.storage.SimpleStorageHelper;
import com.anggrayudi.storage.callback.FileCallback;
import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.DocumentFileType;
import com.anggrayudi.storage.file.DocumentFileUtils;
import com.anggrayudi.storage.file.PublicDirectory;
import com.anggrayudi.storage.media.FileDescription;
import com.anggrayudi.storage.media.MediaFile;
import com.anggrayudi.storage.sample.R;

import timber.log.Timber;

/**
 * Created on 08/08/21
 *
 * @author Anggrayudi H
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String PREF_SAVE_LOCATION = "saveLocation";

    private final SimpleStorageHelper storageHelper = new SimpleStorageHelper(this);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        if (savedInstanceState != null) {
            storageHelper.onRestoreInstanceState(savedInstanceState);
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        // Use 'Download' as default save location
        String downloadsFolder = PublicDirectory.DOWNLOADS.getAbsolutePath();
        Preference saveLocationPref = findPreference(PREF_SAVE_LOCATION);
        saveLocationPref.setSummary(preferences.getString(PREF_SAVE_LOCATION, downloadsFolder));
        saveLocationPref.setOnPreferenceClickListener(preference -> {
            storageHelper.openFolderPicker();
            return true;
        });
        storageHelper.setOnFolderSelected((requestCode, folder) -> {
            final String path = DocumentFileUtils.getAbsolutePath(folder, requireContext());
            preferences.edit().putString(PREF_SAVE_LOCATION, path).apply();
            saveLocationPref.setSummary(path);
            return null;
        });
    }

    @Override
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        storageHelper.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @WorkerThread
    private void moveFileToSaveLocation(@NonNull DocumentFile sourceFile) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String downloadsFolder = PublicDirectory.DOWNLOADS.getAbsolutePath();
        String saveLocationPath = preferences.getString(PREF_SAVE_LOCATION, downloadsFolder);
        DocumentFile saveLocationFolder = DocumentFileCompat.fromFullPath(requireContext(), saveLocationPath, DocumentFileType.FOLDER, true);
        if (saveLocationFolder != null) {
            // write any files into folder 'saveLocationFolder'
            DocumentFileUtils.moveFileTo(sourceFile, requireContext(), saveLocationFolder, null, createCallback());
        } else {
            FileDescription fileDescription = new FileDescription(sourceFile.getName(), "", sourceFile.getType());
            DocumentFileUtils.moveFileToDownloadMedia(sourceFile, requireContext(), fileDescription, createCallback());
        }
    }

    private FileCallback createCallback() {
        return new FileCallback() {
            @Override
            public void onReport(Report report) {
                Timber.d("Progress: %s", report.getProgress());
            }

            @Override
            public void onFailed(ErrorCode errorCode) {
                Toast.makeText(requireContext(), errorCode.toString(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCompleted(@NonNull Object file) {
                final Uri uri;
                final Context context = requireContext();

                if (file instanceof MediaFile) {
                    final MediaFile mediaFile = (MediaFile) file;
                    uri = mediaFile.getUri();
                } else if (file instanceof DocumentFile) {
                    final DocumentFile documentFile = (DocumentFile) file;
                    uri = DocumentFileUtils.isRawFile(documentFile)
                            ? FileProvider.getUriForFile(context, context.getPackageName() + ".provider", DocumentFileUtils.toRawFile(documentFile, context))
                            : documentFile.getUri();
                } else {
                    return;
                }

                Toast.makeText(context, "Completed. File URI: " + uri.toString(), Toast.LENGTH_SHORT).show();
            }
        };
    }
}
