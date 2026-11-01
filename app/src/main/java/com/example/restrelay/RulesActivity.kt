package com.example.restrelay

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class RulesActivity : AppCompatActivity() {

    private data class AppInfo(val label: String, val pkg: String)

    private lateinit var list: ListView
    private var rules: MutableList<Rule> = mutableListOf()
    private lateinit var appInfos: List<AppInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rules)

        list = findViewById(R.id.rule_list)
        appInfos = loadLaunchableApps()

        findViewById<Button>(R.id.btn_add_rule).setOnClickListener { showRuleDialog(null) }
        list.setOnItemClickListener { _, _, pos, _ -> showRuleDialog(rules[pos]) }
        list.setOnItemLongClickListener { _, _, pos, _ -> confirmDelete(pos); true }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        rules = Prefs.rules(this).toMutableList()
        list.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, rules.map { describe(it) }
        )
    }

    private fun describe(r: Rule): String {
        val label = appInfos.firstOrNull { it.pkg == r.pkg }?.label ?: r.pkg
        val det = if (r.type == RuleType.REST_TIMER) " · ${r.detection.label}" else ""
        return "$label — ${r.type.label}$det"
    }

    private fun confirmDelete(pos: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.rule_delete_title)
            .setMessage(describe(rules[pos]))
            .setPositiveButton(R.string.delete) { _, _ ->
                rules.removeAt(pos)
                Prefs.setRules(this, rules)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRuleDialog(existing: Rule?) {
        val view = layoutInflater.inflate(R.layout.dialog_rule, null)
        val appSpinner = view.findViewById<Spinner>(R.id.dialog_app)
        val typeGroup = view.findViewById<RadioGroup>(R.id.dialog_type)
        val detSpinner = view.findViewById<Spinner>(R.id.dialog_detection)
        val keywords = view.findViewById<EditText>(R.id.dialog_keywords)

        appSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, appInfos.map { it.label }
        )
        detSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            RestDetection.entries.map { it.label }
        )

        fun isRest() = typeGroup.checkedRadioButtonId == R.id.dialog_radio_rest
        fun sync() {
            val rest = isRest()
            detSpinner.isEnabled = rest
            keywords.isEnabled = rest
        }

        if (existing != null) {
            appSpinner.setSelection(appInfos.indexOfFirst { it.pkg == existing.pkg }.coerceAtLeast(0))
            typeGroup.check(if (existing.type == RuleType.DELIVERY) R.id.dialog_radio_delivery else R.id.dialog_radio_rest)
            detSpinner.setSelection(RestDetection.entries.indexOf(existing.detection).coerceAtLeast(0))
            keywords.setText(existing.keywords)
        }
        typeGroup.setOnCheckedChangeListener { _, _ -> sync() }
        sync()

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.rule_add_title else R.string.rule_edit_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val pkg = appInfos[appSpinner.selectedItemPosition].pkg
                val type = if (isRest()) RuleType.REST_TIMER else RuleType.DELIVERY
                val det = if (type == RuleType.REST_TIMER)
                    RestDetection.entries[detSpinner.selectedItemPosition]
                else RestDetection.REMOVED
                val rule = Rule(
                    existing?.id ?: UUID.randomUUID().toString(),
                    pkg, type, det, keywords.text.toString().trim(),
                )
                if (existing != null) {
                    val i = rules.indexOfFirst { it.id == existing.id }
                    if (i >= 0) rules[i] = rule
                } else {
                    rules.add(rule)
                }
                Prefs.setRules(this, rules)
                reload()
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadLaunchableApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
}
