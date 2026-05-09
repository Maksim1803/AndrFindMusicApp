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
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentFavoritesBinding
import com.example.andrfindmusicapp.ui.home.adapter.HomeAdapter
import com.example.andrfindmusicapp.ui.main.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import dagger.hilt.android.AndroidEntryPoint

// Класс для фрагмента, отображающего список избранных треков
@AndroidEntryPoint
class FavoritesFragment : Fragment() {
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: HomeAdapter
    private var favoriteTracks: List<Track> = emptyList()

    @javax.inject.Inject
    lateinit var trackDao: com.example.andrfindmusicapp.data.local.TrackDao

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = HomeAdapter(
            onItemClick = { track ->
                mainViewModel.playTrackWithPlaylist(track, favoriteTracks)
                findNavController().navigate(R.id.navigation_player)
            },
            onFavoriteClick = { track ->
                mainViewModel.toggleFavorite(track)
            }
        )
        binding.favoritesRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.favoritesRecycler.adapter = adapter
    }

    private fun observeViewModel() {
        // Подгружаем актуальный список избранного из БД
        viewLifecycleOwner.lifecycleScope.launch {
            trackDao.getAllFavorites().collectLatest { entities ->
                val allFavs = entities.map { entity ->
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
                // ФИЛЬТР: Оставляем в списке Избранное только онлайн-треки
                favoriteTracks = allFavs.filter { 
                    it.audioUrl?.startsWith("http") == true
                }
                adapter.updateData(favoriteTracks)
            }
        }

        // Обновляем иконки звезд через MainViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.favoriteIds.collectLatest { favIds ->
                adapter.updateFavorites(favIds)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
