package com.kh.lecturelink.Managers

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


enum class LocationPerm {
    GRANTED,
    DENIED
}
interface LocationManaging: LocationListener {
    var locationStateFlow: StateFlow<Location?>
    fun getCurrentLocation(): Location?
    fun getCurrentPermissions(): LocationPerm
    fun askPermission(withCallback: (LocationPerm) -> Unit)
    fun StartContinousServices()
    fun singleRequestPls()
    var servicesStarted: Boolean
}

class AppLocationManager(private val client: LocationManager) : LocationManaging {
    private var _locationStateFlow = MutableStateFlow<Location?>(null)
    override var locationStateFlow: StateFlow<Location?> = _locationStateFlow.asStateFlow()
    override var servicesStarted: Boolean = false

    override fun onLocationChanged(p0: Location) {
        _locationStateFlow.value = p0
    }

    override fun getCurrentLocation(): Location? =
        _locationStateFlow.value

    override fun getCurrentPermissions(): LocationPerm {
        TODO("Not yet implemented")
    }

    override fun askPermission(withCallback: (LocationPerm) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun singleRequestPls(){
        Log.d("ZZZ", client.allProviders.toString())
        try {
            client.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 50f, this)
            servicesStarted = true
        } catch (e: SecurityException) {
            Log.e("LocationManager", "Tried to access without permission")
        }
    }
    override fun StartContinousServices() {
        Log.d("ZZZ", client.allProviders.toString())
        try {
            client.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 50f, this)
            servicesStarted = true
        } catch (e: SecurityException) {
            Log.e("LocationManager", "Tried to access without permission")
        }

    }

    fun stopUpdates() {
        client.removeUpdates(this)
    }
}