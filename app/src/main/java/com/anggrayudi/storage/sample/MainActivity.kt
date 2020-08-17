package com.anggrayudi.storage.sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.anggrayudi.storage.SimpleStorage
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var simpleStorage: SimpleStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        simpleStorage = SimpleStorage(this)

        btnRequestStoragePermission.setOnClickListener {
            simpleStorage.requestStoragePermission()
        }

        btnSelectFolder.setOnClickListener {
            startActivityForResult(simpleStorage.requireExternalRootAccess(), 100)
        }

        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAlarms")
        Timber.d("onCreate: ${uri.path}")
        Log.d("MainActivity", "onCreate: ${uri.path}")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        simpleStorage.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        btnSelectFolder.isEnabled = SimpleStorage.hasStoragePermission(this)
    }
}