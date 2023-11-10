package com.kh.lecturelink

import android.location.Location

// A look and doing some kind of MVI/Redux
sealed class Action {
    data class LocationUpdate(val location: Location): Action()
    data class EventsCalculated(val currentEvents: List<WrappedEvent>, val futureEvents: List<WrappedEvent>): Action()
    data class CheckInEvent(val event: CalEvent): Action()
}
