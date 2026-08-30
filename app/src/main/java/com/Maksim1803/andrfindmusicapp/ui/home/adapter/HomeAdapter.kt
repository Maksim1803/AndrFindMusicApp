package com.Maksim1803.andrfindmusicapp.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.Maksim1803.andrfindmusicapp.data.model.Track
import com.Maksim1803.andrfindmusicapp.databinding.MusicItemBinding
import com.Maksim1803.andrfindmusicapp.utils.TimeUtils

// Класс для адаптера списка треков на главном экране
class HomeAdapter(
    private val onItemClick: (Track) -> Unit,
    private val onFavoriteClick: (Track) -> Unit,
    private val onReminderClick: (Track) -> Unit,
    private val onDeleteClick: ((Track) -> Unit)? = null,
    private val onLongClick: ((Track) -> Unit)? = null
) : RecyclerView.Adapter<HomeAdapter.ViewHolder>() {

    private var tracks: List<Track> = emptyList()
    private var favoriteIds: Set<String> = emptySet()
    private var reminderIds: Set<String> = emptySet()
    private var metadataOverrides: Map<String, Track> = emptyMap()

    // Метод для обновления списка треков
    fun updateData(newTracks: List<Track>) {
        tracks = newTracks
        notifyDataSetChanged()
    }

    // Метод для обновления переименований
    fun updateOverrides(overrides: Map<String, Track>) {
        metadataOverrides = overrides
        notifyDataSetChanged()
    }

    // Метод для обновления списка идентификаторов избранных треков
    fun updateFavorites(newFavoriteIds: Set<String>) {
        favoriteIds = newFavoriteIds
        notifyDataSetChanged()
    }

    // Метод для обновления списка идентификаторов напоминаний
    fun updateReminders(newReminderIds: Set<String>) {
        reminderIds = newReminderIds
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
        val originalTrack = tracks[position]
        // Применяем оверрайды прямо при отрисовке (самый надежный способ)
        val track = metadataOverrides[originalTrack.id] ?: originalTrack

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
                if (isFavorite) com.Maksim1803.andrfindmusicapp.R.drawable.ic_star
                else com.Maksim1803.andrfindmusicapp.R.drawable.ic_star_border
            )

            // Метод для обработки клика по иконке избранного
            favoriteIcon.setOnClickListener { onFavoriteClick(originalTrack) }
            
            // Показываем иконку колокольчика, если установлено напоминание
            val hasReminder = reminderIds.contains(track.id)
            reminderIcon.visibility = if (hasReminder) View.VISIBLE else View.GONE
            
            // Метод для обработки клика по иконке напоминания
            reminderIcon.setOnClickListener { onReminderClick(originalTrack) }

            // Если передан колбэк для удаления, показываем иконку корзины
            if (onDeleteClick != null) {
                deleteIcon.visibility = View.VISIBLE
                deleteIcon.setOnClickListener { onDeleteClick.invoke(originalTrack) }
            } else {
                deleteIcon.visibility = View.GONE
            }

            // Показываем индикатор локального файла (дискету) на постере
            val isLocal = track.audioUrl?.startsWith("content://") == true || track.audioUrl?.startsWith("file://") == true
            localIndicatorIcon.visibility = if (isLocal) View.VISIBLE else View.GONE

            // Метод для обработки длинного клика по всему элементу (для переименования)
            root.setOnLongClickListener {
                onLongClick?.invoke(originalTrack)
                true
            }

            // Метод для обработки клика по всему элементу списка
            root.setOnClickListener { onItemClick(originalTrack) }
        }
    }

    // Метод для получения общего количества элементов в списке
    override fun getItemCount() = tracks.size
}
