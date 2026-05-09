package com.example.andrfindmusicapp.ui.player.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.ItemSmallTrackBinding
import com.example.andrfindmusicapp.utils.TimeUtils

// Адаптер для отображения очереди воспроизведения в плеере
class PlaylistAdapter(
    private val onItemClick: (Track) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    private var tracks: List<Track> = emptyList()
    private var currentTrackId: String? = null

    fun updateData(newTracks: List<Track>) {
        tracks = newTracks
        notifyDataSetChanged()
    }

    fun setCurrentTrack(trackId: String?) {
        currentTrackId = trackId
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemSmallTrackBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSmallTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        with(holder.binding) {
            smallTitle.text = track.name
            smallArtist.text = track.artistName
            smallDuration.text = TimeUtils.formatSeconds(track.duration ?: 0)
            
            // Загружаем картинку. Если её нет - покажем "человечка" (placeholder)
            smallPoster.load(track.imageUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
            }

            // Выделяем текущий играющий трек
            val isPlaying = track.id == currentTrackId
            icPlaying.visibility = if (isPlaying) View.VISIBLE else View.GONE
            smallItemContainer.alpha = if (isPlaying) 1.0f else 0.7f
            
            root.setOnClickListener { onItemClick(track) }
        }
    }

    override fun getItemCount() = tracks.size
}
