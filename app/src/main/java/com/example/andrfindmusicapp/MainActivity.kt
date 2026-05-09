package com.example.andrfindmusicapp

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.andrfindmusicapp.databinding.ActivityMainBinding
import com.example.andrfindmusicapp.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

// Класс для главной активности приложения, управляющей навигацией и общей темой
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels()

    // Метод для инициализации активности и настройки BottomNavigationView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // Принудительная обработка для исправления зависания навигации
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
/*
(client_id): 59cb9dad
 */