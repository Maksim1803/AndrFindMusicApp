package com.example.andrfindmusicapp.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentPlayerBinding
import com.example.andrfindmusicapp.ui.main.MainViewModel
import com.example.andrfindmusicapp.ui.player.adapter.SmallPlaylistAdapter
import com.example.andrfindmusicapp.utils.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Класс для отображения экрана плеера и управления воспроизведением
@AndroidEntryPoint
class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val playerViewModel: PlayerViewModel by activityViewModels()
    
    private lateinit var smallPlaylistAdapter: SmallPlaylistAdapter
    private var currentToast: Toast? = null
    
    @javax.inject.Inject
    lateinit var localTrackProvider: com.example.andrfindmusicapp.data.local.LocalTrackProvider
    
    private val handler = Handler(Looper.getMainLooper())
    
    // Регистратор для запроса разрешения на запись
    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            mainViewModel.currentTrack.value?.let { downloadTrack(it) }
        } else {
            showToast(R.string.download_failed)
        }
    }

    // Метод для вывода всплывающих уведомлений с автоматической отменой предыдущих
    private fun showToast(messageResId: Int, vararg args: Any) {
        currentToast?.cancel()
        val message = if (args.isEmpty()) getString(messageResId) else getString(messageResId, *args)
        currentToast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        currentToast?.show()
    }

    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            _binding?.let { b ->
                mainViewModel.getController()?.let { p ->
                    val currentPos = p.currentPosition
                    b.seekBar.progress = currentPos.toInt()
                    b.tvCurrentTime.text = TimeUtils.formatTime(currentPos)
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()

        // Если пришли с конкретным треком (например, из поиска)
        val trackFromArgs = arguments?.getSerializable("track") as? Track
        trackFromArgs?.let {
            mainViewModel.playTrack(it)
        }

        setupListeners()
    }

    // Метод для инициализации наблюдателей за LiveData и Flow
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Слушаем текущий трек
                launch {
                    mainViewModel.currentTrack.collectLatest { track ->
                        track?.let {
                            updateUI(it)
                            smallPlaylistAdapter.setCurrentTrack(it.id)
                            playerViewModel.clearLyrics()
                            binding.lyricsContainer.visibility = View.GONE
                            binding.rvSmallPlaylist.visibility = View.VISIBLE
                        }
                    }
                }

                // Слушаем текст песни
                launch {
                    playerViewModel.lyrics.collectLatest { lyrics ->
                        if (lyrics != null) {
                            binding.tvLyrics.text = lyrics
                        } else if (playerViewModel.isLyricsLoading.value.not() && binding.lyricsContainer.visibility == View.VISIBLE) {
                            binding.tvLyrics.text = getString(R.string.no_lyrics_found)
                        }
                    }
                }

                // Слушаем состояние загрузки текста
                launch {
                    playerViewModel.isLyricsLoading.collectLatest { isLoading ->
                        if (isLoading) {
                            binding.tvLyrics.text = getString(R.string.loading_lyrics)
                        }
                    }
                }

                // Слушаем статус избранного для текущего трека
                launch {
                    kotlinx.coroutines.flow.combine(
                        mainViewModel.currentTrack,
                        mainViewModel.favoriteIds
                    ) { track, favIds ->
                        track?.id?.let { id -> favIds.contains(id) } ?: false
                    }.collectLatest { isFav ->
                        binding.detailsFavorite.setImageResource(
                            if (isFav) R.drawable.ic_star else R.drawable.ic_star_border
                        )
                    }
                }

                // Слушаем состояние воспроизведения (Play/Pause)
                launch {
                    mainViewModel.isPlaying.collectLatest { isPlaying ->
                        binding.btnPlayPause.setImageResource(
                            if (isPlaying) android.R.drawable.ic_media_pause 
                            else android.R.drawable.ic_media_play
                        )
                        if (isPlaying) handler.post(updateSeekBarTask)
                        else handler.removeCallbacks(updateSeekBarTask)
                    }
                }

                // Слушаем плейлист
                launch {
                    mainViewModel.playlist.collectLatest { tracks ->
                        smallPlaylistAdapter.updateData(tracks)
                    }
                }

                // Слушаем состояние таймера для иконки
                launch {
                    mainViewModel.sleepTimerManager.remainingTime.observe(viewLifecycleOwner) { remaining ->
                        binding.btnSleepTimer.setImageResource(
                            if (remaining > 0) R.drawable.ic_alarm_on else R.drawable.ic_alarm_off
                        )
                    }
                }
            }
        }
    }

    // Метод для настройки списка (RecyclerView) воспроизведения
    private fun setupRecyclerView() {
        smallPlaylistAdapter = SmallPlaylistAdapter { clickedTrack ->
            mainViewModel.playTrack(clickedTrack)
        }
        binding.rvSmallPlaylist.apply {
            adapter = smallPlaylistAdapter
            isNestedScrollingEnabled = true
            setHasFixedSize(true)
        }
    }

    // Метод для настройки слушателей нажатий на кнопки
    private fun setupListeners() {
        binding.btnPlayPause.setOnClickListener {
            mainViewModel.togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            mainViewModel.skipToNext()
        }

        binding.btnPrev.setOnClickListener {
            mainViewModel.skipToPrevious()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mainViewModel.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.detailsFavorite.setOnClickListener {
            mainViewModel.currentTrack.value?.let { track ->
                mainViewModel.toggleFavorite(track)
            }
        }

        binding.btnSleepTimer.setOnClickListener {
            showSleepTimerDialog()
        }

        binding.btnShare.setOnClickListener {
            mainViewModel.currentTrack.value?.let { track ->
                shareTrack(track)
            }
        }

        binding.btnDownload.setOnClickListener {
            mainViewModel.currentTrack.value?.let { track ->
                checkPermissionAndDownload(track)
            }
        }

        binding.btnDeletePlayer.setOnClickListener {
            mainViewModel.currentTrack.value?.let { track ->
                showDeleteConfirmation(track)
            }
        }

        binding.btnLyrics.setOnClickListener {
            if (binding.lyricsContainer.visibility == View.VISIBLE) {
                binding.lyricsContainer.visibility = View.GONE
                binding.rvSmallPlaylist.visibility = View.VISIBLE
            } else {
                binding.lyricsContainer.visibility = View.VISIBLE
                binding.rvSmallPlaylist.visibility = View.GONE
                mainViewModel.currentTrack.value?.let { track ->
                    if (playerViewModel.lyrics.value == null) {
                        playerViewModel.loadLyrics(track.id)
                    }
                }
            }
        }
    }

    // Метод для проверки разрешений и запуска скачивания трека
    private fun checkPermissionAndDownload(track: Track) {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    permission
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                downloadTrack(track)
            } else {
                requestPermissionLauncher.launch(permission)
            }
        } else {
            downloadTrack(track)
        }
    }

    // Метод для шаринга информации о треке в другие приложения
    private fun shareTrack(track: Track) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            val trackName = track.name ?: "Unknown"
            val artistName = track.artistName ?: "Unknown Artist"
            putExtra(android.content.Intent.EXTRA_SUBJECT, trackName)
            val text = getString(R.string.share_text, trackName, artistName)
            putExtra(android.content.Intent.EXTRA_TEXT, "$text\n${track.audioUrl ?: ""}")
        }
        startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share_content_description)))
    }

    // Метод для постановки трека в очередь загрузки DownloadManager
    private fun downloadTrack(track: Track) {
        val url = track.audioUrl ?: return
        try {
            val trackName = track.name ?: "Unknown"
            val artistName = track.artistName ?: "Unknown Artist"
            val fileName = "$trackName - $artistName.mp3".replace(Regex("[\\\\/:*?\"<>|]"), "_")
            
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle(trackName)
                .setDescription(artistName)
                .setMimeType("audio/mpeg")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                request.allowScanningByMediaScanner()
            }

            val downloadManager = requireContext().getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadManager.enqueue(request)
            
            showToast(R.string.download_started)
        } catch (e: Exception) {
            showToast(R.string.download_failed)
        }
    }

    // Метод для отображения диалога выбора времени таймера сна
    private fun showSleepTimerDialog() {
        val options = arrayOf(
            getString(R.string.minutes_5),
            getString(R.string.minutes_15),
            getString(R.string.minutes_30),
            getString(R.string.minutes_60),
            getString(R.string.timer_off)
        )
        val minutes = intArrayOf(5, 15, 30, 60, 0)
        val currentSelected = mainViewModel.sleepTimerManager.selectedMinutes
        val checkedItem = minutes.indexOf(currentSelected).let { if (it == -1) 4 else it }

        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.TimerDialogTheme)
            .setTitle(R.string.sleep_timer_title)
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selectedMinutes = minutes[which]
                if (selectedMinutes > 0) {
                    mainViewModel.sleepTimerManager.startTimer(selectedMinutes) {
                        mainViewModel.getController()?.pause()
                    }
                    showToast(R.string.timer_set, selectedMinutes)
                } else {
                    mainViewModel.sleepTimerManager.stopTimer()
                    showToast(R.string.timer_stopped)
                }
                dialog.dismiss()
            }
            .show()
    }

    // Метод для отображения подтверждения удаления локального трека
    private fun showDeleteConfirmation(track: Track) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_track_title)
            .setMessage(getString(R.string.delete_track_message, track.name ?: "Unknown"))
            .setPositiveButton(R.string.delete_confirm) { _, _ ->
                deleteLocalTrack(track)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // Метод для удаления локального файла трека из памяти устройства
    private fun deleteLocalTrack(track: Track) {
        if (localTrackProvider.deleteTrack(track)) {
            mainViewModel.skipToNext()
            showToast(R.string.track_deleted)
        } else {
            showToast(R.string.delete_failed)
        }
    }

    // Метод для обновления элементов UI информацией о текущем треке
    private fun updateUI(track: Track) {
        binding.detailsTitle.text = track.name ?: "Unknown"
        binding.detailsArtist.text = track.artistName ?: "Unknown Artist"
        binding.detailsPoster.load(track.imageUrl) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_gallery)
        }
        
        val duration = track.duration ?: 0
        binding.seekBar.max = duration * 1000
        binding.tvTotalTime.text = TimeUtils.formatSeconds(duration)

        val isLocal = track.audioUrl?.startsWith("content://") == true || track.audioUrl?.startsWith("file://") == true
        binding.btnDeletePlayer.visibility = if (isLocal) View.VISIBLE else View.GONE
        binding.btnDownload.visibility = if (isLocal) View.GONE else View.VISIBLE
        binding.btnLyrics.visibility = if (isLocal) View.VISIBLE else View.GONE
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateSeekBarTask)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
