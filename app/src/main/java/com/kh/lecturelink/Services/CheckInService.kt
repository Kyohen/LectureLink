package com.kh.lecturelink.Services

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface CheckInService {
    suspend fun checkIn(eventId: Long)
    suspend fun pollEventCheckIn(eventId: Long): Boolean

    suspend fun checkOut(eventId: Long)
}
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class LocalCheckInService(private val appContext: Context): CheckInService {

    override suspend fun checkIn(eventId: Long) {
        val EXAMPLE_COUNTER = booleanPreferencesKey("event_${eventId}")
        appContext.dataStore.edit { settings ->
            settings[EXAMPLE_COUNTER] = true
        }
    }

    suspend fun clearStore() {
        appContext.dataStore.edit {
            it.clear()
        }
    }
    override suspend fun pollEventCheckIn(eventId: Long): Boolean {
        val EXAMPLE_COUNTER = booleanPreferencesKey("event_${eventId}")
        Log.e("ZZZZ", eventId.toString())
        return appContext.dataStore.data
            .map { preferences ->
                Log.e("ZZZZ", (preferences[EXAMPLE_COUNTER] ?: false).toString())
                preferences[EXAMPLE_COUNTER] ?: false
            }.first()
    }

    override suspend fun checkOut(eventId: Long) {
        val EXAMPLE_COUNTER = booleanPreferencesKey("event_${eventId}")
        appContext.dataStore.edit { settings ->
            settings[EXAMPLE_COUNTER] = false
        }
    }

}