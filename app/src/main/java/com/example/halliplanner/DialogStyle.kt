package com.example.halliplanner

import android.app.AlertDialog
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat

object DialogStyle {
    fun apply(dialog: AlertDialog) {
        val context = dialog.context
        val primary = ContextCompat.getColor(context, R.color.planner_primary)
        val muted = ContextCompat.getColor(context, R.color.planner_secondary)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { button ->
            button.setTextColor(primary)
            button.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.planner_accent_soft)
            )
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(muted)
    }
}
