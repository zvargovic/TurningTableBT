package com.pravimputem.okretnistol

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import java.util.Locale
import android.content.res.ColorStateList

class MainActivity : AppCompatActivity() {

    // --- BT helper + prefs ---
    private val bt by lazy { BluetoothHelper(this) }
    private val prefs by lazy { getSharedPreferences("okretni_prefs", Context.MODE_PRIVATE) }
    private val PREF_LAST = "last_device_name"
    private val DEFAULT_DEVICE = "TurnTable"
    private var lastAttemptedDeviceName: String? = null

    // --- UI ---
    private lateinit var imgCover: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvSpeedInfoValue: TextView
    private lateinit var tvDirectionInfoValue: TextView
    private lateinit var tvSpeedValue: TextView

    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnCW: ImageButton
    private lateinit var btnCCW: ImageButton
    private lateinit var btnRepeatToggle: ImageButton
    private lateinit var btnLogView: ImageButton
    private lateinit var sliderSpeed: Slider
    private lateinit var btnSelfTest: Button

    // --- state ---
    private var isPlaying = false
    private var modeIsC = true
    private var controlsLocked = false

    // anim webp (samo na API 28+ će biti ne-null)
    private var coverAnim: AnimatedImageDrawable? = null

    // zadnji STATUS timestamp
    private var lastStatusMillis: Long = 0

