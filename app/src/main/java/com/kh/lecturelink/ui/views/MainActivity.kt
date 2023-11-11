package com.kh.lecturelink.ui.views

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.kh.lecturelink.MainViewModel
import com.kh.lecturelink.Managers.AppLocationManager
import com.kh.lecturelink.Managers.CalendarManager
import com.kh.lecturelink.WrappedEvent
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
                    RootView(MainViewModel(calendarManager, locManager))
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RootView(viewModel: MainViewModel) {
    val loc by viewModel.location.collectAsState()
    val state = viewModel.state.collectAsState()

    val locPermState = rememberPermissionState(permission = android.Manifest.permission.ACCESS_FINE_LOCATION)
    val calPermState = rememberPermissionState(permission = android.Manifest.permission.READ_CALENDAR)

    LaunchedEffect(locPermState.status) {
        Log.d("ZZZ", locPermState.status.toString())
        if(locPermState.status == PermissionStatus.Granted) {
            calPermState.launchPermissionRequest()
            viewModel.startLocations()
        }
    }

    LaunchedEffect("Key") {
        locPermState.launchPermissionRequest()
    }

    if(locPermState.status != PermissionStatus.Granted || calPermState.status != PermissionStatus.Granted) {
        EnablePermissionsScreen(locationPerm = locPermState, calendarPerm = calPermState, LocalContext.current)
    } else {
        MainScreenView(viewModel, loc)
    }
    AnimatedVisibility(visible = loc?.latitude == null) {
        LoadingView("Fetching Location")
    }
}

@Composable
fun MainScreenView(viewModel: MainViewModel, loc: Location?) {
    val state = viewModel.state.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(lifecycle, loc) {
        when (lifecycle) {
            Lifecycle.State.RESUMED -> {
                loc?.latitude?.let {
                    viewModel.onResume()
                }
            }
            else -> {

            }
        }
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = state.value.isLoadingEvents) {
            LoadingView(text = "Loading Events")
        }
        CurrentAndUpcomingEventsView(currentEvents = state.value.currentEvents, upcomingEvents = state.value.futureEvents, onCheckIn = viewModel::checkIn)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentAndUpcomingEventsView(currentEvents: List<WrappedEvent>, upcomingEvents: List<WrappedEvent>, onCheckIn: (WrappedEvent) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
            title = {
                Text("Events")
            }
        )
    }) {
        Column(Modifier.fillMaxSize().padding(it), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Current Events", fontSize = 25.sp, modifier = Modifier.align(Alignment.Start).padding(6.dp))
            Divider(Modifier.padding(bottom = 16.dp))
            EventsListView(list = currentEvents, onCheckIn, titleLines = 1) {
                NoEventsView("You have no current events")
            }

            Text("Upcoming Events", fontSize = 25.sp, modifier = Modifier.align(Alignment.Start).padding(6.dp))
            Divider(Modifier.padding(bottom = 16.dp))
            EventsListView(list = upcomingEvents, onCheckInClicked = {}, titleLines = 2) {
                NoEventsView("You have no upcoming events")
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EnablePermissionsScreen(locationPerm: PermissionState, calendarPerm: PermissionState, context: Context) {
    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Accept Location and Calendar Permissions to continue")
        Button(onClick = {
            if (!locationPerm.status.shouldShowRationale || !calendarPerm.status.shouldShowRationale) {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                i.data = Uri.parse("package:" + context.packageName)
                context.startActivity(i)
            } else {
                locationPerm.launchPermissionRequest()
                calendarPerm.launchPermissionRequest()
            }
        }) {
            Text("Prompt Again")
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarChoice(calendarChoices: List<String>, onCalendarSelect: (String) -> Unit, selected: String) {
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
