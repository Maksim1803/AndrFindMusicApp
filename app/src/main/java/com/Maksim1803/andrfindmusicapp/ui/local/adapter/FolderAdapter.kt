package com.Maksim1803.andrfindmusicapp.ui.local.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.Maksim1803.andrfindmusicapp.R

// Адаптер для отображения списка папок (каталогов) с музыкой
class FolderAdapter(
    private val onFolderClick: (String) -> Unit
) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

    private var folders: List<Pair<String, Int>> = emptyList()

    // Метод для обновления данных (имя папки и количество треков в ней)
    fun updateData(newFolders: List<Pair<String, Int>>) {
        folders = newFolders
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_folder_name)
        val icon: ImageView = view.findViewById(R.id.iv_folder_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (folderName, trackCount) = folders[position]
        
        // Красивое имя для папки Jamendo
        val displayName = if (folderName == "Jamendo_Tracks") {
            holder.itemView.context.getString(R.string.folder_jamendo)
        } else {
            folderName
        }

        holder.name.text = "$displayName ($trackCount)"
        holder.icon.setImageResource(R.drawable.ic_folder)
        
        holder.itemView.setOnClickListener { onFolderClick(folderName) }
    }

    override fun getItemCount() = folders.size
}
