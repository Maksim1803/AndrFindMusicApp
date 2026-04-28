package com.example.andrfindmusicapp.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.local.AppDatabase
import com.example.andrfindmusicapp.data.local.TrackEntity
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentFavoritesBinding
import com.example.andrfindmusicapp.ui.home.adapter.HomeAdapter
import com.example.andrfindmusicapp.ui.player.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import dagger.hilt.android.AndroidEntryPoint

// Класс для фрагмента, отображающего список избранных треков
@AndroidEntryPoint
class FavoritesFragment : Fragment() {
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private lateinit var adapter: HomeAdapter

    @javax.inject.Inject
    lateinit var trackDao: com.example.andrfindmusicapp.data.local.TrackDao

    // Метод для создания View фрагмента и инициализации binding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Метод для настройки UI и загрузки данных после создания View
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeFavorites()
    }

    // Метод для настройки RecyclerView и его адаптера
    private fun setupRecyclerView() {
        adapter = HomeAdapter(
            onItemClick = { track ->
                playerViewModel.selectTrack(track)
                val bundle = Bundle().apply { putSerializable("track", track) }
                findNavController().navigate(R.id.navigation_player, bundle)
            },
            onFavoriteClick = { track ->
                toggleFavorite(track)
            }
        )
        binding.favoritesRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.favoritesRecycler.adapter = adapter
    }

    // Метод для подписки на обновления списка избранных треков из базы данных
    private fun observeFavorites() {
        viewLifecycleOwner.lifecycleScope.launch {
            trackDao.getAllFavorites().collectLatest { entities ->
                val tracks = entities.map { entity ->
                    Track(
                        id = entity.id,
                        name = entity.name,
                        artistName = entity.artistName,
                        albumName = entity.albumName,
                        audioUrl = entity.audioUrl,
                        imageUrl = entity.imageUrl,
                        duration = entity.duration
                    )
                }
                adapter.updateData(tracks)
                adapter.updateFavorites(tracks.map { it.id }.toSet())
            }
        }
    }

    // Метод для удаления трека из избранного
    private fun toggleFavorite(track: Track) {
        viewLifecycleOwner.lifecycleScope.launch {
            val isCurrentlyFav = trackDao.isFavorite(track.id)
            
            // Если трека нет в базе или он там без флага избранного, обновляем/вставляем
            val entity = TrackEntity(
                id = track.id,
                name = track.name,
                artistName = track.artistName,
                albumName = track.albumName,
                imageUrl = track.imageUrl,
                audioUrl = track.audioUrl,
                duration = track.duration,
                isFavorite = !isCurrentlyFav
            )
            trackDao.insertTrack(entity)
        }
    }

    // Метод для очистки ресурсов binding
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
