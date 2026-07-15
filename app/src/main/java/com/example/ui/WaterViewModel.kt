package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.WaterLog
import com.example.data.WaterRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class HydrationEvent {
    data class GoalReached(val target: Int) : HydrationEvent()
    data class WaterTracked(val amount: Int) : HydrationEvent()
}

class WaterViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = WaterRepository(database.waterLogDao())
    private val prefs = PreferencesManager(application)

    val allLogs: StateFlow<List<WaterLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _dailyTargetMl = MutableStateFlow(prefs.getDailyTarget())
    val dailyTargetMl: StateFlow<Int> = _dailyTargetMl.asStateFlow()

    private val _hasPromptedGoal = MutableStateFlow(prefs.getHasPromptedGoal())
    val hasPromptedGoal: StateFlow<Boolean> = _hasPromptedGoal.asStateFlow()

    private val _currentScreenIndex = MutableStateFlow(0)
    val currentScreenIndex: StateFlow<Int> = _currentScreenIndex.asStateFlow()

    private val _eventFlow = MutableSharedFlow<HydrationEvent>()
    val eventFlow: SharedFlow<HydrationEvent> = _eventFlow.asSharedFlow()

    fun addWater(amountMl: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newLog = WaterLog(timestamp = now, amountMl = amountMl)

            // Calculate total consumed today before adding
            val logs = allLogs.value
            val todayTotalBefore = logs.filter { isToday(it.timestamp) }.sumOf { it.amountMl }
            val todayTotalAfter = todayTotalBefore + amountMl
            val target = dailyTargetMl.value

            repository.insert(newLog)

            if (todayTotalAfter >= target && todayTotalBefore < target) {
                _eventFlow.emit(HydrationEvent.GoalReached(target))
            } else {
                _eventFlow.emit(HydrationEvent.WaterTracked(amountMl))
            }
        }
    }

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun resetToday() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun setDailyTarget(target: Int) {
        prefs.setDailyTarget(target)
        _dailyTargetMl.value = target
    }

    fun setHasPromptedGoal(prompted: Boolean) {
        prefs.setHasPromptedGoal(prompted)
        _hasPromptedGoal.value = prompted
    }

    fun setCurrentScreenIndex(index: Int) {
        _currentScreenIndex.value = index
    }

    fun isToday(timestamp: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val year = cal.get(Calendar.YEAR)

        val todayCal = Calendar.getInstance()
        val todayDay = todayCal.get(Calendar.DAY_OF_YEAR)
        val todayYear = todayCal.get(Calendar.YEAR)

        return day == todayDay && year == todayYear
    }
}
