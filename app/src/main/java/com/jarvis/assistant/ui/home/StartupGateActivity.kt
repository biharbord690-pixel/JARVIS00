package com.jarvis.assistant.ui.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class StartupGateActivity : AppCompatActivity() {

    private lateinit var message: TextView
    private lateinit var retryButton: Button
    private lateinit var exitButton: Button
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(12, 15, 28))
        }

        val title = TextView(this).apply {
            text = "JARVIS INTELLIGENCE OS"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        message = TextView(this).apply {
            text = "Verifying secure configuration..."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val progress = ProgressBar(this)

        retryButton = Button(this).apply {
            text = "Retry"
            isEnabled = false
            setOnClickListener { verifyConfiguration() }
        }

        exitButton = Button(this).apply {
            text = "Exit"
            setOnClickListener { finishAffinity() }
        }

        layout.addView(title)
        layout.addView(message)
        layout.addView(progress)
        layout.addView(
            retryButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        layout.addView(
            exitButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(layout)
        verifyConfiguration()
    }

    private fun verifyConfiguration() {
        if (checking || isFinishing || isDestroyed) return
        checking = true
        retryButton.isEnabled = false
        message.text = "Connecting to Firebase and checking settings..."

        lifecycleScope.launch {
            try {
                val config = RemoteConfigManager.fetch()

                if (config == null) {
                    showBlocked("Configuration could not be verified. Check your internet and retry.")
                    return@launch
                }

                RemoteConfigManager.currentConfig = config

                if (config.maintenanceMode) {
                    showBlocked(
                        config.maintenanceMessage.ifBlank {
                            "JARVIS is temporarily under maintenance."
                        }
                    )
                    return@launch
                }

                if (config.updateRequired) {
                    showBlocked("An update is required before you can use JARVIS.")
                    return@launch
                }

                if (config.licenseRequired && !config.keylessMode) {
                    message.text = "Verifying license..."
                    val valid = LicenseManager.enforce(this@StartupGateActivity)
                    if (!valid || isFinishing || isDestroyed) {
                        if (!isFinishing) showBlocked("License verification is required to continue.")
                        return@launch
                    }
                } else if (config.licenseRequired == config.keylessMode) {
                    showBlocked("Invalid security configuration. Contact the administrator.")
                    return@launch
                }

                if (!isFinishing && !isDestroyed) {
                    startActivity(
                        Intent(this@StartupGateActivity, MainActivity::class.java)
                    )
                    finish()
                }
            } catch (_: Exception) {
                showBlocked("Security verification failed. Check your connection and retry.")
            } finally {
                checking = false
            }
        }
    }

    private fun showBlocked(text: String) {
        if (isFinishing || isDestroyed) return
        message.text = text
        retryButton.isEnabled = true
    }
}
