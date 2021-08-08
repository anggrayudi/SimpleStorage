package com.anggrayudi.storage.sample.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.anggrayudi.storage.sample.fragment.SettingsFragment

/**
 * Created on 08/08/21
 * @author Anggrayudi H
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }
}