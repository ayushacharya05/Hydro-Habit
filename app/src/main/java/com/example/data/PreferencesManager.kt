package com.example.data

import android.content.Context

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("hydrohabit_prefs", Context.MODE_PRIVATE)

    fun getDailyTarget(): Int {
        return prefs.getInt("daily_target_ml", 2000)
    }

    fun setDailyTarget(target: Int) {
        prefs.edit().putInt("daily_target_ml", target).apply()
    }

    fun getHasPromptedGoal(): Boolean {
        return prefs.getBoolean("has_prompted_goal", false)
    }

    fun setHasPromptedGoal(prompted: Boolean) {
        prefs.edit().putBoolean("has_prompted_goal", prompted).apply()
    }
}
