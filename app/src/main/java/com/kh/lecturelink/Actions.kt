package com.kh.lecturelink

import android.content.Context
import android.location.Location

// A look and doing some kind of MVI/Redux
sealed class Action {
    data class LocationUpdate(val location: Location): Action()
    object LoadingEvents: Action()
    object TimerTickedMinute: Action()
    object TimerTicked15Minutes: Action()
    data class EventsCalculated(val currentEvents: List<WrappedEvent>, val futureEvents: List<WrappedEvent>): Action()
    data class HavePolledEvent(val eventId: Long, val state: CheckInState): Action()
    data class UpdatedEventCheckIn(val events: List<WrappedEvent>): Action()
    data class IsInLocationUpdated(val events: List<WrappedEvent>): Action()
    data class PasswordCorrectFor(val eventId: WrappedEvent, val ctx: Context): Action()
    data class CheckInPressed(val event: WrappedEvent, val ctx: Context): Action()
    object AuthCancelled: Action()
    object PasswordIncorrect: Action()
    data class biometricSucceeded(val ctx: Context): Action()
    object biometricFailed: Action()
}
