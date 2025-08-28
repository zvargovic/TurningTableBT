package com.pravimputem.okretnistol

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IntroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        val tvTitle: TextView = findViewById(R.id.tvIntroTitle)
        val tvWelcome: TextView = findViewById(R.id.tvWelcome)
        val tvConnect: TextView = findViewById(R.id.tvConnect)
        val tvControl: TextView = findViewById(R.id.tvControl)
        val btnStart: Button = findViewById(R.id.btnStart)

        // texts via resources (also set in XML, this just guarantees localization)
        tvTitle.text = getString(R.string.intro_title)
        tvWelcome.text = getString(R.string.intro_welcome)
        tvConnect.text = getString(R.string.intro_connect)
        tvControl.text = getString(R.string.intro_control)
        btnStart.text = getString(R.string.intro_open_controls)

        btnStart.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}