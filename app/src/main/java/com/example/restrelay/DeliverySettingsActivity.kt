package com.example.restrelay

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DeliverySettingsActivity : AppCompatActivity() {

    private lateinit var editBefore: EditText
    private lateinit var editConsecutive: EditText
    private lateinit var editRearm: EditText
    private lateinit var checkDelay: CheckBox
    private lateinit var editDelay: EditText
    private lateinit var editCooldown: EditText
    private lateinit var editArrival: EditText
    private lateinit var editRel: EditText
    private lateinit var editAbs: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delivery_settings)

        editBefore = findViewById(R.id.edit_alert_before)
        editConsecutive = findViewById(R.id.edit_consecutive)
        editRearm = findViewById(R.id.edit_rearm)
        checkDelay = findViewById(R.id.check_delay_enabled)
        editDelay = findViewById(R.id.edit_delay_threshold)
        editCooldown = findViewById(R.id.edit_cooldown)
        editArrival = findViewById(R.id.edit_arrival_keywords)
        editRel = findViewById(R.id.edit_eta_relative)
        editAbs = findViewById(R.id.edit_eta_absolute)

        editBefore.setText(Prefs.alertBeforeMin(this).toString())
        editConsecutive.setText(Prefs.consecutive(this).toString())
        editRearm.setText(Prefs.rearmMarginMin(this).toString())
        checkDelay.isChecked = Prefs.delayEnabled(this)
        editDelay.setText(Prefs.delayThresholdMin(this).toString())
        editCooldown.setText(Prefs.cooldownMin(this).toString())
        editArrival.setText(Prefs.arrivalKeywords(this).joinToString(", "))
        editRel.setText(Prefs.etaRelativeRegex(this))
        editAbs.setText(Prefs.etaAbsoluteRegex(this))

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            Prefs.setAlertBeforeMin(this, editBefore.text.toString().toIntOrNull()?.coerceIn(1, 30) ?: 2)
            Prefs.setConsecutive(this, editConsecutive.text.toString().toIntOrNull()?.coerceIn(1, 3) ?: 2)
            Prefs.setRearmMarginMin(this, editRearm.text.toString().toIntOrNull()?.coerceIn(0, 30) ?: 3)
            Prefs.setDelayEnabled(this, checkDelay.isChecked)
            Prefs.setDelayThresholdMin(this, editDelay.text.toString().toIntOrNull()?.coerceIn(1, 120) ?: 5)
            Prefs.setCooldownMin(this, editCooldown.text.toString().toIntOrNull()?.coerceIn(1, 120) ?: 10)
            Prefs.setArrivalKeywords(this, editArrival.text.toString())
            Prefs.setEtaRelativeRegex(this, editRel.text.toString())
            Prefs.setEtaAbsoluteRegex(this, editAbs.text.toString())
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
    }
}
