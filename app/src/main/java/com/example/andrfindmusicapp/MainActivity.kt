package com.example.andrfindmusicapp

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.andrfindmusicapp.databinding.ActivityMainBinding
import com.example.andrfindmusicapp.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

// Главный класс приложения
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        if (savedInstanceState == null) {
            binding.root.post {
                // 1. Напоминание
                if (intent.getBooleanExtra("is_reminder", false)) {
                    val track =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getSerializableExtra(
                                "recommended_track",
                                com.example.andrfindmusicapp.data.model.Track::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getSerializableExtra("recommended_track") as? com.example.andrfindmusicapp.data.model.Track
                        }
                    if (track != null) {
                        mainViewModel.playTrackWithPlaylist(track, emptyList())
                        navController.navigate(R.id.navigation_player)
                        return@post
                    }
                }

            }
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId != navController.currentDestination?.id) {
                navController.navigate(item.itemId)
                true
            } else {
                false
            }
        }
    }
}
