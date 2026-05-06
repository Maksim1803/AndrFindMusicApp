package com.example.andrfindmusicapp.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentFavoritesBinding
import com.example.andrfindmusicapp.ui.home.adapter.HomeAdapter
import com.example.andrfindmusicapp.ui.main.MainViewModel
import com.example.andrfindmusicapp.ui.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Класс для фрагмента, отображающего список избранных треков
@AndroidEntryPoint
class FavoritesFragment : Fragment() {
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
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
                mainViewModel.playTrack(track)
                findNavController().navigate(R.id.navigation_player)
            },
            onFavoriteClick = { track ->
                mainViewModel.toggleFavorite(track)
            }
        )
        binding.favoritesRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.favoritesRecycler.adapter = adapter
    }

    // Метод для подписки на обновления списка избранных треков из базы данных
    private fun observeFavorites() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
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
                    }
                }
                launch {
                    mainViewModel.favoriteIds.collectLatest { favIds ->
                        adapter.updateFavorites(favIds)
                    }
                }
            }
        }
    }

    // Метод для очистки ресурсов binding
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
