package com.example.andrfindmusicapp.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentHomeBinding
import com.example.andrfindmusicapp.ui.home.adapter.HomeAdapter
import com.example.andrfindmusicapp.ui.main.MainViewModel
import com.example.andrfindmusicapp.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import dagger.hilt.android.AndroidEntryPoint

// Класс для главного экрана приложения, отображающего список треков и поиск
@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: HomeAdapter

    @javax.inject.Inject
    lateinit var trackDao: com.example.andrfindmusicapp.data.local.TrackDao

    @javax.inject.Inject
    lateinit var preferenceProvider: com.example.andrfindmusicapp.utils.PreferenceProvider

    // Метод для создания View и инициализации binding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Метод для настройки логики фрагмента и наблюдателей за данными
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchView()
        observeViewModel()

        binding.settingsMenu.setOnClickListener { view ->
            showSettingsMenu(view)
        }

        binding.pullToRefresh.setOnRefreshListener {
            viewModel.loadLastCategoryTracks()
        }
    }

    // Метод для показа выпадающего меню настроек (язык, тема)
    private fun showSettingsMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.settings_options_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_theme -> {
                    toggleTheme()
                    true
                }
                R.id.action_language -> {
                    toggleLanguage()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // Метод для переключения темы приложения (светлая/темная)
    private fun toggleTheme() {
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val newMode = if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        } else {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        }
        
        preferenceProvider.saveIsDarkMode(!isDarkMode)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(newMode)
    }

    // Метод для переключения языка интерфейса (RU/EN)
    private fun toggleLanguage() {
        // Получаем текущий язык через AppCompat (он вернет системный, если выбор еще не сделан)
        val currentLocale = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()[0]
        val currentLang = currentLocale?.language ?: java.util.Locale.getDefault().language
        
        val newLang = if (currentLang == "ru") "en" else "ru"
        
        // Используем современный способ переключения (работает на всех версиях Android)
        val appLocale: androidx.core.os.LocaleListCompat = androidx.core.os.LocaleListCompat.forLanguageTags(newLang)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
    }

    // Метод для показа диалога подтверждения удаления напоминания
    private fun showReminderDialog(track: Track) {
        val timeMillis = mainViewModel.reminderTimes.value[track.id] ?: return
        val calendar = java.util.Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val dateStr = android.text.format.DateFormat.getDateFormat(requireContext()).format(calendar.time)
        val timeStr = android.text.format.DateFormat.getTimeFormat(requireContext()).format(calendar.time)

        com.example.andrfindmusicapp.utils.DialogHelper.showReminderDeleteDialog(
            requireContext(),
            dateStr,
            timeStr
        ) {
            mainViewModel.removeReminder(track.id)
            android.widget.Toast.makeText(requireContext(), R.string.reminder_removed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Метод для настройки RecyclerView и его адаптера
    private fun setupRecyclerView() {
        adapter = HomeAdapter(
            onItemClick = { track ->
                mainViewModel.playTrackWithPlaylist(track, viewModel.tracks.value ?: emptyList())
                findNavController().navigate(R.id.navigation_player)
            },
            onFavoriteClick = { track ->
                mainViewModel.toggleFavorite(track)
            },
            onReminderClick = { track ->
                showReminderDialog(track)
            }
        )
        binding.mainRecycler.adapter = adapter
        
        // Добавляем слушатель для пагинации
        binding.mainRecycler.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) { // Проверяем скролл вниз
                    val layoutManager = recyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItemCount = layoutManager.findFirstVisibleItemPosition()
                    
                    viewModel.doPagination(visibleItemCount, totalItemCount, pastVisibleItemCount)
                }
            }
        })
    }

    // Метод для настройки поисковой строки
    private fun setupSearchView() {
        binding.searchViewHome.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // Отрабатывает при нажатии кнопки "поиск" на клавиатуре
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.searchTracks(query ?: "")
                return true
            }
            // Отрабатывает на каждое изменение текста (реактивный поиск)
            override fun onQueryTextChange(newText: String?): Boolean {
                // Если ввод пуст, возвращаем стандартный список (как в FindAFilm)
                if (newText.isNullOrBlank()) {
                    viewModel.loadLastCategoryTracks()
                } else {
                    // Иначе запускаем поиск (в ViewModel добавлена задержка и отмена старых запросов)
                    viewModel.searchTracks(newText)
                }
                return true
            }
        })
    }

    // Метод для подписки на изменения в ViewModel
    private fun observeViewModel() {
        viewModel.tracks.observe(viewLifecycleOwner) { tracks ->
            adapter.updateData(tracks)
            binding.pullToRefresh.isRefreshing = false
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.favoriteIds.collectLatest { favIds ->
                adapter.updateFavorites(favIds)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.reminderIds.collectLatest { remIds ->
                adapter.updateReminders(remIds)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLastCategoryTracks()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
