package com.kh.lecturelink.Services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices
import com.kh.lecturelink.Managers.CheckInManager
import com.kh.lecturelink.ui.views.MainActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    private val TAG = "GEO_BORADCAST"
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context?, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        val checkInManager = CheckInManager(context!!)

        val n = NotificationService(context)
        if (geofencingEvent == null) {
            Log.e(TAG, "geofencing event was null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes
                .getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, errorMessage)
            Log.e(TAG, "Ran into an error")
            return
        }

        // Get the transition type.
        val geofenceTransition = geofencingEvent?.geofenceTransition
        Log.e(TAG, "Event: $geofencingEvent")
        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            // Get the geofences that were triggered. A single event can trigger
            // multiple geofences.
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            // Send notification and log the transition details.
            Toast.makeText(context, geofenceTransition.toString(), Toast.LENGTH_SHORT).show()
            Log.e("Context exists", context?.let {  "Woo" } ?: run {"None" })
            n.sendHighPriorityNotification("You have left", "Go back to your lectures", MainActivity::class.java)
            Log.i(TAG, geofenceTransition.toString() + " " + triggeringGeofences.toString())
            val f = triggeringGeofences!!.first()

            val geoFenceClient = LocationServices.getGeofencingClient(context)
            geoFenceClient.removeGeofences(listOf(f.requestId))
            GlobalScope.launch(EmptyCoroutineContext) {
                checkInManager.checkOut(f.requestId.toLong())
                Log.e("ZZZ", "Checked out event")
            }
        } else {
            // Log the error.
            Log.e(TAG, "Error, was ${geofenceTransition.toString()}")
        }
    }


}

//class MyService : Service() {
//    private var broadcastReceiver: BroadcastReceiver? = null
//
//    inner class MyReceiver  // constructor
//        : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            // do something
//        }
//    }
//
//    override fun onCreate() {
//        // create IntentFilter
//        val intentFilter = IntentFilter()
//
//        //add actions
//        intentFilter.addAction("com.example.NEW_INTENT")
//        intentFilter.addAction("com.example.CUSTOM_INTENT")
//        intentFilter.addAction("com.example.FINAL_INTENT")
//
//        //create and register receiver
//        broadcastReceiver = MyReceiver()
//        registerReceiver(broadcastReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
//    }
//
//    override fun onBind(intent: Intent?): IBinder? {
//        return null
//    }
//}
//
//class GeofenceReceiverGeo : BroadcastReceiver() {
//    override fun onReceive(context: Context, intent: Intent) {
//        if (GeofencingEvent.fromIntent(intent)?.hasError() == true) {
//            // Handle error
//            return
//        }
//
//        // Handle geofence transition event here
//        // You may want to show a notification to the user
//        when (GeofencingEvent.fromIntent(intent)?.geofenceTransition) {
//            Geofence.GEOFENCE_TRANSITION_ENTER -> {
//                // Handle enter event
//            }
//            Geofence.GEOFENCE_TRANSITION_EXIT -> {
//                // Handle exit event and show a notification
//                sendNotification(context, "You left the geofenced area.")
//            }
//            else -> {
//                Log.d("ZSZ", "NOOOO")
//            }
//        }
//    }
//
//    private fun sendNotification(context: Context, message: String) {
//        // Implement notification logic here
//        // Use NotificationManager to show a notification
//        // ...
////        NotificationManager.
//        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
//    }
//}
