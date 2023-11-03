package com.kh.lecturelink

import android.location.Location
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CurrentEvent(
    val checkedIn: Boolean,
    val event: CalEvent
)

data class UiState(
    val currentEvents: List<CurrentEvent>,
    val futureEvents: List<CalEvent>
)
class MainViewModel(private val calendarManager: CalendarManager, locationManager: LocationManaging): ViewModel() {
    var location = locationManager.locationStateFlow

    private var _uiState = MutableStateFlow(UiState(listOf(), listOf()))
    var uiState = _uiState.asStateFlow()

    fun checkIn(calEvent: CalEvent) {
        //TODO: Authenticate User, check-in with server (or local) using auth details
        throw NotImplementedError()
    }

    fun getCalendarEvents() {
        //TODO: Query calendar to get events for Today (System Clock), separate current Events and future by dates and durations
        throw NotImplementedError()
    }
}