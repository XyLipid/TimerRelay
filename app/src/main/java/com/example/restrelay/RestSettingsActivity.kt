package com.example.restrelay

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RestSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rest_settings)

        val editSkip = findViewById<EditText>(R.id.edit_skip_threshold)
        val checkUnsure = findViewById<CheckBox>(R.id.check_unsure_alert)
        val editStale = findViewById<EditText>(R.id.edit_stale_sec)

        editSkip.setText(Prefs.skipThresholdSec(this).toString())
        checkUnsure.isChecked = Prefs.unsureAlert(this)
        editStale.setText(Prefs.staleSec(this).toString())

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            Prefs.setSkipThresholdSec(this, editSkip.text.toString().toIntOrNull()?.coerceIn(0, 60) ?: 3)
            Prefs.setUnsureAlert(this, checkUnsure.isChecked)
            Prefs.setStaleSec(this, editStale.text.toString().toIntOrNull()?.coerceIn(1, 3600) ?: 15)
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
    }
}
