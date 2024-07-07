package com.anggrayudi.storage.sample

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.StorageId.PRIMARY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Created on 12/14/20
 * @author Anggrayudi H
 */
class StorageInfoAdapter(
    private val context: Context,
    private val ioScope: CoroutineScope,
    private val uiScope: CoroutineScope
) : RecyclerView.Adapter<StorageInfoAdapter.ViewHolder>() {

    private val storageIds = DocumentFileCompat.getStorageIds(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_item_storage_info, parent, false))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        ioScope.launch {
            val storageId = storageIds[position]
            val storageName = if (storageId == PRIMARY) "External Storage" else storageId
            val storageCapacity = Formatter.formatFileSize(context, DocumentFileCompat.getStorageCapacity(context, storageId))
            val storageUsedSpace = Formatter.formatFileSize(context, DocumentFileCompat.getUsedSpace(context, storageId))
            val storageFreeSpace = Formatter.formatFileSize(context, DocumentFileCompat.getFreeSpace(context, storageId))
            uiScope.launch {
                holder.run {
                    tvStorageName.text = storageName
                    tvStorageCapacity.text = "Capacity: $storageCapacity"
                    tvStorageUsedSpace.text = "Used Space: $storageUsedSpace"
                    tvStorageFreeSpace.text = "Free Space: $storageFreeSpace"
                    btnShowGrantedUri.setOnClickListener { showGrantedUris(it.context, storageId) }
                    if (storageId == PRIMARY && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        // No URI permission required for external storage
                        btnShowGrantedUri.visibility = View.GONE
                    }
                }
            }
        }
    }

    /**
     * A storageId may contains more than one granted URIs
     */
    @SuppressLint("NewApi")
    private fun showGrantedUris(context: Context, filterStorageId: String) {
        val grantedPaths = DocumentFileCompat.getAccessibleAbsolutePaths(context)[filterStorageId]
        if (grantedPaths == null) {
            MaterialDialog(context)
                .message(text = "No permission granted on storage ID \"$filterStorageId\"")
                .positiveButton()
                .show()
        } else {
            MaterialDialog(context)
                .title(text = "Granted paths for \"$filterStorageId\"")
                .listItems(items = grantedPaths.toList().sorted())
                .show()
        }
    }

    override fun getItemCount() = storageIds.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        internal val tvStorageName = view.findViewById<TextView>(R.id.tvStorageName)
        internal val tvStorageCapacity = view.findViewById<TextView>(R.id.tvStorageCapacity)
        internal val tvStorageUsedSpace = view.findViewById<TextView>(R.id.tvStorageUsedSpace)
        internal val tvStorageFreeSpace = view.findViewById<TextView>(R.id.tvStorageFreeSpace)
        internal val btnShowGrantedUri = view.findViewById<Button>(R.id.btnShowGrantedPaths)
    }
}