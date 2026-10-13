package com.jarvis.assistant.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RemoteConfigManager {

    private const val TAG = "JARVIS_RemoteConfig"

    private const val CONFIG_URL =
        "https://jarvis-remote-control-default-rtdb.firebaseio.com/config.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun check(activity: Activity) {
        val config = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(CONFIG_URL)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "HTTP error: ${response.code}")
                        return@withContext null
                    }

                    response.body?.string()?.let { JSONObject(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not load remote config", e)
                null
            }
        } ?: return

        if (activity.isFinishing || activity.isDestroyed) return

        val maintenance = config.optBoolean("maintenanceCode", false)
        val updateRequired = config.optBoolean("updateRequired", false)
        val message = config.optString(
            "maintenanceMessage",
            "JARVIS is under maintenance. Please try again later."
        )
        val instagramUrl = config.optString("instagramurl", "")

        if (maintenance) {
            AlertDialog.Builder(activity)
                .setTitle("JARVIS Maintenance")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Close App") { _, _ ->
                    activity.finishAffinity()
                }
                .show()
            return
        }

        if (updateRequired) {
            val builder = AlertDialog.Builder(activity)
                .setTitle("Update Required")
                .setMessage(
                    "A new version of JARVIS is required. Please get the latest version."
                )
                .setCancelable(false)
                .setPositiveButton("Close App") { _, _ ->
                    activity.finishAffinity()
                }

            builder.setNeutralButton("Instagram") { _, _ ->
                if (instagramUrl.startsWith("https://")) {
                    try {
                        activity.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(instagramUrl))
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not open Instagram URL", e)
                    }
                }
            }

            builder.show()
        }
    }
}
