package com.pravimputem.okretnistol

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {

    private lateinit var tv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        tv = findViewById(R.id.tvLog)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val lines = LogBus.all()
        tv.text = if (lines.isEmpty()) "No log available" else lines.joinToString("\n")
    }
}