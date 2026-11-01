package com.example.restrelay

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AppearanceActivity : AppCompatActivity() {

    private var type = AlertType.REST_OVER
    private var selectedColor = Prefs.DEFAULT_COLOR
    private var selectedIcon = "ic_bell"
    private var soundUri = ""

    private lateinit var typeSpinner: Spinner
    private lateinit var editTitle: EditText
    private lateinit var editBody: EditText
    private lateinit var vibSpinner: Spinner
    private lateinit var editCustomVib: EditText
    private lateinit var soundSpinner: Spinner
    private lateinit var timeoutSpinner: Spinner
    private lateinit var checkBigText: CheckBox
    private lateinit var colorRow: LinearLayout
    private lateinit var iconRow: LinearLayout

    private val TIMEOUTS = longArrayOf(0, 3000, 5000, 8000, 15000, 30000, 60000, 300000)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appearance)

        typeSpinner = findViewById(R.id.spinner_type)
        editTitle = findViewById(R.id.edit_title)
        editBody = findViewById(R.id.edit_body)
        vibSpinner = findViewById(R.id.spinner_vibration)
        editCustomVib = findViewById(R.id.edit_custom_vibration)
        soundSpinner = findViewById(R.id.spinner_sound)
        timeoutSpinner = findViewById(R.id.spinner_timeout)
        checkBigText = findViewById(R.id.check_bigtext)
        colorRow = findViewById(R.id.color_row)
        iconRow = findViewById(R.id.icon_row)

        typeSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, AlertType.entries.map { it.label }
        )
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                type = AlertType.entries[pos]
                load()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        vibSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, VibrationPreset.entries.map { it.label }
        )
        soundSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.sound_none), getString(R.string.sound_default), getString(R.string.sound_custom))
        )
        timeoutSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Never", "3 s", "5 s", "8 s", "15 s", "30 s", "1 min", "5 min")
        )

        findViewById<Button>(R.id.btn_test_vibration).setOnClickListener {
            Vibration.vibrate(this, Vibration.pattern(readAppearance()))
        }
        findViewById<Button>(R.id.btn_pick_sound).setOnClickListener { pickSound() }
        findViewById<Button>(R.id.btn_test_alert).setOnClickListener {
            AlertNotifier.post(this, type, readAppearance(), samplePlaceholders())
        }
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            Prefs.setAppearance(this, type, readAppearance())
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_reset).setOnClickListener {
            Prefs.setAppearance(this, type, Prefs.defaultAppearance(type))
            load()
        }

        load()
    }

    private fun load() {
        val a = Prefs.appearance(this, type)
        editTitle.setText(a.title)
        editBody.setText(a.body)
        vibSpinner.setSelection(a.vibration.ordinal)
        editCustomVib.setText(a.customVibration)
        soundSpinner.setSelection(a.sound.ordinal)
        soundUri = a.soundUri
        timeoutSpinner.setSelection(TIMEOUTS.indexOf(a.timeoutMs).coerceAtLeast(0))
        checkBigText.isChecked = a.bigText
        selectedColor = a.color
        selectedIcon = a.icon
        setupColorRow()
        setupIconRow()
    }

    private fun readAppearance(): AlertAppearance = AlertAppearance(
        title = editTitle.text.toString(),
        body = editBody.text.toString(),
        vibration = VibrationPreset.entries[vibSpinner.selectedItemPosition],
        customVibration = editCustomVib.text.toString(),
        sound = SoundType.entries[soundSpinner.selectedItemPosition],
        soundUri = soundUri,
        color = selectedColor,
        icon = selectedIcon,
        timeoutMs = TIMEOUTS[timeoutSpinner.selectedItemPosition],
        bigText = checkBigText.isChecked,
    )

    private fun samplePlaceholders() = mapOf("app" to "Test App", "eta" to "5", "exercise" to "Bench Press")

    private fun setupColorRow() {
        colorRow.removeAllViews()
        for (c in COLOR_SWATCHES) {
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) }
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                if (c == selectedColor) setStroke(dp(3), 0xFFFFFFFF.toInt())
            }
            v.setOnClickListener { selectedColor = c; setupColorRow() }
            colorRow.addView(v)
        }
    }

    private fun setupIconRow() {
        iconRow.removeAllViews()
        for (name in ICON_NAMES) {
            val res = resources.getIdentifier(name, "drawable", packageName)
            val b = ImageButton(this)
            b.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) }
            b.setImageResource(res)
            b.setColorFilter(0xFFFFFFFF.toInt())
            b.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0xFF37474F.toInt())
                if (name == selectedIcon) setStroke(dp(3), 0xFF00B0FF.toInt())
            }
            b.setOnClickListener { selectedIcon = name; setupIconRow() }
            iconRow.addView(b)
        }
    }

    private fun pickSound() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.sound_pick_title))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        }
        startActivityForResult(intent, REQ_SOUND)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SOUND && resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
            if (uri != null) {
                soundUri = uri.toString()
                soundSpinner.setSelection(SoundType.CUSTOM.ordinal)
            } else {
                soundUri = ""
                soundSpinner.setSelection(SoundType.NONE.ordinal)
            }
        }
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_SOUND = 42
    }
}
