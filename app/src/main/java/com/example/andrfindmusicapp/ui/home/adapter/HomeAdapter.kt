package com.example.andrfindmusicapp.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.MusicItemBinding
import com.example.andrfindmusicapp.utils.TimeUtils

// Класс для адаптера списка треков на главном экране
class HomeAdapter(
    private val onItemClick: (Track) -> Unit,
    private val onFavoriteClick: (Track) -> Unit,
    private val onDeleteClick: ((Track) -> Unit)? = null
) : RecyclerView.Adapter<HomeAdapter.ViewHolder>() {

    private var tracks: List<Track> = emptyList()
    private var favoriteIds: Set<String> = emptySet()

    // Метод для обновления списка треков
    fun updateData(newTracks: List<Track>) {
        tracks = newTracks
        notifyDataSetChanged()
    }

    // Метод для обновления списка идентификаторов избранных треков
    fun updateFavorites(newFavoriteIds: Set<String>) {
        favoriteIds = newFavoriteIds
        notifyDataSetChanged()
    }

    // Класс для хранения ссылок на элементы интерфейса одного элемента списка
    class ViewHolder(val binding: MusicItemBinding) : RecyclerView.ViewHolder(binding.root)

    // Метод для создания ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MusicItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    // Метод для привязки данных трека к ViewHolder
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        with(holder.binding) {
            title.text = track.name
            artist.text = track.artistName
            album.text = track.albumName
            duration.text = TimeUtils.formatSeconds(track.duration ?: 0)
            poster.load(track.imageUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
            }
            
            val isFavorite = favoriteIds.contains(track.id)
            favoriteIcon.setImageResource(
                if (isFavorite) com.example.andrfindmusicapp.R.drawable.ic_star 
                else com.example.andrfindmusicapp.R.drawable.ic_star_border
            )

            favoriteIcon.setOnClickListener { onFavoriteClick(track) }
            
            // Если передан колбэк для удаления, показываем иконку корзины
            if (onDeleteClick != null) {
                deleteIcon.visibility = android.view.View.VISIBLE
                deleteIcon.setOnClickListener { onDeleteClick.invoke(track) }
            } else {
                deleteIcon.visibility = android.view.View.GONE
            }

            // Показываем индикатор локального файла (дискету) на постере
            val isLocal = track.audioUrl?.startsWith("content://") == true || track.audioUrl?.startsWith("file://") == true
            localIndicatorIcon.visibility = if (isLocal) android.view.View.VISIBLE else android.view.View.GONE

            root.setOnClickListener { onItemClick(track) }
        }
    }

    // Метод для получения общего количества элементов в списке
    override fun getItemCount() = tracks.size
}