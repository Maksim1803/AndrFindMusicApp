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
import com.example.andrfindmusicapp.data.local.TrackEntity
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentPlayerBinding
import com.example.andrfindmusicapp.ui.main.MainViewModel
import com.example.andrfindmusicapp.ui.player.adapter.SmallPlaylistAdapter
import com.example.andrfindmusicapp.utils.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val playerViewModel: PlayerViewModel by activityViewModels()
    
    private lateinit var smallPlaylistAdapter: SmallPlaylistAdapter
    
    @javax.inject.Inject
    lateinit var trackDao: com.example.andrfindmusicapp.data.local.TrackDao
    
    private val handler = Handler(Looper.getMainLooper())
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

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Слушаем текущий трек
                launch {
                    mainViewModel.currentTrack.collectLatest { track ->
                        track?.let {
                            updateUI(it)
                            smallPlaylistAdapter.setCurrentTrack(it.id)
                            checkIsFavorite(it.id)
                        }
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

    private fun setupRecyclerView() {
        smallPlaylistAdapter = SmallPlaylistAdapter { clickedTrack ->
            mainViewModel.playTrack(clickedTrack)
        }
        binding.rvSmallPlaylist.adapter = smallPlaylistAdapter
    }

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
                toggleFavorite(track)
            }
        }

        binding.btnSleepTimer.setOnClickListener {
            showSleepTimerDialog()
        }
    }

    private fun showSleepTimerDialog() {
        val options = arrayOf(
            getString(R.string.minutes_5),
            getString(R.string.minutes_15),
            getString(R.string.minutes_30),
            getString(R.string.minutes_60),
            getString(R.string.timer_off)
        )
        val minutes = intArrayOf(5, 15, 30, 60, 0)

        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.TimerDialogTheme)
            .setTitle(R.string.sleep_timer_title)
            .setItems(options) { _, which ->
                val selectedMinutes = minutes[which]
                if (selectedMinutes > 0) {
                    mainViewModel.sleepTimerManager.startTimer(selectedMinutes) {
                        mainViewModel.getController()?.pause()
                    }
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.timer_set, selectedMinutes),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    mainViewModel.sleepTimerManager.stopTimer()
                    Toast.makeText(
                        requireContext(),
                        R.string.timer_stopped,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun updateUI(track: Track) {
        binding.detailsTitle.text = track.name
        binding.detailsArtist.text = track.artistName
        binding.detailsPoster.load(track.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_background)
        }
        
        mainViewModel.getController()?.let { p ->
            val duration = p.duration
            if (duration > 0) {
                binding.seekBar.max = duration.toInt()
                binding.tvTotalTime.text = TimeUtils.formatTime(duration)
            } else {
                // Если плеер еще не знает длительность (например, только начал загрузку),
                // берем данные из модели трека (Jamendo отдает в секундах)
                val totalMs = track.duration.toLong() * 1000
                binding.seekBar.max = totalMs.toInt()
                binding.tvTotalTime.text = TimeUtils.formatTime(totalMs)
            }
        }
    }

    private fun checkIsFavorite(trackId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val isFav = trackDao.isFavorite(trackId)
            _binding?.let {
                it.detailsFavorite.setImageResource(
                    if (isFav) R.drawable.ic_star else R.drawable.ic_star_border
                )
            }
        }
    }

    private fun toggleFavorite(track: Track) {
        viewLifecycleOwner.lifecycleScope.launch {
            val isCurrentlyFav = trackDao.isFavorite(track.id)
            val newFavStatus = !isCurrentlyFav
            
            trackDao.updateFavoriteStatus(track.id, newFavStatus)
            
            // Если трека не было в базе, вставляем его
            if (!trackDao.isFavorite(track.id) && !isCurrentlyFav) {
                val entity = TrackEntity(
                    id = track.id,
                    name = track.name,
                    duration = track.duration,
                    artistName = track.artistName,
                    albumName = track.albumName,
                    imageUrl = track.imageUrl,
                    audioUrl = track.audioUrl,
                    isFavorite = true,
                    category = "" 
                )
                trackDao.insertTrack(entity)
            }

            checkIsFavorite(track.id)
        }
    }

    override fun onStop() {
        super.onStop()
        // Теперь мы НЕ останавливаем плеер здесь!
        handler.removeCallbacks(updateSeekBarTask)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
