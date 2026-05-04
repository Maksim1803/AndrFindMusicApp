package com.example.andrfindmusicapp.ui.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.databinding.FragmentLocalTracksBinding
import com.example.andrfindmusicapp.ui.home.adapter.HomeAdapter
import com.example.andrfindmusicapp.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Фрагмент для отображения и воспроизведения локальных аудиофайлов с устройства
@AndroidEntryPoint
class LocalTracksFragment : Fragment() {
    private var _binding: FragmentLocalTracksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocalTracksViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    
    private lateinit var adapter: HomeAdapter

    // Лаунчер для запроса разрешений на чтение медиафайлов
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.loadLocalTracks()
        } else {
            Toast.makeText(requireContext(), "Разрешение отклонено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocalTracksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        adapter = HomeAdapter(
            onItemClick = { track ->
                mainViewModel.playTrack(track)
                findNavController().navigate(R.id.navigation_player)
            },
            onFavoriteClick = { track ->
                mainViewModel.toggleFavorite(track)
            },
            onDeleteClick = { track ->
                // Показываем подтверждение удаления
                androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.TimerDialogTheme)
                    .setTitle("Удалить файл?")
                    .setMessage("Вы действительно хотите удалить ${track.name} с устройства?")
                    .setPositiveButton("Удалить") { _, _ ->
                        viewModel.deleteTrack(track)
                        Toast.makeText(requireContext(), "Трек удален", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        )
        binding.rvLocalTracks.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.localTracks.collectLatest { tracks ->
                        adapter.updateData(tracks)
                        binding.tvNoTracks.isVisible = tracks.isEmpty() && !viewModel.isLoading.value
                    }
                }
                launch {
                    mainViewModel.favoriteIds.collectLatest { favIds ->
                        adapter.updateFavorites(favIds)
                    }
                }
                launch {
                    viewModel.isLoading.collectLatest { isLoading ->
                        binding.progressBar.isVisible = isLoading
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadLocalTracks()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
