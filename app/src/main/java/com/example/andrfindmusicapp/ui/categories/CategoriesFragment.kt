package com.example.andrfindmusicapp.ui.categories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.databinding.FragmentCategoriesBinding
import com.example.andrfindmusicapp.ui.categories.adapter.CategoryAdapter
import com.example.andrfindmusicapp.utils.PreferenceProvider

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CategoriesFragment : Fragment() {
    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var preferenceProvider: PreferenceProvider

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categories = listOf(
            Category(1, getString(R.string.category_metal), "metal"),
            Category(2, getString(R.string.category_pop), "pop"),
            Category(3, getString(R.string.category_electronic), "electronic"),
            Category(4, getString(R.string.category_classical), "classical")
        )

        binding.categoriesRecycler.layoutManager = LinearLayoutManager(requireContext())
        val currentCategoryTag = preferenceProvider.getLastCategory()
        
        binding.categoriesRecycler.adapter = CategoryAdapter(categories, currentCategoryTag) { category ->
            // 1. Сохраняем выбор
            preferenceProvider.saveCategory(category.tag)
            // 2. Переходим на главный экран
            findNavController().navigate(R.id.navigation_home)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}