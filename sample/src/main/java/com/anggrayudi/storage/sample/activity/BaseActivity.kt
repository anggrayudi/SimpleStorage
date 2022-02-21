package com.anggrayudi.storage.sample.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.anggrayudi.storage.SimpleStorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

/**
 * Created on 04/01/22
 * @author Anggrayudi H
 */
open class BaseActivity : AppCompatActivity() {

    private val job = Job()
    protected val ioScope = CoroutineScope(Dispatchers.IO + job)
    protected val uiScope = CoroutineScope(Dispatchers.Main + job)

    protected lateinit var storageHelper: SimpleStorageHelper
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageHelper = SimpleStorageHelper(this, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storageHelper.onRestoreInstanceState(savedInstanceState)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }
}