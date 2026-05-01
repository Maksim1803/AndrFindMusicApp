package com.example.andrfindmusicapp.ui.player.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.ItemSmallTrackBinding
import com.example.andrfindmusicapp.utils.TimeUtils

// Класс для адаптера вертикального плейлиста в плеере
class SmallPlaylistAdapter(
    private val onItemClick: (Track) -> Unit
) : RecyclerView.Adapter<SmallPlaylistAdapter.ViewHolder>() {

    private var tracks: List<Track> = emptyList()
    private var currentPlayingTrackId: String? = null

    // Метод для обновления данных плейлиста
    fun updateData(newTracks: List<Track>) {
        tracks = newTracks
        notifyDataSetChanged()
    }

    // Метод для обновления текущего играющего трека
    fun setCurrentTrack(trackId: String?) {
        currentPlayingTrackId = trackId
        notifyDataSetChanged()
    }

    // Класс для хранения ссылок на элементы интерфейса одного элемента плейлиста
    class ViewHolder(val binding: ItemSmallTrackBinding) : RecyclerView.ViewHolder(binding.root)

    // Метод для создания ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSmallTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    // Метод для привязки данных трека к ViewHolder и управления индикацией воспроизведения
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        val isPlaying = track.id == currentPlayingTrackId

        with(holder.binding) {
            smallPoster.load(track.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_launcher_background)
            }
            smallTitle.text = track.name
            smallArtist.text = track.artistName
            smallDuration.text = TimeUtils.formatSeconds(track.duration)
            
            // Показываем дискету на мини-обложке, если трек локальный
            val isLocal = track.audioUrl.startsWith("content://") || track.audioUrl.startsWith("file://")
            localIndicatorSmall.visibility = if (isLocal) View.VISIBLE else View.GONE
            
            // Подсветка фона и иконка треугольника
            if (isPlaying) {
                root.setCardBackgroundColor(ContextCompat.getColor(root.context, R.color.item_playing_background))
                icPlaying.visibility = View.VISIBLE
            } else {
                root.setCardBackgroundColor(ContextCompat.getColor(root.context, R.color.item_background))
                icPlaying.visibility = View.GONE
            }

            root.setOnClickListener { onItemClick(track) }
        }
    }

    // Метод для получения общего количества элементов в плейлисте
    override fun getItemCount() = tracks.size
}