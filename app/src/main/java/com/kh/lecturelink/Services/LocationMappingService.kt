package com.kh.lecturelink.Services

import android.location.Location

object LocationMappingService {
    fun mapLeicesterLocationToLatLon(locationString: String): Location? {
        val l = Location("")
        val locId = locationString.split(" ").first()
        return LeicesterMap[locId]?.let {
            l.latitude = it.first
            l.longitude = it.second
            l
        }
    }

    private val LeicesterMap = mapOf(
        "CW" to Pair(52.6218, -1.12377),
        "CW2" to Pair(52.6218, -1.12377),
        "MSB" to Pair(52.623294, -1.12504),
        "BEN" to Pair(52.62332, -1.12287),
        "KE" to Pair(52.62129, -1.12583),
        "DW" to Pair(52.62034, -1.12477),
        "ATT" to Pair(52.62134, -1.12407),
        "SBB" to Pair(52.61893, -1.12904)
    )
}