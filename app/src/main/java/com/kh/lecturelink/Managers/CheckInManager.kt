package com.kh.lecturelink.Managers

import android.content.Context
import com.kh.lecturelink.CheckInState
import com.kh.lecturelink.Services.LocalCheckInService

class CheckInManager(context: Context) {
    val store = LocalCheckInService(context)

    suspend fun pollEventCheckin(eventId: Long): CheckInState {
        return if (store.pollEventCheckIn(eventId)) CheckInState.CheckedIn else CheckInState.NotCheckedIn
    }

    // checks in user and returns the new result
    suspend fun checkIn(id: Long): CheckInState {
        store.checkIn(id)
        return pollEventCheckin(id)
    }
}