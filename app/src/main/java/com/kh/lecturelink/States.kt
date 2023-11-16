package com.kh.lecturelink

import android.location.Location
import com.kh.lecturelink.Managers.CalEvent

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
    val location: Location?,
    val isInLocation: Boolean,
    val event: CalEvent,
    val password: String?
)

data class AuthState(
    val needAuth: Boolean,
    val authFailed: Boolean,
    val event: WrappedEvent?
) {
    companion object {
        fun defaultAuthState(): AuthState = AuthState(false, false, null)
    }
}

data class UiState(
    val currentEvents: List<WrappedEvent>,
    val futureEvents: List<WrappedEvent>,
    val isLoadingEvents: Boolean,
    val lastFetchInMinutes: String,
    val timerToggled: Boolean,
    val currentLocation: Location?,
    val authState: AuthState,
    val needBiometric: Boolean
) {
    companion object {
        fun defaultUiState(): UiState = UiState(listOf(), listOf(), false, "now", false, null, AuthState.defaultAuthState(), false)
    }
}