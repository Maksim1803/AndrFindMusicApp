package com.example.andrfindmusicapp.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.andrfindmusicapp.databinding.FragmentSearchBinding

// Класс для фрагмента поиска музыки (функционал поиска реализован в HomeFragment)
class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    // Метод для создания View фрагмента и инициализации binding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Метод для очистки ресурсов binding
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}