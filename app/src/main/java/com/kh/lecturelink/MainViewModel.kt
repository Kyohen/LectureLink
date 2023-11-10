package com.kh.lecturelink

import android.location.Location
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
sealed class CheckInState {
    object CheckedIn: CheckInState()
    object CantCheckIn: CheckInState()
    object NotCheckedIn: CheckInState()
}
data class WrappedEvent(
    val checkedIn: CheckInState,
    val event: CalEvent
)

data class UiState(
    val currentEvents: List<WrappedEvent>,
    val futureEvents: List<WrappedEvent>
)
class MainViewModel(private val calendarManager: CalendarManager, private val locationManager: LocationManaging): ViewModel() {
    private val events = Channel<Action>()

    val state = events.receiveAsFlow()
        .runningFold(UiState(listOf(), listOf()), ::reduceState)
        .stateIn(viewModelScope, Eagerly, UiState(listOf(), listOf()))

    private fun handleEvent(event: Action) {
        events.trySend(event)
    }

    private fun reduceState(currentState: UiState, action: Action): UiState {
        Log.d("HandleEvent", "$action")
        return when (action) {
            is Action.CheckInEvent -> TODO()
            is Action.EventsCalculated -> currentState.copy(currentEvents = action.currentEvents, futureEvents = action.futureEvents)
            is Action.LocationUpdate -> TODO()
        }
    }

    var location = locationManager.locationStateFlow

    private var _uiState = MutableStateFlow(UiState(listOf(), listOf()))
    var uiState = _uiState.asStateFlow()

    fun checkIn(calEvent: WrappedEvent) {
        //TODO: Authenticate User, check-in with server (or local) using auth details
        throw NotImplementedError()
    }

    fun getCalendarEvents() {
        //TODO: separate current Events and future by dates and durations, fetch CheckInStatus
        val c = Calendar.getInstance()
        val currentTime = Calendar.getInstance()
        c.add(Calendar.DATE, 1)
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        val events = calendarManager.fetchEvents("Calendar", currentTime, c)

        //cases: current time after startTime - 15mins && currentTime is before end time
        val (current, future) = events.partition {
            currentTime.timeInMillis >= it.startTime + (15*60*1000) && currentTime.timeInMillis <= it.endTime
        }

        //Check events for check in status
        val futureWrapped = future.map {
            WrappedEvent(CheckInState.CantCheckIn, it)
        }

        val currentWrapped = current.map {
            WrappedEvent(CheckInState.NotCheckedIn, it)
        }
        handleEvent(Action.EventsCalculated(currentWrapped, futureWrapped))
    }
    fun startLocations() = locationManager.StartContinousServices()
}