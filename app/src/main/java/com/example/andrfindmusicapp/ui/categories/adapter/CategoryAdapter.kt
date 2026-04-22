package com.example.andrfindmusicapp.ui.categories.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.andrfindmusicapp.R
import com.example.andrfindmusicapp.ui.categories.Category

class CategoryAdapter(
    private val categories: List<Category>,
    private val selectedCategoryName: String?,
    private val onItemClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.category_name)
        val selectedIcon: ImageView = view.findViewById(R.id.iv_selected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.name.text = category.name
        
        // Показываем закрашенную точку, если категория выбрана
        val isSelected = category.tag == selectedCategoryName
        holder.selectedIcon.setImageResource(
            if (isSelected) R.drawable.ic_radio_on else R.drawable.ic_radio_off
        )
        
        holder.itemView.setOnClickListener { onItemClick(category) }
    }

    override fun getItemCount() = categories.size
}
