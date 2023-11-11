package com.kh.lecturelink

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kh.lecturelink.Managers.CalEvent
import com.kh.lecturelink.Managers.CalendarManager
import com.kh.lecturelink.Managers.LocationManaging
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.TimeZone

sealed class CheckInState {
    object CheckedIn: CheckInState()
    object CantCheckIn: CheckInState()
    object NotCheckedIn: CheckInState()
}
data class WrappedEvent(
    val checkedIn: CheckInState,
    val startTime: String,
    val endTime: String,
    val event: CalEvent
)

data class UiState(
    val currentEvents: List<WrappedEvent>,
    val futureEvents: List<WrappedEvent>,
    val isLoadingEvents: Boolean
)
class MainViewModel(private val calendarManager: CalendarManager, private val locationManager: LocationManaging): ViewModel() {
    private val events = Channel<Action>()

    var location = locationManager.locationStateFlow //TODO: Make this a state update from within manager

    val state = events.receiveAsFlow()
        .runningFold(UiState(listOf(), listOf(), false), ::reduceState)
        .stateIn(viewModelScope, Eagerly, UiState(listOf(), listOf(), false))

    private fun handleEvent(event: Action) {
        events.trySend(event)
    }

    private fun reduceState(currentState: UiState, action: Action): UiState {
        Log.d("HandleEvent", "$action")
        return when (action) {
            is Action.CheckedInEvent -> TODO()
            is Action.EventsCalculated -> currentState.copy(currentEvents = action.currentEvents, futureEvents = action.futureEvents, isLoadingEvents = false)
            is Action.LocationUpdate -> TODO()
            is Action.loadingEvents -> currentState.copy(isLoadingEvents = true)
        }
    }

    fun checkIn(calEvent: WrappedEvent) {
        //TODO: Authenticate User, check-in with server (or local) using auth details
        throw NotImplementedError()
    }

    fun onResume() {
        getCalendarEvents()
    }

    private fun getCalendarEvents() {
        //TODO: separate current Events and future by dates and durations, fetch CheckInStatus
        handleEvent(Action.loadingEvents)
        viewModelScope.launch {
            val c = Calendar.getInstance()
            c.timeZone = TimeZone.getTimeZone("UTC")
            c.add(Calendar.DATE, 10)
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)

            val currentTime = Calendar.getInstance()
            currentTime.timeZone = TimeZone.getTimeZone("UTC")
            currentTime.add(Calendar.MINUTE, -15)

            val events = calendarManager.fetchEvents("Calendar", currentTime, c)

            //cases: current time after startTime - 15mins && currentTime is before end time
            val (current, future) = events.partition {
                currentTime.timeInMillis >= it.startTime - (15 * 60 * 1000) && currentTime.timeInMillis <= it.endTime
            }

            //Check events for check in status
            val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)

            val futureWrapped = future.map {
                val start = timeFormatter.format(it.startTime)
                val end = timeFormatter.format(it.endTime)
                WrappedEvent(CheckInState.CantCheckIn, start, end, it)
            }

            val currentWrapped = current.map {
                val start = timeFormatter.format(it.startTime)
                val end = timeFormatter.format(it.endTime)
                WrappedEvent(CheckInState.NotCheckedIn, start, end, it)
            }
            handleEvent(Action.EventsCalculated(currentWrapped, futureWrapped))
        }
    }
    fun startLocations() = locationManager.StartContinousServices()
}