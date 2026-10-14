package com.jarvis.assistant.ui.home

import android.app.Activity
import android.text.InputFilter
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

object LicenseManager {

    private const val TAG = "JARVIS_License"
    private const val BASE_URL =
        "https://jarvis-remote-control-default-rtdb.firebaseio.com/licenseCheck/"

    private const val PREFS = "jarvis_license_prefs"
    private const val KEY = "activated_license_key"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private enum class Result {
        VALID, INVALID, EXPIRED, REVOKED, NETWORK_ERROR
    }

    private suspend fun verify(key: String): Result =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL$key.json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext Result.NETWORK_ERROR

                    val body = response.body?.string()
                        ?: return@withContext Result.NETWORK_ERROR

                    if (body == "null") return@withContext Result.INVALID

                    val item = JSONObject(body)
                    when {
                        item.optString("status") == "revoked" ->
                            Result.REVOKED

                        item.optString("status") != "active" ->
                            Result.INVALID

                        item.optLong("expiresAt", 0L) <= System.currentTimeMillis() ->
                            Result.EXPIRED

                        else -> Result.VALID
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "License verification failed", e)
                Result.NETWORK_ERROR
            }
        }

    suspend fun enforce(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val savedKey = prefs.getString(KEY, null)

        if (!savedKey.isNullOrBlank()) {
            when (verify(savedKey)) {
                Result.VALID -> return true
                Result.NETWORK_ERROR -> {
                    // Keep the saved key; network failure does not prove it is invalid.
                }
                else -> prefs.edit().remove(KEY).apply()
            }
        }

        if (activity.isFinishing || activity.isDestroyed) return false

        val result = CompletableDeferred<Boolean>()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 12, 48, 0)
        }

        val description = TextView(activity).apply {
            text = "Enter your 8-character JARVIS license key."
            textSize = 14f
        }

        val input = EditText(activity).apply {
            hint = "A7K9P2X4"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(
                InputFilter.LengthFilter(8),
                InputFilter.AllCaps()
            )
            singleLine = true
        }

        val status = TextView(activity).apply {
            textSize = 13f
        }

        container.addView(description)
        container.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(status)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("JARVIS License Activation")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Verify", null)
            .setNegativeButton("Exit") { _, _ ->
                if (!result.isCompleted) result.complete(false)
                activity.finishAffinity()
            }
            .create()

        dialog.setOnCancelListener {
            if (!result.isCompleted) result.complete(false)
        }

        dialog.setOnDismissListener {
            if (!result.isCompleted) result.complete(false)
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = input.text.toString()
                    .trim()
                    .uppercase(Locale.ROOT)

                if (!entered.matches(Regex("[A-Z0-9]{8}"))) {
                    status.text = "Enter exactly 8 letters or numbers."
                    return@setOnClickListener
                }

                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.isEnabled = false
                status.text = "Verifying license..."

                activity.lifecycleScope.launch {
                    when (verify(entered)) {
                        Result.VALID -> {
                            prefs.edit().putString(KEY, entered).apply()
                            status.text = "Activation successful."
                            if (!result.isCompleted) result.complete(true)
                            dialog.dismiss()
                        }

                        Result.EXPIRED -> {
                            status.text = "This license has expired."
                            button.isEnabled = true
                        }

                        Result.REVOKED -> {
                            status.text = "This license has been revoked."
                            button.isEnabled = true
                        }

                        Result.INVALID -> {
                            status.text = "Invalid license key."
                            button.isEnabled = true
                        }

                        Result.NETWORK_ERROR -> {
                            status.text = "Could not verify. Check internet and retry."
                            button.isEnabled = true
                        }
                    }
                }
            }
        }

        dialog.show()
        return result.await()
    }
}
