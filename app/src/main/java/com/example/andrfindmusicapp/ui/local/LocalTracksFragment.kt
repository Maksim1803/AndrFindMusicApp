package com.example.andrfindmusicapp.ui.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentLocalTracksBinding
import com.example.andrfindmusicapp.ui.home.adapter.HomeAdapter
import com.example.andrfindmusicapp.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Класс для фрагмента, отображающего локальные (скачанные) треки на устройстве
@AndroidEntryPoint
class LocalTracksFragment : Fragment() {
    private var _binding: FragmentLocalTracksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocalTracksViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var adapter: HomeAdapter

    // Обработчик запроса разрешений на чтение памяти
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.loadLocalTracks()
        }
    }

    // Метод для создания View и инициализации binding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocalTracksBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Метод для настройки фрагмента после создания View
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
        checkPermissionAndLoad()
    }

    // Метод для настройки RecyclerView и его адаптера для локальных треков
    private fun setupRecyclerView() {
        adapter = HomeAdapter(
            onItemClick = { track ->
                mainViewModel.playTrackWithPlaylist(track, viewModel.localTracks.value ?: emptyList())
                findNavController().navigate(R.id.navigation_player)
            },
            onFavoriteClick = { track ->
                mainViewModel.toggleFavorite(track)
            },
            onReminderClick = { track ->
                showReminderDialog(track)
            },
            onDeleteClick = { track ->
                showDeleteConfirmDialog(track)
            }
        )
        binding.rvLocalTracks.adapter = adapter
    }

    // Метод для показа диалога подтверждения удаления локального файла
    private fun showDeleteConfirmDialog(track: Track) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_track_title)
            .setMessage(getString(R.string.delete_track_message, track.name))
            .setPositiveButton(R.string.delete_confirm) { _, _ ->
                if (mainViewModel.deleteTrack(track)) {
                    android.widget.Toast.makeText(requireContext(), R.string.track_deleted, android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.loadLocalTracks() // Обновляем список
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // Метод для показа диалога удаления напоминания
    private fun showReminderDialog(track: Track) {
        val timeMillis = mainViewModel.reminderTimes.value[track.id] ?: return
        val calendar = java.util.Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val dateStr = android.text.format.DateFormat.getDateFormat(requireContext()).format(calendar.time)
        val timeStr = android.text.format.DateFormat.getTimeFormat(requireContext()).format(calendar.time)

        com.example.andrfindmusicapp.utils.DialogHelper.showReminderDeleteDialog(
            requireContext(),
            dateStr,
            timeStr
        ) {
            mainViewModel.removeReminder(track.id)
            android.widget.Toast.makeText(requireContext(), R.string.reminder_removed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Метод для подписки на изменения данных во ViewModel
    private fun observeViewModel() {
        viewModel.localTracks.observe(viewLifecycleOwner) { tracks ->
            adapter.updateData(tracks)
            binding.tvNoTracks.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Наблюдаем за избранным, чтобы звездочки обновлялись
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.favoriteIds.collectLatest { favIds ->
                adapter.updateFavorites(favIds)
            }
        }

        // Наблюдаем за напоминаниями, чтобы колокольчики обновлялись
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.reminderIds.collectLatest { remIds ->
                adapter.updateReminders(remIds)
            }
        }
    }

    // Метод для проверки разрешений и запуска загрузки локальных треков
    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.loadLocalTracks()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
