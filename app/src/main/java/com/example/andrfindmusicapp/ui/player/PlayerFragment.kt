package com.example.andrfindmusicapp.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.local.TrackEntity
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentPlayerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// Класс для фрагмента плеера, отвечающего за отображение текущего трека и управление воспроизведением
@AndroidEntryPoint
class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var currentTrack: Track? = null
    private val playerViewModel: PlayerViewModel by activityViewModels()
    
    @javax.inject.Inject
    lateinit var trackDao: com.example.andrfindmusicapp.data.local.TrackDao
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            // Проверяем, существует ли еще binding, чтобы избежать вылета
            _binding?.let { b ->
                player?.let { p ->
                    b.seekBar.progress = p.currentPosition.toInt()
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    // Метод для создания View фрагмента и инициализации binding
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

        // Сначала пытаемся получить трек из аргументов (если пришли со списка)
        val trackFromArgs = arguments?.getSerializable("track") as? Track
        
        if (trackFromArgs != null) {
            currentTrack = trackFromArgs
            setupUI(currentTrack!!)
            checkIsFavorite(currentTrack!!.id)
            setupPlayer(currentTrack!!.audioUrl)
        } else {
            // Если пришли через нижнее меню, берем последний трек из ViewModel
            playerViewModel.selectedTrack.observe(viewLifecycleOwner) { track ->
                track?.let {
                    currentTrack = it
                    setupUI(it)
                    checkIsFavorite(it.id)
                    // Важно: здесь мы НЕ вызываем setupPlayer автоматически, 
                    // чтобы музыка не начинала играть сама при возврате на экран
                }
            }
        }
    }

    // Метод для проверки, добавлен ли трек в базу данных избранного
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

    // Метод для добавления или удаления трека из списка избранного
    private fun toggleFavorite(track: Track) {
        viewLifecycleOwner.lifecycleScope.launch {
            val isCurrentlyFav = trackDao.isFavorite(track.id)
            val newFavStatus = !isCurrentlyFav
            
            // Проверяем, есть ли трек вообще в базе (любой: в кэше или избранном)
            // Мы можем использовать тот же isFavorite или отдельный метод. 
            // Но проще всего попробовать обновить флаг, и если ничего не обновилось - вставить.
            
            val updated = trackDao.updateFavoriteStatus(track.id, newFavStatus)
            
            // Если трек новый (например, из поиска) и его не было в базе - вставляем
            // Так как updateFavoriteStatus возвращает Unit, проверим существование вручную
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
            } else {
                trackDao.updateFavoriteStatus(track.id, newFavStatus)
            }

            // Обновляем иконку сразу после изменения
            _binding?.let {
                it.detailsFavorite.setImageResource(
                    if (newFavStatus) R.drawable.ic_star else R.drawable.ic_star_border
                )
            }
        }
    }

    // Метод для настройки элементов интерфейса (текст, обложка, кнопки)
    private fun setupUI(track: Track) {
        binding.detailsTitle.text = track.name
        binding.detailsArtist.text = track.artistName
        binding.detailsPoster.load(track.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_background)
        }

        binding.detailsFavorite.setOnClickListener {
            toggleFavorite(track)
        }

        binding.btnPlayPause.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                player?.play()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // Метод для инициализации ExoPlayer и запуска потокового аудио
    private fun setupPlayer(url: String?) {
        if (url == null) return

        player = ExoPlayer.Builder(requireContext()).build().apply {
            val mediaItem = MediaItem.fromUri(url)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }

        player?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _binding?.let { b ->
                    if (state == androidx.media3.common.Player.STATE_READY) {
                        b.seekBar.max = player?.duration?.toInt() ?: 0
                        handler.post(updateSeekBarTask)
                        b.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    }
                }
            }
        })
    }

    // Метод для остановки воспроизведения и освобождения ресурсов плеера
    override fun onStop() {
        super.onStop()
        // Останавливаем музыку при уходе с экрана, чтобы избежать ошибок навигации
        player?.stop()
        player?.release()
        player = null
        handler.removeCallbacks(updateSeekBarTask)
    }

    // Метод для очистки ресурсов binding
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
