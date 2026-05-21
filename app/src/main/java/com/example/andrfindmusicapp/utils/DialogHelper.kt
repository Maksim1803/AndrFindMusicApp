package com.example.andrfindmusicapp.utils

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.andrfindmusicapp.R

// Класс-помощник для создания и отображения кастомных диалоговых окон
object DialogHelper {
    
    // Метод для показа диалога подтверждения-удаления напоминания
    fun showReminderDeleteDialog(
        context: Context,
        dateStr: String,
        timeStr: String,
        onConfirm: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_reminder_confirm, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val isDarkTheme = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        val bgColor = if (isDarkTheme) R.color.dark_gradient_bottom else R.color.timer_dialog_bg
        val textColor = if (isDarkTheme) R.color.timer_dialog_bg else R.color.timer_dialog_text
        val btnColor = if (isDarkTheme) R.color.colorAccent else R.color.player_icon_tint

        // Применяем цвета к фону диалога
        val background = dialogView.background as GradientDrawable
        background.setColor(ContextCompat.getColor(context, bgColor))

        // Настройка заголовка диалога
        val title = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        title.setTextColor(ContextCompat.getColor(context, textColor))

        // Настройка сообщения диалога
        val message = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        message.setTextColor(ContextCompat.getColor(context, textColor))
        message.text = context.getString(R.string.reminder_delete_confirm, "$dateStr $timeStr")

        // Настройка кнопки "Отмена"
        val btnNo = dialogView.findViewById<Button>(R.id.btn_no)
        btnNo.setTextColor(ContextCompat.getColor(context, btnColor))
        btnNo.setOnClickListener { dialog.dismiss() }

        // Настройка кнопки "Удалить"
        val btnYes = dialogView.findViewById<Button>(R.id.btn_yes)
        btnYes.setTextColor(ContextCompat.getColor(context, btnColor))
        btnYes.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }
}
