package com.anggrayudi.storage.sample.fragment;

import com.anggrayudi.storage.SimpleStorageHelper;
import com.anggrayudi.storage.file.DocumentFileUtils;
import com.anggrayudi.storage.file.PublicDirectory;
import com.anggrayudi.storage.sample.R;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

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
}
