package com.example.restrelay

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationsPermissionIfNeeded()

        findViewById<Button>(R.id.btn_access).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btn_rules).setOnClickListener {
            startActivity(Intent(this, RulesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_appearance).setOnClickListener {
            startActivity(Intent(this, AppearanceActivity::class.java))
        }
        findViewById<Button>(R.id.btn_delivery).setOnClickListener {
            startActivity(Intent(this, DeliverySettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_rest).setOnClickListener {
            startActivity(Intent(this, RestSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_log).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        val ignoreEta = findViewById<Switch>(R.id.switch_ignore_eta)
        ignoreEta.isChecked = Prefs.ignoreEta(this)
        ignoreEta.setOnCheckedChangeListener { _, checked -> Prefs.setIgnoreEta(this, checked) }
    }

    override fun onResume() {
        super.onResume()
        val status = findViewById<TextView>(R.id.status)
        val granted = NotificationAccess.isEnabled(this)
        status.text = getString(if (granted) R.string.status_granted else R.string.status_denied)
        status.setTextColor(ContextCompat.getColor(this, if (granted) R.color.ok else R.color.err))
    }

    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}

/** Checks whether our listener is enabled in the system's notification-access list. */
object NotificationAccess {
    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val wanted = ComponentName(context, RestListenerService::class.java).flattenToString()
        return flat.split(':').any { it == wanted }
    }
}
