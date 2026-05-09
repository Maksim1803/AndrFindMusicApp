package com.example.andrfindmusicapp.ui.recommendation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.model.DailyTrackProvider
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentRecommendationBinding
import com.example.andrfindmusicapp.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecommendationFragment : Fragment() {
    private var _binding: FragmentRecommendationBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecommendationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val track = DailyTrackProvider.currentDailyTrack ?: DailyTrackProvider.getBaseTrack()

        binding.tvRecTitle.text = track.name
        binding.tvRecArtist.text = track.artistName

        binding.ivRecommendationPoster.load(track.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_launcher_background)
            error(android.R.drawable.ic_menu_gallery)
        }

        startAnimations()

        binding.btnListenNow.setOnClickListener {
            mainViewModel.playTrackWithPlaylist(track, emptyList())
            findNavController().navigate(R.id.navigation_player)
        }

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun startAnimations() {
        val anim = ScaleAnimation(
            0.9f, 1.1f, 0.9f, 1.1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 2000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        binding.viewGlow.startAnimation(anim)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
