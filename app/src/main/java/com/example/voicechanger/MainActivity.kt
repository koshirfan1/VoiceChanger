package com.example.voicechanger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val engine = VoiceEngine()
    private var on = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply { text = "Pitch: 1.00x"; textSize = 20f }
        val status = TextView(this).apply { text = "Ready" }
        engine.onStatus = { s -> runOnUiThread { status.text = s } }

        val seek = SeekBar(this).apply {
            max = 150 // 0.5x .. 2.0x (sirf Local mode)
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    val r = 0.5f + p / 100f
                    engine.shifter.ratio = r
                    label.text = "Pitch: %.2fx".format(r)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val aiSwitch = Switch(this).apply { text = "AI voice mode (server)" }
        val url = EditText(this).apply { setText("ws://192.168.1.10:8765"); hint = "Server URL" }
        val btn = Button(this).apply { text = "Start" }

        fun preset(name: String, ratio: Float, eq: Boolean) = Button(this).apply {
            text = name
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                engine.shifter.eq = eq
                seek.progress = ((ratio - 0.5f) * 100).toInt() // slider listener ratio set karega
            }
        }
        val presets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(preset("Female", 1.30f, true))
            addView(preset("Normal", 1.00f, false))
            addView(preset("Deep", 0.78f, false))
        }

        btn.setOnClickListener {
            if (!on) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                    return@setOnClickListener
                }
                engine.start(aiSwitch.isChecked, url.text.toString().trim())
                on = true; btn.text = "Stop"
            } else { engine.stop(); on = false; btn.text = "Start"; status.text = "Stopped" }
        }
        val note = TextView(this).apply { text = "Headphones lagayen, warna feedback (seeti) aayegi." }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(label); addView(seek); addView(presets); addView(aiSwitch); addView(url)
            addView(btn); addView(status); addView(note)
        })
    }

    override fun onDestroy() { engine.stop(); super.onDestroy() }
}
