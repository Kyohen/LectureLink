package com.kh.lecturelink

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CalEvent(
    val title: String,
    val location: String,
    val id: Long
)
class CalendarManager(private val contentResolver: ContentResolver) {
    private var _calendarsList = MutableStateFlow<List<String>>(listOf())
    private var _events = MutableStateFlow<List<CalEvent>>(listOf())

    val calendarsList = _calendarsList.asStateFlow()
    val calendarEventsList = _events.asStateFlow()

    companion object {
        val EVENT_PROJECTION: Array<String> = arrayOf(
            CalendarContract.Calendars._ID,                     // 0
            CalendarContract.Calendars.ACCOUNT_NAME,            // 1
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,   // 2
            CalendarContract.Calendars.OWNER_ACCOUNT            // 3
        )

        val calUri: Uri = CalendarContract.Calendars.CONTENT_URI

        val selectionEvents = "${CalendarContract.Events.CALENDAR_DISPLAY_NAME} = ?"

    }
    fun fetchCalendars() {
        var cur: Cursor? = null
        try {
            cur = contentResolver.query(
                Uri.parse("content://com.android.calendar/calendars"),
                arrayOf("_id", "calendar_displayName"),
                null,
                null,
                null
            ) as Cursor

            val calendars: MutableList<String> = mutableListOf()
            while (cur.moveToNext()) {
                val id = cur.getInt(0)
                val calendarName = cur.getString(1)
                calendars.add(calendarName)
            }

            _calendarsList.value = calendars.toList()
            Log.d("ZZZ", "cal: ${calendarsList.value}")
        } catch (e: SecurityException) {
            Log.e("Cal", "SecurtiyException")
        } finally {
            cur?.let { it.close() }
        }

//        if (cur.count > 0) {
//            cur.moveToFirst()
//            val calendarNames = arrayOfNulls<String>(cur.getCount())
//            // Get calendars id
//            val calendarIds = IntArray(cur.getCount())
//            for (i in 0 until cur.getCount()) {
//                calendarIds[i] = cur.getInt(0)
//                calendarNames[i] = cur.getString(1)
//                Log.i("@calendar", "Calendar Name : " + calendarNames[i])
//                cur.moveToNext()
//            }
//        } else {
//            Log.e("@calendar", "No calendar found in the device")
//        }
    }

    fun fetchEvents(calendar: String) {
        val selectArgs = arrayOf(calendar)
        val cur2 = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION, CalendarContract.Events._ID),
            selectionEvents,
            selectArgs,
            null
        ) as Cursor

        val events = mutableListOf<CalEvent>()
        while (cur2.moveToNext()) {
            // Get the field values
            val location: String = cur2.getString(0)
            val title: String = cur2.getString(1)
            var desc = ""
            try {
                desc = cur2.getString(2)
            } catch(_: Exception) {

            }

            val id = cur2.getLong(3)

            events.add(CalEvent(title, location, id))
        }

        _events.value = events.toList()
        cur2.close()
    }

}