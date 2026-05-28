package com.piums.cliente.data.location

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationSearchHelper @Inject constructor(private val gson: Gson) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun reverseGeocode(lat: Double, lng: Double): LocationSuggestion? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://nominatim.openstreetmap.org/reverse" +
                      "?format=json&lat=$lat&lon=$lng"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PiumsCliente/1.0 (soporte@piums.io)")
                .build()
            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string() ?: return@runCatching null
            }
            val result = gson.fromJson(body, NominatimResult::class.java)
            LocationSuggestion(displayName = result.displayName.toShortName(), lat = lat, lng = lng)
        }.getOrNull()
    }

    suspend fun search(query: String): List<LocationSuggestion> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = Uri.encode(query)
            val url = "https://nominatim.openstreetmap.org/search" +
                      "?q=$encoded&format=json&countrycodes=gt&limit=6&addressdetails=0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PiumsCliente/1.0 (soporte@piums.io)")
                .build()

            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                resp.body?.string() ?: return@runCatching emptyList()
            }

            val type = object : TypeToken<List<NominatimResult>>() {}.type
            val results: List<NominatimResult> = gson.fromJson(body, type)
            results.map {
                LocationSuggestion(
                    displayName = it.displayName.toShortName(),
                    lat         = it.lat.toDoubleOrNull() ?: 0.0,
                    lng         = it.lon.toDoubleOrNull() ?: 0.0
                )
            }
        }.getOrDefault(emptyList())
    }
}

private fun String.toShortName(): String =
    split(",").take(2).joinToString(",").trim()

private data class NominatimResult(
    @SerializedName("display_name") val displayName: String,
    val lat: String,
    val lon: String
)
