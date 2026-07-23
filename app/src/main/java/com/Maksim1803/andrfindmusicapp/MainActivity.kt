package com.Maksim1803.andrfindmusicapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.Maksim1803.andrfindmusicapp.databinding.ActivityMainBinding
import com.Maksim1803.andrfindmusicapp.ui.main.MainViewModel
import com.Maksim1803.andrfindmusicapp.utils.PreferenceProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Главный класс приложения
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var preferenceProvider: PreferenceProvider

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            preferenceProvider.saveLastTrackCount(mainViewModel.getLocalTracksCount())
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navHostFragment.navController.navigate(R.id.navigation_local)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Обработка системных отступов (Edge-to-Edge) для API 35+
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Сверху отодвигаем весь контент (статус-бар)
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            
            // Снизу отодвигаем ТОЛЬКО иконки внутри нижнего меню (навигационная панель)
            binding.bottomNav.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        if (savedInstanceState == null) {
            if (preferenceProvider.isFirstLaunch()) {
                showFirstLaunchDialog()
            } else {
                checkForNewTracks()
            }
            
            binding.root.post {
                // 1. Напоминание
                if (intent.getBooleanExtra("is_reminder", false)) {
                    val track =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getSerializableExtra(
                                "recommended_track",
                                com.Maksim1803.andrfindmusicapp.data.model.Track::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getSerializableExtra("recommended_track") as? com.Maksim1803.andrfindmusicapp.data.model.Track
                        }
                    if (track != null) {
                        mainViewModel.playTrackWithPlaylist(track, emptyList())
                        navController.navigate(R.id.navigation_player)
                        return@post
                    }
                }

                // 2. Обработка внешнего файла (Open With)
                if (intent.action == android.content.Intent.ACTION_VIEW) {
                    intent.data?.let { uri ->
                        handleExternalAudioUri(uri)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.errorEvents.collect { errorResId ->
                    android.widget.Toast.makeText(this@MainActivity, errorResId, android.widget.Toast.LENGTH_SHORT).show()
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

    private fun showFirstLaunchDialog() {
        // Проверяем наличие треков ПЕРЕД показом диалога
        val tracksCount = mainViewModel.getLocalTracksCount()
        if (tracksCount == 0) {
            preferenceProvider.setFirstLaunchCompleted()
            saveCurrentTrackCount()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.first_launch_scan_title)
            .setMessage(R.string.first_launch_scan_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                preferenceProvider.setFirstLaunchCompleted()
                checkAndRequestMusicPermission()
            }
            .setNegativeButton(R.string.no) { _, _ ->
                preferenceProvider.setFirstLaunchCompleted()
                // Даже если отказались, сохраним текущее количество, чтобы не спрашивать про старые треки как про новые
                saveCurrentTrackCount()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkForNewTracks() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            val lastCount = preferenceProvider.getLastTrackCount()
            val currentCount = mainViewModel.getLocalTracksCount()
            
            if (currentCount > lastCount) {
                showNewTracksDialog(currentCount)
            }
        }
    }

    private fun showNewTracksDialog(newCount: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.first_launch_scan_title)
            .setMessage(R.string.new_tracks_scan_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                preferenceProvider.saveLastTrackCount(newCount)
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                navHostFragment.navController.navigate(R.id.navigation_local)
            }
            .setNegativeButton(R.string.no) { _, _ ->
                preferenceProvider.saveLastTrackCount(newCount)
            }
            .show()
    }

    private fun saveCurrentTrackCount() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            preferenceProvider.saveLastTrackCount(mainViewModel.getLocalTracksCount())
        }
    }

    private fun handleExternalAudioUri(uri: android.net.Uri) {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Пытаемся получить имя файла для отображения
        var fileName = getString(R.string.local_file_desc)
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }

        val externalTrack = com.Maksim1803.andrfindmusicapp.data.model.Track(
            id = uri.toString(),
            name = fileName,
            artistName = getString(R.string.app_name),
            albumName = getString(R.string.local_file_desc),
            audioUrl = uri.toString(),
            imageUrl = "",
            duration = 0
        )

        mainViewModel.playTrackWithPlaylist(externalTrack, listOf(externalTrack))
        navController.navigate(R.id.navigation_player)
    }

    private fun checkAndRequestMusicPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            preferenceProvider.saveLastTrackCount(mainViewModel.getLocalTracksCount())
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navHostFragment.navController.navigate(R.id.navigation_local)
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }
}
