package com.kh.lecturelink

import android.location.Location
import com.kh.lecturelink.Managers.CalEvent

// A look and doing some kind of MVI/Redux
sealed class Action {
    data class LocationUpdate(val location: Location): Action()
    object loadingEvents: Action()
    data class EventsCalculated(val currentEvents: List<WrappedEvent>, val futureEvents: List<WrappedEvent>): Action()
    data class CheckInEvent(val event: CalEvent): Action()
}
