package com.android1500.gpssetter.utils.ext


import android.content.Context
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Locale

suspend fun LatLng.getAddress(context: Context) = callbackFlow {
    withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        try {
            val addresses = Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0].getAddressLine(0)
                val parts = address.split(",".toRegex()).toTypedArray()
                if (parts.size > 1) {
                    sb.append(parts[0])
                    val index = address.indexOf(",") + 2
                    if (index > 1 && address.length > index) {
                        sb.append("\n").append(address.substring(index))
                    }
                } else {
                    sb.append(address)
                }
            }
        } catch (_: Throwable) {
            // Geocoder can throw IOException on network errors; emit empty.
        }
        trySend(sb.toString())
    }
    awaitClose { }
}