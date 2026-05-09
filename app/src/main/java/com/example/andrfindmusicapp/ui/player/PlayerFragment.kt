package com.example.andrfindmusicapp.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentPlayerBinding
import com.example.andrfindmusicapp.ui.main.MainViewModel
import com.example.andrfindmusicapp.ui.player.adapter.PlaylistAdapter
import com.example.andrfindmusicapp.utils.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Класс для фрагмента плеера, отвечающего за отображение текущего трека и управление воспроизведением
@AndroidEntryPoint
class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var playlistAdapter: PlaylistAdapter
    
    // Флаг для предотвращения прыжков SeekBar при перемотке
    private var isUserSeeking = false

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
        setupListeners()
        observeViewModel()
        startSeekBarUpdate()
    }

    private fun setupRecyclerView() {
        playlistAdapter = PlaylistAdapter { track ->
            mainViewModel.playTrackWithPlaylist(track, mainViewModel.playlist.value)
        }
        binding.rvSmallPlaylist.adapter = playlistAdapter
    }

    private fun setupListeners() {
        with(binding) {
            btnPlayPause.setOnClickListener { mainViewModel.togglePlayPause() }
            
            btnNext.setOnClickListener { 
                android.widget.Toast.makeText(requireContext(), R.string.playlist_hint, android.widget.Toast.LENGTH_SHORT).show()
                mainViewModel.skipToNext() 
            }

            btnPrev.setOnClickListener { 
                android.widget.Toast.makeText(requireContext(), R.string.playlist_hint, android.widget.Toast.LENGTH_SHORT).show()
                mainViewModel.skipToPrevious() 
            }
            
            detailsFavorite.setOnClickListener {
                mainViewModel.currentTrack.value?.let { mainViewModel.toggleFavorite(it) }
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

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.currentTrack.collectLatest { track ->
                track?.let { 
                    setupUI(it)
                    playlistAdapter.setCurrentTrack(it.id)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.playlist.collectLatest { tracks ->
                playlistAdapter.updateData(tracks)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.favoriteIds.collectLatest { favoriteIds ->
                val isFavorite = favoriteIds.contains(mainViewModel.currentTrack.value?.id)
                binding.detailsFavorite.setImageResource(
                    if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
                )
            }
        }
    }

    private fun setupUI(track: Track) {
        binding.detailsTitle.text = track.name
        binding.detailsArtist.text = track.artistName
        binding.detailsPoster.load(track.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_background)
        }
        
        // Обновляем максимальное значение SeekBar при смене трека
        binding.seekBar.max = (track.duration ?: 0) * 1000 // Jamendo дает в секундах
    }

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
