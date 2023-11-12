package com.kh.lecturelink

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kh.lecturelink.Managers.CalEvent
import com.kh.lecturelink.Managers.CalendarManager
import com.kh.lecturelink.Managers.CheckInManager
import com.kh.lecturelink.Managers.LocationManaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.floor

sealed class CheckInState {
    object CheckedIn: CheckInState()
    object CantCheckIn: CheckInState()
    object NotCheckedIn: CheckInState()
    object NotKnown: CheckInState()
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
    val isLoadingEvents: Boolean,
    val lastFetchInMinutes: String,
    val timerToggled: Boolean
) {
    companion object {
        fun defaultUiState(): UiState = UiState(listOf(), listOf(), false, "now", false)
    }
}

class MainViewModel(
    private val calendarManager: CalendarManager,
    private val locationManager: LocationManaging,
    private val checkInManager: CheckInManager
): ViewModel() {
    private val events = Channel<Action>()

    private var lastFetch = Calendar.getInstance()

    private var lastRun = System.currentTimeMillis()
    private val updateEveryMinuteRoutine = suspend {
        while (true) {
            val currentTime = System.currentTimeMillis()
            handleEvent(Action.TimerTickedMinute)

            // Check if 15 minutes have passed since the last run
            if (currentTime - lastRun >= 15 * 60 * 1000) {
                handleEvent(Action.TimerTicked15Minutes)
                lastRun = currentTime
            }
            // Delay the coroutine for one minute
            delay(60 * 1000)
        }
    }

    var location = locationManager.locationStateFlow
    //TODO: Make this a state update from within manager

    val state = events.receiveAsFlow()
        .runningFold(UiState.defaultUiState(), ::reduceState)
        .stateIn(viewModelScope, Eagerly, UiState.defaultUiState())

    private fun handleEvent(event: Action) {
        events.trySend(event)
    }

    fun setupAlarm(){
        Log.d("ZZZ", "Setup alarms")
        viewModelScope.launch {
            updateEveryMinuteRoutine()
        }
    }

    private fun reduceState(currentState: UiState, action: Action): UiState {
        Log.d("HandleEvent", "$action")
        return when (action) {
            is Action.CheckedInEvent -> TODO()
            is Action.EventsCalculated -> {
                lastFetch = Calendar.getInstance()
                pollCurrentEventCheckIn(action.currentEvents)
                currentState.copy(currentEvents = action.currentEvents, futureEvents = action.futureEvents, isLoadingEvents = false)
            }
            is Action.LocationUpdate -> TODO()
            is Action.LoadingEvents -> currentState.copy(isLoadingEvents = true)
            is Action.TimerTicked15Minutes -> {
                getCalendarEvents()
                currentState
            }
            is Action.TimerTickedMinute -> {
                val lastFetchInt = floor(((Calendar.getInstance().timeInMillis - lastFetch.timeInMillis)/60000f)).toInt()
                val lastFetchString = if(lastFetchInt == 0) "now" else if (lastFetchInt == 1) "$lastFetchInt min ago" else "$lastFetchInt mins ago"
                currentState.copy(timerToggled = currentState.timerToggled.not(), lastFetchInMinutes = lastFetchString)
            }

            is Action.HavePolledEvent -> {
                val events = mutableListOf<WrappedEvent>()
                for (event in currentState.currentEvents) {
                    val e = if (event.event.id == action.eventId) {
                        event.copy(checkedIn = action.state)
                    } else {
                        event
                    }
                    events.add(e)
                }
                currentState.copy(currentEvents = events)
            }
            is Action.UpdatedEventCheckIn -> currentState.copy(currentEvents = action.events)
        }
    }

    private fun pollCurrentEventCheckIn(currentEvents: List<WrappedEvent>) {
        CoroutineScope(Dispatchers.IO).launch {
            delay(2000)
            Log.e("ZZZZ", currentEvents.toString())
            val v = mutableListOf<WrappedEvent>()
            currentEvents.forEach {
                Log.e("ZZZZ", it.toString())
                val res = checkInManager.pollEventCheckin(it.event.id)
                Log.e("ZZZZ", res.toString())
                v.add(it.copy(checkedIn = res))
            }

            Log.e("ZZZZ", v.toString())
            launch(Dispatchers.Main) {
                Log.e("ZZZZ", v.toString())
                handleEvent(Action.UpdatedEventCheckIn(v))
            }
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            checkInManager.store.clearStore()
            pollCurrentEventCheckIn(state.value.currentEvents)
        }
    }
    fun checkIn(calEvent: WrappedEvent) {
        //TODO: Authenticate User, check-in with server (or local) using auth details
        CoroutineScope(Dispatchers.IO).launch {
            val res = checkInManager.checkIn(calEvent.event.id)
            launch(Dispatchers.Main) {
                handleEvent(Action.HavePolledEvent(calEvent.event.id, res))
            }
        }
    }

    fun onResume() {
        getCalendarEvents()
        setupAlarm()
    }

    fun onPause() {
//        cancelAlarms()
    }

    private fun getCalendarEvents() {
        //TODO: separate current Events and future by dates and durations, fetch CheckInStatus
        handleEvent(Action.LoadingEvents)
        viewModelScope.launch {
            val c = Calendar.getInstance()
            c.timeZone = TimeZone.getTimeZone("UTC")
            c.add(Calendar.DATE, 4)
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)

            val currentTime = Calendar.getInstance()
            currentTime.timeZone = TimeZone.getTimeZone("UTC")
            currentTime.set(Calendar.HOUR_OF_DAY, 11)
            currentTime.set(Calendar.MINUTE, 44)
            currentTime.add(Calendar.DATE, 3)

            val events = calendarManager.fetchEvents("Calendar", currentTime, c)

            //cases: current time after startTime - 15mins && currentTime is before end time
            val (current, future) = events.partition {
                currentTime.timeInMillis >= it.startTime - (15*60*1000) && currentTime.timeInMillis <= it.endTime
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
                WrappedEvent(CheckInState.NotKnown, start, end, it)
            }
            handleEvent(Action.EventsCalculated(currentWrapped, futureWrapped))
        }
    }
    fun startLocations() = locationManager.StartContinousServices()
}