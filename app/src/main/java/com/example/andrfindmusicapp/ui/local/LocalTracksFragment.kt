package com.example.andrfindmusicapp.ui.local

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.andrfindmusicapp.databinding.FragmentLocalTracksBinding

// Класс для фрагмента, отображающего локальные треки устройства
class LocalTracksFragment : Fragment() {
    private var _binding: FragmentLocalTracksBinding? = null
    private val binding get() = _binding!!

    // Метод для создания View фрагмента и инициализации binding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocalTracksBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Метод для очистки ресурсов binding
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}