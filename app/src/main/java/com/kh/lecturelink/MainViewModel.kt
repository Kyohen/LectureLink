package com.kh.lecturelink

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.kh.lecturelink.Managers.CalendarManager
import com.kh.lecturelink.Managers.CheckInManager
import com.kh.lecturelink.Managers.LocationManaging
import com.kh.lecturelink.Services.GeofenceBroadcastReceiver
import com.kh.lecturelink.Services.LocationMappingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.floor

class MainViewModel : ViewModel() {
    private val events = Channel<Action>()
    private var lastFetch = Calendar.getInstance()
    private var lastRun = System.currentTimeMillis()


    lateinit var calendarManager: CalendarManager
    var locationManager: LocationManaging? = null
        set(value) {
            field = value
            locJob.cancel()
            locJob = viewModelScope.launch {
                locationManager?.locationStateFlow?.collect {
                    if (!isActive) {
                        cancel()
                        return@collect
                    }

                    Log.e("LOC", it.toString())
                    it?.let { handleEvent(Action.LocationUpdate(it)) }
                }
            }
        }
    lateinit var checkInManager: CheckInManager
    lateinit var geoFenceClient: GeofencingClient
    lateinit var biometricManager: BiometricManager

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric login for my app")
        .setSubtitle("Log in using your biometric credential")
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL or BIOMETRIC_WEAK)
        .build()

    private fun setupGeoFence(context: Context, location: Location, expirary: Long, eventID: String) {
        val geofencePendingIntent: PendingIntent by lazy {
            val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
            // addGeofences() and removeGeofences().
            val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            PendingIntent.getBroadcast(context, 0, intent, flags)
        }


        val v = Geofence.Builder()
            .setRequestId(eventID)
            .setCircularRegion(
                location.latitude,
                location.longitude,
                LOCATION_RADIUS.toFloat()
            )
            .setExpirationDuration(expirary)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()


            val req = GeofencingRequest.Builder().apply {
                setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                addGeofence(v)
            }.build()

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        geoFenceClient.addGeofences(req, geofencePendingIntent).run {
            addOnSuccessListener {
                Log.d("ZZZ FNC", "Added")
            }
            addOnFailureListener {e ->
                Log.d("ZZZ FNC", "Failed $e")
                Toast.makeText(context, "Geofence couldn't be added", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var locJob = viewModelScope.launch {
        locationManager?.locationStateFlow?.collect {
            if (!isActive) {
                cancel()
                return@collect
            }
            it?.let { handleEvent(Action.LocationUpdate(it)) }
        }
    }

    val state = events.receiveAsFlow()
        .runningFold(UiState.defaultUiState(), ::reduceState)
        .stateIn(viewModelScope, Eagerly, UiState.defaultUiState())

    fun handleEvent(event: Action) {
        Log.d("HandleEventAction", event.toString())
        val r = events.trySend(event)
        Log.d("EventWasAdded", r.isSuccess.toString())
    }

    private fun setupAlarm(){
        Log.d("ZZZ", "Setup alarms")
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()

                // Check if 15 minutes have passed since the last run
                if (currentTime - lastRun >= 15 * 60 * 1000) {
                    handleEvent(Action.TimerTicked15Minutes)
                    lastRun = currentTime
                }
                // Delay the coroutine for one minute
                delay(60 * 1000)
                handleEvent(Action.TimerTickedMinute)
            }
            Log.e("TIMER", "cancelled")
        }
    }

    private var timerJob: Job? = null
    private fun reduceState(currentState: UiState, action: Action): UiState {
        Log.d("HandleEvent", "$action")
        return when (action) {
            is Action.EventsCalculated -> {
                lastFetch = Calendar.getInstance()
                pollCurrentEventCheckIn(action.currentEvents)
                currentState.copy(currentEvents = action.currentEvents, futureEvents = action.futureEvents, isLoadingEvents = false)
            }
            is Action.LocationUpdate -> {
                pollCurrentEventCheckIn(currentState.currentEvents)
                currentState.copy(currentLocation = action.location)
            }
            is Action.LoadingEvents -> currentState.copy(isLoadingEvents = true)
            is Action.TimerTicked15Minutes -> {
                getCalendarEvents()
                currentState
            }
            is Action.TimerTickedMinute -> {
                locationManager?.singleRequestPls()
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
            is Action.IsInLocationUpdated -> currentState.copy(currentEvents = action.events)
            is Action.PasswordCorrectFor -> {
                val needBio = biometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                if (needBio != 0) {
                    checkIn(action.eventId, action.ctx)
                }
                currentState.copy(authState = AuthState(needAuth = false, false, action.eventId), needBiometric = needBio == 0)
            }
            is Action.CheckInPressed -> {
                action.event.password?.let {
                    currentState.copy(authState = AuthState(needAuth = true, false, action.event))
                } ?: run {
                    checkIn(action.event, action.ctx)
                    currentState
                }
            }
            is Action.AuthCancelled -> currentState.copy(authState = AuthState(needAuth = false, false, null))
            is Action.PasswordIncorrect -> currentState.copy(authState = currentState.authState.copy(authFailed = true))
            is Action.biometricFailed -> currentState
            is Action.biometricSucceeded -> {
                currentState.authState.event?.let {
                    checkIn(it, action.ctx)
                }
                currentState.copy(authState = currentState.authState.copy(event = null), needBiometric = false)
            }
        }
    }

    private fun pollCurrentEventCheckIn(currentEvents: List<WrappedEvent>) {
        CoroutineScope(Dispatchers.IO).launch {
//            delay(2000)
            val v = mutableListOf<WrappedEvent>()
            currentEvents.forEach {
                // get check-in state
                var res = checkInManager.pollEventCheckin(it.event.id)
                state.value.currentLocation?.let { loc ->
                    // check whether current location is in range, if signedIn and not in area, sign them out
                    val inArea = isAtLocationOfEvent(it, loc)
                    if (res is CheckInState.CheckedIn && !inArea) {
                        res = checkInManager.checkOut(it.event.id)
                    }
                    v.add(it.copy(checkedIn = res, isInLocation = inArea))
                } ?: run {
                    v.add(it.copy(checkedIn = res))
                }
            }

            launch(Dispatchers.Main) {
                handleEvent(Action.UpdatedEventCheckIn(v))
            }
        }
    }

//    fun clearDatabase() {
//        viewModelScope.launch {
//            checkInManager.store.clearStore()
//            pollCurrentEventCheckIn(state.value.currentEvents)
//        }
//    }
    private fun checkIn(calEvent: WrappedEvent, context: Context) {
        //TODO: Authenticate User, check-in with server (or local) using auth details
        val timeToLast = calEvent.event.endTime - Calendar.getInstance().timeInMillis
        calEvent.location?.let {
            setupGeoFence(context, it, timeToLast, calEvent.event.id.toString())
        }
        CoroutineScope(Dispatchers.IO).launch {
            val res = checkInManager.checkIn(calEvent.event.id)
            launch(Dispatchers.Main) {
                handleEvent(Action.HavePolledEvent(calEvent.event.id, res))
            }
        }
    }

    fun checkInPressed(calEvent: WrappedEvent, context: Context) {
        handleEvent(Action.CheckInPressed(calEvent, context))
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
            c.add(Calendar.DATE, 1)
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)

            val currentTime = Calendar.getInstance()
            currentTime.timeZone = TimeZone.getTimeZone("UTC")
//            Log.e("ZZZ", DateFormat.getTimeInstance(DateFormat.LONG).format(currentTime.timeInMillis))
//            Log.e("ZZZ", DateFormat.getDateInstance(DateFormat.LONG).format(currentTime.timeInMillis))
            val events = calendarManager.fetchEvents("Calendar", currentTime, c)

            //cases: current time after startTime - 15mins && currentTime is before end time
            val (current, future) = events.partition {
                currentTime.timeInMillis >= it.startTime - (15*60*1000) && currentTime.timeInMillis <= it.endTime
            }

            //Check events for check in status

            val futureWrapped = future.map {
                val start = timeFormatter.format(it.startTime)
                val end = timeFormatter.format(it.endTime)
                
                WrappedEvent(CheckInState.CantCheckIn, start, end, null, false, it, "password")
            }

            val currentWrapped = current.map {
                val start = timeFormatter.format(it.startTime)
                val end = timeFormatter.format(it.endTime)
                
                val loc = LocationMappingService.mapLeicesterLocationToLatLon(it.location)
                WrappedEvent(CheckInState.NotKnown, start, end, loc, false, it, "password")
            }
            handleEvent(Action.EventsCalculated(currentWrapped, futureWrapped))
        }
    }

    private fun isAtLocationOfEvent(event: WrappedEvent, location: Location): Boolean {
        return event.location?.let { loc -> location.distanceTo(loc) < LOCATION_RADIUS } ?: false
    }
    fun startLocations() = locationManager?.StartContinousServices()

    fun passwordSubmit(password: String, calEvent: WrappedEvent, context: Context) {
        calEvent.password?.let {
            if (password == it)
                handleEvent(Action.PasswordCorrectFor(calEvent, context))
            else
                handleEvent(Action.PasswordIncorrect)
        }
    }

    fun authCancelled() {
        handleEvent(Action.AuthCancelled)
    }

    companion object {
        const val LOCATION_RADIUS = 70
        val timeFormatter: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    }
}
class BiometricCallBack(val context: Context, val handleEvent: (Action) -> Unit) : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        handleEvent(Action.biometricFailed)
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
//        state.value.authState.event?.let { Action.biometricSucceeded(it) }
        handleEvent(Action.biometricSucceeded(context))
    }
}