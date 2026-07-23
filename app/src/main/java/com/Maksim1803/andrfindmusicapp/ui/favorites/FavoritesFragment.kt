package com.Maksim1803.andrfindmusicapp.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.Maksim1803.andrfindmusicapp.R
import com.Maksim1803.andrfindmusicapp.data.model.Track
import com.Maksim1803.andrfindmusicapp.databinding.FragmentFavoritesBinding
import com.Maksim1803.andrfindmusicapp.ui.home.adapter.HomeAdapter
import com.Maksim1803.andrfindmusicapp.ui.main.MainViewModel
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
    lateinit var trackDao: com.Maksim1803.andrfindmusicapp.data.local.TrackDao

    // Метод для создания View и инициализации binding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Метод для настройки логики фрагмента после создания View
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.notifyNoInternet()
    }

    // Метод для настройки RecyclerView и его адаптера
    private fun setupRecyclerView() {
        adapter = HomeAdapter(
            onItemClick = { track ->
                mainViewModel.playTrackWithPlaylist(track, favoriteTracks)
                findNavController().navigate(R.id.navigation_player)
            },
            onFavoriteClick = { track ->
                mainViewModel.toggleFavorite(track)
            },
            onReminderClick = { track ->
                showReminderDialog(track)
            }
        )
        binding.favoritesRecycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.favoritesRecycler.adapter = adapter
    }

    // Метод для подписки на изменения данных (избранное, напоминания)
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

        // Обновляем иконки напоминаний через MainViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.reminderIds.collectLatest { remIds ->
                adapter.updateReminders(remIds)
            }
        }
    }

    // Метод для показа диалога удаления напоминания
    private fun showReminderDialog(track: Track) {
        val timeMillis = mainViewModel.reminderTimes.value[track.id] ?: return
        val calendar = java.util.Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val dateStr = android.text.format.DateFormat.getDateFormat(requireContext()).format(calendar.time)
        val timeStr = android.text.format.DateFormat.getTimeFormat(requireContext()).format(calendar.time)

        com.Maksim1803.andrfindmusicapp.utils.DialogHelper.showReminderDeleteDialog(
            requireContext(),
            dateStr,
            timeStr
        ) {
            mainViewModel.removeReminder(track.id)
            android.widget.Toast.makeText(requireContext(), R.string.reminder_removed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