    // --- polling ---
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (bt.isConnected()) {
                bt.sendLine("STATUS?")
                pollHandler.postDelayed(this, 2000)
            }
        }
    }
    private fun startPolling() {
        pollHandler.removeCallbacks(pollRunnable)
        pollHandler.postDelayed(pollRunnable, 500)
    }
    private fun stopPolling() = pollHandler.removeCallbacks(pollRunnable)

    private fun requestStatus() { if (bt.isConnected()) bt.sendLine("STATUS?") }

    private fun forceResyncSoon() {
        if (!bt.isConnected()) return
        val h = Handler(Looper.getMainLooper())
        listOf(200L, 700L, 1500L).forEach { d ->
            h.postDelayed({ if (bt.isConnected()) bt.sendLine("STATUS?") }, d)
        }
    }

    // --- runtime BT dozvole (samo 31+) ---
    private val btPerms = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    @SuppressLint("InlinedApi")
    private fun ensureBtPerms() {
        if (Build.VERSION.SDK_INT >= 31) {
            val need = btPerms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (need) requestPermissions(btPerms, 1001)
        }
    }

    // --- lifecycle ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ensureBtPerms()
        bindViews()
        styleSliderBlue()
        wireBtCallbacks()

        updatePlayUi(isPlaying)
        setControlsLocked(false)
        tvSpeedValue.text = String.format(Locale.US, "%.0f RPM", sliderSpeed.value)
        tvSpeedInfoValue.text = String.format(Locale.US, "%.0f RPM", sliderSpeed.value)
        updateConnectionTitle()

        showStaticCover()
        attemptAutoConnect()

        // --- Gumbi ---
        btnCW.setOnClickListener {
            if (guardLocked()) return@setOnClickListener
            log("UI: CW")
            send("SetDirectionCW")
            tvDirectionInfoValue.text = "CW"
        }
        btnCCW.setOnClickListener {
            if (guardLocked()) return@setOnClickListener
            log("UI: CCW")
            send("SetDirectionCCW")
            tvDirectionInfoValue.text = "CCW"
        }
        btnRepeatToggle.setOnClickListener {
            if (guardLocked()) return@setOnClickListener
            modeIsC = !modeIsC
            val cmd = if (modeIsC) "ModeRunC" else "ModeRunT"
            log("UI: Toggle mode -> ${if (modeIsC) "C" else "T"}")
            send(cmd)
        }
        btnPlayPause.setOnClickListener {
            // Play/Stop uvijek dopušten
            isPlaying = !isPlaying
            updatePlayUi(isPlaying)
            val cmd = if (isPlaying) "Play" else "Stop"
            log("UI: $cmd")
            send(cmd)
            setControlsLocked(isPlaying)
        }
        btnLogView.setOnClickListener {
            log("UI: Open log")
            startActivity(Intent(this, LogActivity::class.java))
        }
        btnSelfTest.setOnClickListener {
            log("UI: SelfTest")
            send("SelfTest")
            setControlsLocked(true) // zaključaj dok STATUS ne vrati kontrolu
        }

        // Slider
        sliderSpeed.addOnChangeListener { _, v, fromUser ->
            val txt = String.format(Locale.US, "%.0f RPM", v)
            tvSpeedValue.text = txt
            if (fromUser) tvSpeedInfoValue.text = txt
        }
        sliderSpeed.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                if (controlsLocked) toast(getString(R.string.locked_while_running))
            }
            override fun onStopTrackingTouch(slider: Slider) {
                if (controlsLocked) return
                val cmd = String.format(Locale.US, "SetSpeed%.1f", slider.value)
                log("UI: $cmd")
                send(cmd)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // API 28 guard za start animacije
        if (Build.VERSION.SDK_INT >= 28) {
            (coverAnim as? AnimatedImageDrawable)?.apply {
                setVisible(true, false)
                start()
            }
        }
        if (bt.isConnected() && System.currentTimeMillis() - lastStatusMillis > 3000) {
            requestStatus()
        }
    }

    override fun onPause() {
        // API 28 guard za stop animacije
        if (Build.VERSION.SDK_INT >= 28) {
            (coverAnim as? AnimatedImageDrawable)?.stop()
        }
        super.onPause()
    }

    // --- autoconnect ---
    private fun attemptAutoConnect() {
        val last = prefs.getString(PREF_LAST, null)
        if (!last.isNullOrBlank()) {
            tvTitle.text = getString(R.string.title_connecting_to, last)
            lastAttemptedDeviceName = last
            bt.connectToBondedByName(last) { ok, _ ->
                runOnUiThread { if (ok) onConnectedActions() else tryDefaultNameElsePrompt() }
            }
        } else {
            tryDefaultNameElsePrompt()
        }
    }

    private fun tryDefaultNameElsePrompt() {
        lastAttemptedDeviceName = DEFAULT_DEVICE
        tvTitle.text = getString(R.string.title_connecting_to, DEFAULT_DEVICE)
        bt.connectToBondedByName(DEFAULT_DEVICE) { ok, _ ->
            runOnUiThread { if (ok) onConnectedActions() else maybePromptConnect() }
        }
    }

    private fun onConnectedActions() {
        tvTitle.text = getString(R.string.title_connected)
        lastAttemptedDeviceName?.let { prefs.edit().putString(PREF_LAST, it).apply() }
        forceResyncSoon()
        startPolling()
    }

    // --- BT callbacks ---
    private fun wireBtCallbacks() {
        bt.onConnected = {
            runOnUiThread {
                tvTitle.text = getString(R.string.title_connected)
                lastAttemptedDeviceName?.let { prefs.edit().putString(PREF_LAST, it).apply() }
                forceResyncSoon()
                startPolling()
            }
        }
        bt.onDisconnected = {
            runOnUiThread {
                tvTitle.text = getString(R.string.title_not_connected)
                stopPolling()
            }
        }
        bt.onLineReceived = { raw ->
            val line = raw.trim()
            log("IN: $line")
            val norm = line.removePrefix("<").trimStart()
            if (norm.uppercase(Locale.US).startsWith("STATUS")) parseStatus(norm)
        }
        bt.onLineSent = { s -> log("OUT: $s") }
        bt.onError = { msg -> runOnUiThread { toast(msg) } }
    }

    // --- STATUS parser
    private fun parseStatus(s: String) {
        lastStatusMillis = System.currentTimeMillis()

        val sp  = Regex("""Speed:([0-9.]+)""").find(s)?.groupValues?.get(1)?.toFloatOrNull()
        val dir = Regex("""Dir:(CW|CCW)""").find(s)?.groupValues?.get(1)
        val mode = Regex("""Mode:(C|T)""").find(s)?.groupValues?.get(1)
        val running = Regex("""Running:(0|1)""").find(s)?.groupValues?.get(1)

        runOnUiThread {
            sp?.let {
                val txt = String.format(Locale.US, "%.0f RPM", it)
                tvSpeedInfoValue.text = txt
                tvSpeedValue.text = txt
                if (it in sliderSpeed.valueFrom..sliderSpeed.valueTo && !sliderSpeed.isPressed) {
                    sliderSpeed.value = it
                }
            }
            dir?.let { tvDirectionInfoValue.text = it }
            mode?.let { modeIsC = (it == "C") }

            running?.let {
                val playing = it == "1"
                if (playing != isPlaying) {
                    isPlaying = playing
                    updatePlayUi(isPlaying)
                }
                setControlsLocked(playing)
            }
        }
    }

    // --- izbor uparenog uređaja ---
    private fun maybePromptConnect() {
        val names = bt.listBondedDeviceNames()
        if (names.isEmpty()) {
            toast(getString(R.string.no_paired_devices))
            return
        }
        val arr: Array<CharSequence> = names.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_paired_device))
            .setItems(arr) { _, which ->
                val sel = arr[which].toString()
                log("UI: connect to $sel")
                lastAttemptedDeviceName = sel
                tvTitle.text = getString(R.string.title_connecting_to, sel)
                bt.connectToBondedByName(sel) { ok, msg ->
                    runOnUiThread {
                        if (ok) onConnectedActions()
                        else {
                            tvTitle.text = getString(R.string.title_not_connected)
                            toast(msg)
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // --- zaključavanje kontrola ---
    private fun setControlsLocked(locked: Boolean) {
        controlsLocked = locked

        btnCW.isEnabled = !locked
        btnCCW.isEnabled = !locked
        btnRepeatToggle.isEnabled = !locked
        sliderSpeed.isEnabled = !locked
        btnSelfTest.isEnabled = !locked

        val alpha = if (locked) 0.4f else 1.0f
        btnCW.alpha = alpha
        btnCCW.alpha = alpha
        btnRepeatToggle.alpha = alpha
        sliderSpeed.alpha = if (locked) 0.7f else 1f
        btnSelfTest.alpha = alpha

        if (locked) showAnimatedCover() else showStaticCover()
    }

    private fun guardLocked(): Boolean {
        if (controlsLocked) {
            toast(getString(R.string.locked_while_running))
            return true
        }
        return false
    }

    // --- UI helpers ---
    private fun bindViews() {
        imgCover = findViewById(R.id.imgCover)
        tvTitle = findViewById(R.id.tvTitle)
        tvSpeedInfoValue = findViewById(R.id.tvSpeedInfoValue)
        tvDirectionInfoValue = findViewById(R.id.tvDirectionInfoValue)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)

        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnCW = findViewById(R.id.btnCW)
        btnCCW = findViewById(R.id.btnCCW)
        btnRepeatToggle = findViewById(R.id.btnRepeatToggle)
        btnLogView = findViewById(R.id.btnLogView)
        sliderSpeed = findViewById(R.id.sliderSpeed)
        btnSelfTest = findViewById(R.id.btnSelfTest)
    }

    private fun styleSliderBlue() {
        val blue = Color.parseColor("#00BFFF")
        val blueDisabled = Color.parseColor("#5500BFFF")
        val gray = Color.parseColor("#444444")
        val grayDisabled = Color.parseColor("#33444444")

        val thumbStates = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            ),
            intArrayOf(blueDisabled, blue, blue)
        )
        val activeStates = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(blueDisabled, blue)
        )
        val inactiveStates = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(grayDisabled, gray)
        )
        val halo = ColorStateList.valueOf(Color.parseColor("#3300BFFF"))

        sliderSpeed.valueFrom = 1f
        sliderSpeed.valueTo   = 5f
        sliderSpeed.stepSize  = 1f
        if (sliderSpeed.value < 1f || sliderSpeed.value > 5f) sliderSpeed.value = 1f
        sliderSpeed.thumbTintList = thumbStates
        sliderSpeed.setTrackActiveTintList(activeStates)
        sliderSpeed.setTrackInactiveTintList(inactiveStates)
        sliderSpeed.setTickActiveTintList(activeStates)
        sliderSpeed.setTickInactiveTintList(inactiveStates)
        sliderSpeed.haloTintList = halo
    }

    private fun updatePlayUi(playing: Boolean) {
        btnPlayPause.setBackgroundResource(
            if (playing) R.drawable.bg_circle_play else R.drawable.bg_circle_neutral
        )
        btnPlayPause.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        if (playing) showAnimatedCover() else showStaticCover()
    }

    private fun updateConnectionTitle() {
        tvTitle.text = if (bt.isConnected())
            getString(R.string.title_connected)
        else
            getString(R.string.title_not_connected)
    }

    private fun send(cmd: String) {
        if (!bt.isConnected()) { toast(getString(R.string.not_connected)); return }
        bt.sendLine(cmd) { ok, msg -> if (!ok) runOnUiThread { toast(msg) } }
    }

    private fun log(s: String) { try { LogBus.add(s) } catch (_: Throwable) {} }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        // API 28 guard za stop animacije prije gašenja
        if (Build.VERSION.SDK_INT >= 28) {
            (coverAnim as? AnimatedImageDrawable)?.stop()
        }
        stopPolling()
        bt.close()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                toast(getString(R.string.bt_perm_granted))
                attemptAutoConnect()
            } else {
                toast(getString(R.string.bt_perm_required))
            }
        }
    }

    // --- cover helpers ---
    private fun showStaticCover() {
        // API 28 guard
        if (Build.VERSION.SDK_INT >= 28) {
            (coverAnim as? AnimatedImageDrawable)?.stop()
        }
        coverAnim = null
        imgCover.setImageResource(R.drawable.ttoled)
    }

    private fun showAnimatedCover() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val src = ImageDecoder.createSource(resources, R.drawable.turntable)
                val dr = ImageDecoder.decodeDrawable(src) { dec, _, _ ->
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    dec.isMutableRequired = false
                }
                if (dr is AnimatedImageDrawable) {
                    // ugasi staru ako postoji
                    (coverAnim as? AnimatedImageDrawable)?.stop()
                    coverAnim = dr
                    dr.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    imgCover.setImageDrawable(dr)
                    imgCover.post {
                        dr.setVisible(true, true)
                        dr.start()
                    }
                    return
                }
            } catch (_: Exception) { /* fallback niže */ }
        }
        showStaticCover()
    }
}