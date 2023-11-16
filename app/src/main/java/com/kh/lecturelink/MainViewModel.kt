package com.kh.lecturelink

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.security.keystore.KeyProperties
import android.util.Log
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
import javax.crypto.Cipher
import kotlin.math.floor

class MainViewModel(
    private val context: Context,
    private val calendarManager: CalendarManager,
    private val locationManager: LocationManaging,
    private val checkInManager: CheckInManager,
    private val geoFenceClient: GeofencingClient,
    private val biometricManager: BiometricManager
): ViewModel() {
    private val events = Channel<Action>()
    private var lastFetch = Calendar.getInstance()
    private var lastRun = System.currentTimeMillis()

    val biometricCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            handleEvent(Action.biometricFailed)
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            state.value.authState.event?.let { Action.biometricSucceeded(it) }
                ?.let { handleEvent(it) }
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric login for my app")
        .setSubtitle("Log in using your biometric credential")
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL or BIOMETRIC_WEAK)
        .build()

    fun setupGeoFence(context: Context, location: Location, expirary: Long, eventID: String) {
            val geofencePendingIntent: PendingIntent by lazy {
                val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
                // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
                // addGeofences() and removeGeofences().
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            val v = Geofence.Builder()
                // Set the request ID of the geofence. This is a string to identify this
                // geofence.
                .setRequestId(eventID)

                // Set the circular region of this geofence.
                .setCircularRegion(
                    location.latitude,
                    location.longitude,
                    LOCATION_RADIUS.toFloat()
                )

                // Set the expiration duration of the geofence. This geofence gets automatically
                // removed after this period of time.
                .setExpirationDuration(expirary)

                // Set the transition types of interest. Alerts are only generated for these
                // transition. We track entry and exit transitions in this sample.
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)

                // Create the geofence.
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
                // Geofences added
                // ...
                Log.d("ZZZ", "Added")
            }
            addOnFailureListener {e ->
                // Failed to add geofences
                // ...
                Log.d("ZZZ", "Failed $e")
            }
        }
    }

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

    var location = viewModelScope.launch {
        locationManager.locationStateFlow.collect {
            it?.let { handleEvent(Action.LocationUpdate(it)) }
        }
    }

    val state = events.receiveAsFlow()
        .runningFold(UiState.defaultUiState(), ::reduceState)
        .stateIn(viewModelScope, Eagerly, UiState.defaultUiState())

    private fun handleEvent(event: Action) {
        Log.d("HaandleEventAction", event.toString())
        val r = events.trySend(event)
        Log.d("HandleEventAdded", r.toString())
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
                    checkIn(action.eventId)
                }
                currentState.copy(authState = AuthState(false, false, action.eventId), needBiometric = needBio == 0)
            }
            is Action.CheckInPressed -> {
                action.event.password?.let {
                    currentState.copy(authState = AuthState(true, false, action.event))
                } ?: run {
                    checkIn(action.event)
                    currentState
                }
            }
            is Action.AuthCancelled -> currentState.copy(authState = AuthState(false, false, null))
            is Action.PasswordIncorrect -> currentState.copy(authState = currentState.authState.copy(authFailed = true))
            is Action.biometricFailed -> currentState
            is Action.biometricSucceeded -> {
                checkIn(action.event)
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

    fun clearDatabase() {
        viewModelScope.launch {
            checkInManager.store.clearStore()
            pollCurrentEventCheckIn(state.value.currentEvents)
        }
    }
    private fun checkIn(calEvent: WrappedEvent) {
        //TODO: Authenticate User, check-in with server (or local) using auth details
        calEvent.location?.let {
            setupGeoFence(context, it, 1000*60*15, calEvent.event.id.toString())
        }
        CoroutineScope(Dispatchers.IO).launch {
            val res = checkInManager.checkIn(calEvent.event.id)
            launch(Dispatchers.Main) {
                handleEvent(Action.HavePolledEvent(calEvent.event.id, res))
            }
        }
    }

    fun checkInPressed(calEvent: WrappedEvent) {
        handleEvent(Action.CheckInPressed(calEvent))
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
            currentTime.add(Calendar.DATE, 0)
            currentTime.set(Calendar.HOUR_OF_DAY, 12)
            currentTime.set(Calendar.MINUTE, 0)
            currentTime.set(Calendar.SECOND, 0)
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
    fun startLocations() = locationManager.StartContinousServices()

    fun passwordSubmit(password: String, calEvent: WrappedEvent) {
        calEvent.password?.let {
            if (password == it)
                handleEvent(Action.PasswordCorrectFor(calEvent))
            else
                handleEvent(Action.PasswordIncorrect)
        }
    }

    fun authCancelled() {
        handleEvent(Action.AuthCancelled)
    }

    companion object {
        const val LOCATION_RADIUS = 100
        val timeFormatter: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    }
}