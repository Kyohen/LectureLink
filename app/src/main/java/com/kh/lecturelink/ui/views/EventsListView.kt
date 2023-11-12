package com.kh.lecturelink.ui.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kh.lecturelink.CheckInState
import com.kh.lecturelink.Managers.CalEvent
import com.kh.lecturelink.WrappedEvent

@Composable
fun EventsListView(list: List<WrappedEvent>, onCheckInClicked: (WrappedEvent) -> Unit, titleLines: Int, defaultView: @Composable () -> Unit) {
    if (list.isEmpty()) {
        defaultView()
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            this.items(list) {
                EventCardView(event = it, onCheckinClicked = onCheckInClicked, titleLines)
            }
        }
    }
}

@Composable
fun CheckInButtonLabel(state: CheckInState) {
    when (state) {
        CheckInState.CantCheckIn -> Text("Not available")
        CheckInState.CheckedIn -> Text("Checked in")
        CheckInState.NotCheckedIn -> Text("Check in")
        else -> { CircularProgressIndicator(color = Color.Gray, modifier = Modifier.size(20.dp)) }
    }
}

fun buttonEnabled(state: CheckInState, inLocation: Boolean): Boolean {
    return when (state) {
        CheckInState.CantCheckIn -> false
        CheckInState.CheckedIn -> false
        CheckInState.NotCheckedIn -> true
        CheckInState.NotKnown -> false
    } && inLocation
}

@Composable
fun EventCardView(event: WrappedEvent, onCheckinClicked: (WrappedEvent) -> Unit, titleLines: Int) {
    OutlinedCard(elevation = CardDefaults.cardElevation(
        defaultElevation = 6.dp
    ),
        modifier = Modifier
            .height(height = 120.dp)
            .fillMaxWidth(0.9F),
        border = BorderStroke(1.dp, Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Text("${event.event.id}. ${event.event.title}", overflow = TextOverflow.Ellipsis, maxLines = titleLines)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.event.location)
                Text("${event.startTime} - ${event.endTime}")
            }
            event.location?.let {
                if (event.isInLocation) Text("You are at ${event.event.location}", color = Color.Blue) else Text("You are not at ${event.event.location}", color = Color.Red)
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.Bottom) {
                Button(onClick = { onCheckinClicked(event) }, enabled = buttonEnabled(event.checkedIn, event.isInLocation)) {
                    CheckInButtonLabel(state = event.checkedIn)
                }
            }
        }
    }
}

@Composable
fun NoEventsView(text: String) {
    OutlinedCard(
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        ),
        modifier = Modifier
            .height(height = 100.dp)
            .fillMaxWidth(0.8F),
        border = BorderStroke(1.dp, Color.Black)
    ) {
        Text(
            text, modifier = Modifier
                .fillMaxSize()
                .wrapContentHeight(), textAlign = TextAlign.Center, fontSize = 18.sp
        )
    }
}