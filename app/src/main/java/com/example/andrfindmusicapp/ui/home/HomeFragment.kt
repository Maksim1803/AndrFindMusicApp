package com.example.andrfindmusicapp.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.data.local.AppDatabase
import com.example.andrfindmusicapp.data.local.TrackEntity
import com.example.andrfindmusicapp.data.model.Track
import com.example.andrfindmusicapp.databinding.FragmentHomeBinding
import com.example.andrfindmusicapp.ui.home.adapter.HomeAdapter
import com.example.andrfindmusicapp.ui.player.PlayerViewModel
import com.example.andrfindmusicapp.utils.PreferenceProvider
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
    private val playerViewModel: PlayerViewModel by activityViewModels()
    private lateinit var adapter: HomeAdapter

    @javax.inject.Inject
    lateinit var trackDao: com.example.andrfindmusicapp.data.local.TrackDao

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
        refreshFavorites()

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
        if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    // Метод для переключения языка интерфейса (RU/EN)
    private fun toggleLanguage() {
        val currentLang = resources.configuration.locales[0].language
        val newLang = if (currentLang == "ru") "en" else "ru"
        
        val locale = java.util.Locale(newLang)
        java.util.Locale.setDefault(locale)
        
        val resources = requireContext().resources
        val config = resources.configuration
        config.setLocale(locale)
        
        // Для Android 13+ (API 33) и выше используем специальный API
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireActivity().getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales = android.os.LocaleList(locale)
        } else {
            // Для старых версий
            context?.createConfigurationContext(config)
            resources.updateConfiguration(config, resources.displayMetrics)
        }
        
        activity?.recreate()
    }

    // Метод для настройки RecyclerView и его адаптера
    private fun setupRecyclerView() {
        adapter = HomeAdapter(
            onItemClick = { track ->
                playerViewModel.selectTrack(track)
                val bundle = Bundle().apply { putSerializable("track", track) }
                findNavController().navigate(R.id.navigation_player, bundle)
            },
            onFavoriteClick = { track ->
                toggleFavorite(track)
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

    // Метод для добавления или удаления трека из списка избранного
    private fun toggleFavorite(track: Track) {
        val database = AppDatabase.getDatabase(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val dao = database.trackDao()
            val isCurrentlyFav = dao.isFavorite(track.id)
            val newFavStatus = !isCurrentlyFav

            // Пробуем обновить статус, если трек уже есть в базе
            dao.updateFavoriteStatus(track.id, newFavStatus)
            
            // Если трека не было (он пришел из поиска и мы его еще не кэшировали) - вставляем
            if (!dao.isFavorite(track.id) && !isCurrentlyFav) {
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
                dao.insertTrack(entity)
            }
        }
    }

    // Метод для обновления статуса "избранное" в адаптере
    private fun refreshFavorites() {
        val database = AppDatabase.getDatabase(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            database.trackDao().getAllFavorites().collectLatest { favorites ->
                val favoriteIds = favorites.map { it.id }.toSet()
                adapter.updateFavorites(favoriteIds)
            }
        }
    }

    // Метод для настройки поисковой строки
    private fun setupSearchView() {
        binding.searchViewHome.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.searchTracks(query ?: "")
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) viewModel.loadLastCategoryTracks()
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
