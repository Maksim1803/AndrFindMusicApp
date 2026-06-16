package com.Maksim1803.andrfindmusicapp.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.Maksim1803.andrfindmusicapp.R
import com.Maksim1803.andrfindmusicapp.data.model.Track
import com.Maksim1803.andrfindmusicapp.databinding.FragmentPlayerBinding
import com.Maksim1803.andrfindmusicapp.ui.main.MainViewModel
import com.Maksim1803.andrfindmusicapp.ui.player.adapter.PlaylistAdapter
import com.Maksim1803.andrfindmusicapp.utils.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

// Класс для фрагмента плеера, отвечающего за отображение текущего трека и управление воспроизведением
@AndroidEntryPoint
class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var playlistAdapter: PlaylistAdapter
    
    // Флаг для предотвращения прыжков SeekBar при перемотке
    private var isUserSeeking = false

    // Обработчик запроса разрешений для скачивания
    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            mainViewModel.currentTrack.value?.let { track ->
                mainViewModel.downloadTrack(track)
                android.widget.Toast.makeText(requireContext(), R.string.download_started, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Метод для создания View и инициализации binding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Метод для настройки логики фрагмента после создания View
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.checkNetworkAndNotifyIfOnline()
        startSeekBarUpdate()
    }

    // Метод для настройки списка (RecyclerView) очереди воспроизведения
    private fun setupRecyclerView() {
        playlistAdapter = PlaylistAdapter(
            onItemClick = { track ->
                mainViewModel.playTrackWithPlaylist(track, mainViewModel.playlist.value)
            },
            onDeleteClick = { track ->
                mainViewModel.removeFromPlaylist(track)
            }
        )
        binding.rvSmallPlaylist.adapter = playlistAdapter
    }

    // Метод для настройки обработчиков кликов по элементам интерфейса
    private fun setupListeners() {
        with(binding) {
            btnPlayPause.setOnClickListener { mainViewModel.togglePlayPause() }
            
            btnNext.setOnClickListener { 
                if (mainViewModel.favoriteIds.value.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), R.string.playlist_add_hint, android.widget.Toast.LENGTH_SHORT).show()
                }
                mainViewModel.skipToNext() 
            }

            btnPrev.setOnClickListener { 
                if (mainViewModel.favoriteIds.value.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), R.string.playlist_add_hint, android.widget.Toast.LENGTH_SHORT).show()
                }
                mainViewModel.skipToPrevious() 
            }
            
            detailsFavorite.setOnClickListener {
                mainViewModel.currentTrack.value?.let { mainViewModel.toggleFavorite(it) }
            }

            btnReminder.setOnClickListener {
                mainViewModel.currentTrack.value?.let { track ->
                    if (mainViewModel.reminderIds.value.contains(track.id)) {
                        showRemoveReminderDialog(track)
                    } else {
                        showDateTimePicker(track)
                    }
                }
            }

            btnDownload.setOnClickListener {
                mainViewModel.currentTrack.value?.let { track ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        // На Android 10+ (API 29+) разрешение не требуется для сохранения в Music
                        if (mainViewModel.downloadTrack(track)) {
                            android.widget.Toast.makeText(requireContext(), R.string.download_started, android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(requireContext(), R.string.download_already_exists, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // На старых версиях проверяем разрешение
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                requireContext(),
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            if (mainViewModel.downloadTrack(track)) {
                                android.widget.Toast.makeText(requireContext(), R.string.download_started, android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(requireContext(), R.string.download_already_exists, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                }
            }

            btnShare.setOnClickListener {
                mainViewModel.currentTrack.value?.let { track ->
                    shareTrack(track)
                }
            }

            btnSleepTimer.setOnClickListener {
                showSleepTimerDialog()
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        mainViewModel.seekTo(progress.toLong())
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { isUserSeeking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { isUserSeeking = false }
            })
        }
    }

    // Метод для подписки на изменения данных в ViewModel
    private fun observeViewModel() {
        // Объединяем наблюдение за текущим треком, избранным и напоминаниями
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                mainViewModel.currentTrack,
                mainViewModel.favoriteIds,
                mainViewModel.reminderIds
            ) { track, favoriteIds, reminderIds ->
                Triple(track, favoriteIds, reminderIds)
            }.collectLatest { (track, favoriteIds, reminderIds) ->
                track?.let { 
                    setupUI(it)
                    playlistAdapter.setCurrentTrack(it.id)
                    
                    val isFavorite = favoriteIds.contains(it.id)
                    binding.detailsFavorite.setImageResource(
                        if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
                    )

                    val hasReminder = reminderIds.contains(it.id)
                    binding.btnReminder.setImageResource(
                        if (hasReminder) R.drawable.ic_notifications_active else R.drawable.ic_notifications_none
                    )
                    // Всегда синий цвет иконки (как у звездочки)
                    binding.btnReminder.imageTintList = android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark)
                    )
                    // Всегда светлый (белый) фон для кнопки
                    binding.btnReminder.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.WHITE
                    )
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.playlist.collectLatest { tracks ->
                playlistAdapter.updateData(tracks)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.isPlaying.collectLatest { isPlaying ->
                binding.btnPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            }
        }

        // Наблюдение за состоянием таймера сна
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.sleepTimerManager.isTimerRunning.collectLatest { isRunning ->
                binding.btnSleepTimer.setImageResource(
                    if (isRunning) R.drawable.ic_alarm_on else R.drawable.ic_alarm_off
                )
                binding.btnSleepTimer.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isRunning) R.color.colorPrimaryDark else R.color.player_icon_tint
                    )
                )
            }
        }

        // Наблюдение за буферизацией (слабый интернет)
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.isBuffering.collectLatest { isBuffering ->
                binding.playerProgressBar.visibility = if (isBuffering) View.VISIBLE else View.GONE
            }
        }
    }

    // Метод для обновления UI данными трека
    private fun setupUI(track: Track) {
        binding.detailsTitle.text = track.name
        binding.detailsArtist.text = track.artistName
        binding.detailsPoster.load(track.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_background)
        }
        
        // Показываем индикатор только для локальных треков
        val isLocal = track.audioUrl?.startsWith("content://") == true || track.audioUrl?.startsWith("file://") == true
        binding.btnLyrics.visibility = if (isLocal) View.VISIBLE else View.GONE
        
        // Обновляем максимальное значение SeekBar при смене трека
        binding.seekBar.max = (track.duration ?: 0) * 1000 // Jamendo дает в секундах
    }

    // Метод для того, чтобы поделиться информацией о треке
    private fun shareTrack(track: Track) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            val text = getString(R.string.share_text, track.name, track.artistName)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share_content_description)))
    }

    // Метод для показа выбора даты и времени напоминания
    private fun showDateTimePicker(track: Track) {
        val calendar = Calendar.getInstance()
        val datePicker = android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                android.app.TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)

                        if (calendar.timeInMillis <= System.currentTimeMillis()) {
                            android.widget.Toast.makeText(requireContext(), "Выберите время в будущем", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            mainViewModel.setReminder(track, calendar.timeInMillis)
                            val dateStr = android.text.format.DateFormat.getDateFormat(requireContext()).format(calendar.time)
                            val timeStr = android.text.format.DateFormat.getTimeFormat(requireContext()).format(calendar.time)
                            android.widget.Toast.makeText(requireContext(), getString(R.string.reminder_set, "$dateStr $timeStr"), android.widget.Toast.LENGTH_SHORT).show()
                            
                            // Schedule the notification as a reminder
                            binding.root.postDelayed({
                                // Проверяем, не удалили ли напоминание к этому моменту
                                if (mainViewModel.reminderIds.value.contains(track.id)) {
                                    com.Maksim1803.andrfindmusicapp.utils.NotificationHelper.showRecommendationNotification(requireContext(), track, isReminder = true)
                                }
                            }, calendar.timeInMillis - System.currentTimeMillis())
                        }
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    // Метод для показа диалога удаления напоминания
    private fun showRemoveReminderDialog(track: Track) {
        val timeMillis = mainViewModel.reminderTimes.value[track.id] ?: return
        val calendar = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
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

    // Метод для показа диалога настройки таймера сна
    private fun showSleepTimerDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sleep_timer, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        val bgColor = if (isDarkTheme) R.color.dark_gradient_bottom else R.color.timer_dialog_bg
        val textColor = if (isDarkTheme) R.color.timer_dialog_bg else R.color.timer_dialog_text
        val btnColor = if (isDarkTheme) R.color.colorAccent else R.color.player_icon_tint

        // Применяем цвета к фону диалога
        val background = dialogView.background as android.graphics.drawable.GradientDrawable
        background.setColor(ContextCompat.getColor(requireContext(), bgColor))

        // Применяем цвета к тексту
        val title = dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_title)
        title.setTextColor(ContextCompat.getColor(requireContext(), textColor))

        val rgOptions = dialogView.findViewById<android.widget.RadioGroup>(R.id.rg_timer_options)
        
        // Пре-селект текущего значения таймера
        val currentMinutes = mainViewModel.sleepTimerManager.lastSetMinutes
        if (currentMinutes > 0) {
            val radioButtonId = when (currentMinutes) {
                10 -> R.id.rb_10
                15 -> R.id.rb_15
                20 -> R.id.rb_20
                25 -> R.id.rb_25
                30 -> R.id.rb_30
                else -> -1
            }
            if (radioButtonId != -1) {
                rgOptions.check(radioButtonId)
            }
        } else {
            rgOptions.check(R.id.rb_off)
        }

        for (i in 0 until rgOptions.childCount) {
            val rb = rgOptions.getChildAt(i) as android.widget.RadioButton
            rb.setTextColor(ContextCompat.getColor(requireContext(), textColor))
            rb.buttonTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), btnColor))
        }

        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btn_cancel)
        btnCancel.setTextColor(ContextCompat.getColor(requireContext(), btnColor))

        rgOptions.setOnCheckedChangeListener { _, checkedId ->
            val minutes = when (checkedId) {
                R.id.rb_10 -> 10
                R.id.rb_15 -> 15
                R.id.rb_20 -> 20
                R.id.rb_25 -> 25
                R.id.rb_30 -> 30
                R.id.rb_off -> -1
                else -> 0
            }

            if (minutes == -1) {
                mainViewModel.stopSleepTimer()
                android.widget.Toast.makeText(requireContext(), R.string.timer_stopped, android.widget.Toast.LENGTH_SHORT).show()
            } else if (minutes > 0) {
                mainViewModel.startSleepTimer(minutes)
                android.widget.Toast.makeText(requireContext(), getString(R.string.timer_set, minutes), android.widget.Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // Метод для циклического обновления SeekBar в зависимости от прогресса воспроизведения
    private fun startSeekBarUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                if (!isUserSeeking) {
                    val controller = mainViewModel.getController()
                    controller?.let {
                        binding.seekBar.progress = it.currentPosition.toInt()
                        binding.tvCurrentTime.text = TimeUtils.formatMillis(it.currentPosition)
                        
                        if (it.duration > 0) {
                            binding.seekBar.max = it.duration.toInt()
                            binding.tvTotalTime.text = TimeUtils.formatMillis(it.duration)
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
