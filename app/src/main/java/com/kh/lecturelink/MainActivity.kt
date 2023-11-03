package com.kh.lecturelink

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.kh.lecturelink.ui.theme.LectureLinkTheme

class MainActivity : ComponentActivity() {
    private lateinit var service: LocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locManager = AppLocationManager(service)
        val calendarManager = CalendarManager(contentResolver)
        setContent {
            LectureLinkTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RootView(locManager, calendarManager)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RootView(locManager: LocationManaging, calendarManager: CalendarManager) {
    val loc by locManager.locationStateFlow.collectAsState()
    val cal by calendarManager.calendarsList.collectAsState()
    val events by calendarManager.calendarEventsList.collectAsState()

    val locPermState = rememberPermissionState(permission = android.Manifest.permission.ACCESS_FINE_LOCATION)
    val calPermState = rememberPermissionState(permission = android.Manifest.permission.READ_CALENDAR)

    LaunchedEffect(locPermState.status) {
        Log.d("ZZZ", locPermState.status.toString())
        if(locPermState.status == PermissionStatus.Granted)
            calPermState.launchPermissionRequest()
            locManager.StartContinousServices()
    }

    LaunchedEffect(events) {
        Log.d("ZZZ", events.toString())
    }
    LaunchedEffect("Key") {
        locPermState.launchPermissionRequest()
    }

    if(locPermState.status != PermissionStatus.Granted || calPermState.status != PermissionStatus.Granted) {
        enablePermissionsScreen(locationPerm = locPermState, calendarPerm = calPermState)
    } else {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Row {
                    Text("lat: ${loc?.latitude}")
                    Spacer(Modifier.size(10.dp))
                    Text("lon: ${loc?.longitude}")
                }
            Button(onClick = {
                calendarManager.fetchCalendars()
            }) {
                Text("Press me")
            }
            if (cal.isNotEmpty()) {
                calendarChoice(
                    calendarChoices = cal,
                    onCalendarSelect = { calendarManager.fetchEvents(it) },
                    selected = cal.first()
                )
            }

            EventsList(list = events)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun enablePermissionsScreen(locationPerm: PermissionState, calendarPerm: PermissionState) {
    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Accept Location and Calendar Permissions to continue")
        Button(onClick = {
            locationPerm.launchPermissionRequest()
            calendarPerm.launchPermissionRequest()
        }) {
            Text("Prompt Again")
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun calendarChoice(calendarChoices: List<String>, onCalendarSelect: (String) -> Unit, selected: String) {
    var isExpanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(selected) }

    Column {
        OutlinedTextField(
            value = selected,
            onValueChange = { selected = it },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            enabled = false,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                //For Icons
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant),
            label = {Text("Calendar")},
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) })
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }, modifier = Modifier
            .fillMaxWidth()) {
            calendarChoices.forEach {
                DropdownMenuItem(text = { Text(it) }, onClick = {
                    isExpanded = false
                    selected = it
                    onCalendarSelect(it)
                }, modifier = Modifier.border(BorderStroke(4.dp, Color.Black)))
            }
        }
    }
}

@Composable
fun EventsList(list: List<CalEvent>) {
    LazyColumn() {
        this.items(list) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color.Black))
            ) {
                Row {
                    Text(it.id.toString())
                    Text(it.title)
                }
                Text(it.location)
            }
        }
    }
}
//@Composable
//fun RootView(t: theSuperClass) {
//    val sensorManager: SensorManager = LocalContext.current.getSystemService(
//        ComponentActivity.SENSOR_SERVICE
//    ) as SensorManager
//
//    val counter by t.n.collectAsState()
//    Column {
////        sensorManager.getDefaultSensor(Sensor.)
//        Button(onClick = t::increment) {
//            Text(counter.toString())
//        }
//    }
//}

//@Composable
//fun sensorRow(v: String, )