package com.Maksim1803.andrfindmusicapp.ui.local

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
import com.Maksim1803.andrfindmusicapp.R
import com.Maksim1803.andrfindmusicapp.data.model.Track
import com.Maksim1803.andrfindmusicapp.databinding.FragmentLocalTracksBinding
import com.Maksim1803.andrfindmusicapp.ui.home.adapter.HomeAdapter
import com.Maksim1803.andrfindmusicapp.ui.main.MainViewModel
import com.Maksim1803.andrfindmusicapp.utils.PreferenceProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// Класс для фрагмента, отображающего локальные (скачанные) треки на устройстве
@AndroidEntryPoint
class LocalTracksFragment : Fragment() {
    private var _binding: FragmentLocalTracksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocalTracksViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    @Inject
    lateinit var preferenceProvider: PreferenceProvider

    private lateinit var trackAdapter: HomeAdapter
    private lateinit var folderAdapter: com.Maksim1803.andrfindmusicapp.ui.local.adapter.FolderAdapter
    private var currentFolderName: String? = null

    // Обработчик системного запроса на изменение файла (для Android 10+)
    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onPermissionGranted()
        }
    }

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
        setupRecyclerViews()
        setupListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndLoad()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            currentFolderName = null
            updateUI()
        }
    }

    // Метод для настройки RecyclerView и его адаптера для локальных треков
    private fun setupRecyclerViews() {
        trackAdapter = HomeAdapter(
            onItemClick = { track ->
                val playlist = viewModel.localFolders.value?.get(currentFolderName) ?: emptyList()
                mainViewModel.playTrackWithPlaylist(track, playlist)
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
            },
            onLongClick = { track ->
                showEditTrackDialog(track)
            }
        )

        folderAdapter = com.Maksim1803.andrfindmusicapp.ui.local.adapter.FolderAdapter(
            onFolderClick = { folderName ->
                currentFolderName = folderName
                updateUI()
            }
        )
    }

    private fun showEditTrackDialog(track: Track) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_track, null)
        val etFileName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_file_name)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_track_name)
        val etAlbum = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_album)
        val etArtist = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_artist)
        val etGenre = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_genre)

        etFileName.setText(track.displayName ?: "")
        etName.setText(track.name ?: "")
        etAlbum.setText(track.albumName ?: "")
        etArtist.setText(track.artistName ?: "")
        etGenre.setText(track.genre ?: "")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_track_title)
            .setView(dialogView)
            .setPositiveButton(R.string.yes) { _, _ ->
                val newName = etName.text.toString().trim()
                val newFileName = etFileName.text.toString().trim()
                if (newName.isNotEmpty() && newFileName.isNotEmpty()) {
                    viewModel.updateTrackMetadata(
                        track = track,
                        newName = newName,
                        newAlbum = etAlbum.text.toString().trim(),
                        newArtist = etArtist.text.toString().trim(),
                        newGenre = etGenre.text.toString().trim(),
                        newYear = track.year ?: "", // Оставляем старый год
                        newFileName = newFileName
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()

        // Настраиваем окно для максимального поднятия вверх
        dialog.window?.apply {
            // Позволяем диалогу заходить в зону статус-бара если нужно
            addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            
            setGravity(android.view.Gravity.TOP)
            val params = attributes
            // Используем небольшое отрицательное значение для компенсации отступов Material диалога
            params.y = -20.toPx(requireContext())
            attributes = params
            
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    // Вспомогательная функция для перевода dp в px
    private fun Int.toPx(context: android.content.Context): Int = 
        (this * context.resources.displayMetrics.density).toInt()

    private fun updateUI() {
        val folders = viewModel.localFolders.value
        
        if (folders == null) {
            binding.tvNoTracks.visibility = View.GONE
            return
        }
        
        if (currentFolderName == null) {
            if (binding.rvLocalTracks.adapter != folderAdapter) {
                binding.rvLocalTracks.adapter = folderAdapter
            }
            val folderList = folders.map { it.key to it.value.size }.sortedBy { it.first }
            folderAdapter.updateData(folderList)
            
            binding.tvFolderTitle.text = getString(R.string.local_folders)
            binding.btnBack.visibility = View.GONE
            binding.tvNoTracks.visibility = if (folderList.isEmpty()) View.VISIBLE else View.GONE
        } else {
            if (binding.rvLocalTracks.adapter != trackAdapter) {
                binding.rvLocalTracks.adapter = trackAdapter
            }
            val tracksInFolder = folders[currentFolderName] ?: emptyList()
            trackAdapter.updateData(tracksInFolder)
            
            val displayName = if (currentFolderName == "Jamendo_Tracks") {
                getString(R.string.folder_jamendo)
            } else {
                currentFolderName
            }
            binding.tvFolderTitle.text = displayName
            binding.btnBack.visibility = View.VISIBLE
            binding.tvNoTracks.visibility = if (tracksInFolder.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // Метод для показа диалога подтверждения удаления локального файла
    private fun showDeleteConfirmDialog(track: Track) {
        MaterialAlertDialogBuilder(requireContext())
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

        com.Maksim1803.andrfindmusicapp.utils.DialogHelper.showReminderDeleteDialog(
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
        viewModel.localFolders.observe(viewLifecycleOwner) {
            updateUI()
        }

        viewModel.metadataOverrides.observe(viewLifecycleOwner) { overrides ->
            trackAdapter.updateOverrides(overrides)
            // Уведомляем MainViewModel, чтобы плеер тоже узнал о правках
            mainViewModel.updateMetadataOverrides()
        }

        viewModel.pendingIntent.observe(viewLifecycleOwner) { intent ->
            intent?.let {
                val request = androidx.activity.result.IntentSenderRequest.Builder(it).build()
                intentSenderLauncher.launch(request)
                viewModel.clearPendingIntent()
            }
        }

        viewModel.renameEvent.observe(viewLifecycleOwner) { event ->
            val (success, error) = event
            if (success) {
                android.widget.Toast.makeText(requireContext(), R.string.rename_success, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val message = error ?: getString(R.string.rename_error)
                android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
            }
        }

        viewModel.localTracks.observe(viewLifecycleOwner) { tracks ->
            // Обновляем сохраненное количество
            preferenceProvider.saveLastTrackCount(tracks.size)
            // Если мы не используем папки (вдруг?), можно раскомментировать updateUI()
            // Но пока всё завязано на localFolders
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Наблюдаем за избранным, чтобы звездочки обновлялись
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.favoriteIds.collectLatest { favIds ->
                trackAdapter.updateFavorites(favIds)
            }
        }

        // Наблюдаем за напоминаниями, чтобы колокольчики обновлялись
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.reminderIds.collectLatest { remIds ->
                trackAdapter.updateReminders(remIds)
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
