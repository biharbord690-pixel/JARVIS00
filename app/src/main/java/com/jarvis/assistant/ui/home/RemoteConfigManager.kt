package com.jarvis.assistant.ui.home

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class JarvisRemoteConfig(
    val maintenanceMode: Boolean,
    val maintenanceMessage: String,
    val updateRequired: Boolean,
    val appDisplayName: String,
    val ownerName: String,
    val instagramUrl: String,
    val licenseRequired: Boolean,
    val keylessMode: Boolean
)

object RemoteConfigManager {
    @Volatile
    var currentConfig: JarvisRemoteConfig? = null
    private const val TAG = "JARVIS_RemoteConfig"
    private const val CONFIG_URL =
        "https://jarvis-remote-control-default-rtdb.firebaseio.com/config.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(): JarvisRemoteConfig? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(CONFIG_URL)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Config HTTP status: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string()
                    ?: return@withContext null

                if (body == "null") {
                    return@withContext null
                }

                val json = JSONObject(body)

                if (!json.has("maintenanceMode") ||
                    !json.has("updateRequired") ||
                    !json.has("licenseRequired") ||
                    !json.has("keylessMode")
                ) {
                    Log.w(TAG, "Required config fields are missing")
                    return@withContext null
                }

                val licenseRequired = json.getBoolean("licenseRequired")
                val keylessMode = json.getBoolean("keylessMode")

                if (licenseRequired == keylessMode) {
                    Log.w(TAG, "Invalid license/keyless configuration")
                    return@withContext null
                }

                JarvisRemoteConfig(
                    maintenanceMode = json.getBoolean("maintenanceMode"),
                    maintenanceMessage = json.optString(
                        "maintenanceMessage",
                        "JARVIS is temporarily under maintenance."
                    ),
                    updateRequired = json.getBoolean("updateRequired"),
                    appDisplayName = json.optString(
                        "appDisplayName",
                        "JARVIS INTELLIGENCE OS"
                    ),
                    ownerName = json.optString("ownerName", ""),
                    instagramUrl = json.optString("instagramUrl", ""),
                    licenseRequired = licenseRequired,
                    keylessMode = keylessMode
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not verify remote configuration", e)
            null
        }
    }
}
