package com.kh.lecturelink

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.util.Calendar
import java.util.Date

data class CalEvent(
    val title: String,
    val location: String,
    val id: Long,
    val startTime: Long,
    val endTime: Long
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

        const val selectionEvents = "(${CalendarContract.Events.CALENDAR_DISPLAY_NAME} = ?)" + " AND (${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTEND} <= ?)"

    }

//    Calendar startTime = Calendar.getInstance();
//
//    startTime.set(Calendar.HOUR_OF_DAY,0);
//    startTime.set(Calendar.MINUTE,0);
//    startTime.set(Calendar.SECOND, 0);
//
//    Calendar endTime= Calendar.getInstance();
//    endTime.add(Calendar.DATE, 1);
//
//    String selection = "(( " + CalendarContract.Events.DTSTART + " >= " + startTime.getTimeInMillis() + " ) AND ( " + CalendarContract.Events.DTSTART + " <= " + endTime.getTimeInMillis() + " ) AND ( deleted != 1 ))";
//    Cursor cursor = context.getContentResolver().query(CalendarContract.Events.CONTENT_URI, projection, selection, null, null);


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
    }

    fun fetchEvents(calendar: String, firstDate: Calendar, endDate: Calendar): List<CalEvent> {
        val selectArgs = arrayOf(calendar, firstDate.timeInMillis.toString(), endDate.timeInMillis.toString())
        val cur2 = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.TITLE, CalendarContract.Events._ID, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND),
            selectionEvents,
            selectArgs,
            null
        ) as Cursor

        val events = mutableListOf<CalEvent>()
        while (cur2.moveToNext()) {
            // Get the field values
            val location: String = cur2.getString(0)
            val title: String = cur2.getString(1)

            val id = cur2.getLong(2)
            val startTime = cur2.getLong(3)
            val endTime = cur2.getLong(4)

            events.add(CalEvent(title, location, id, startTime, endTime))
        }

        _events.value = events.toList()
        cur2.close()
        return events.toList()
    }

}