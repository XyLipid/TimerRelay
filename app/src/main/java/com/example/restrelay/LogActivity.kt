package com.example.restrelay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val list: ListView = findViewById(R.id.log_list)
        list.adapter = LogAdapter()

        findViewById<Button>(R.id.btn_refresh).setOnClickListener { refresh() }
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            LogStore.clear()
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val list: ListView = findViewById(R.id.log_list)
        (list.adapter as? LogAdapter)?.notifyDataSetChanged()
    }

    private inner class LogAdapter : BaseAdapter() {
        override fun getCount(): Int = LogStore.all().size
        override fun getItem(position: Int): Any = LogStore.all()[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View = convertView ?: LayoutInflater.from(this@LogActivity)
                .inflate(R.layout.item_log, parent, false)
            val tv = view.findViewById<TextView>(R.id.log_text)
            tv.text = LogStore.all()[position].toText()
            return view
        }
    }
}
