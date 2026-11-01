package com.dbrush.jacobstv

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    // Greet when the set comes back after this long. 0 replays on every return home.
    private val welcomeAfterMs = 4 * 60 * 1000L
    private val dreamAfterMs = 3 * 60 * 1000L

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("jacobstv", MODE_PRIVATE) }
    private lateinit var welcome: View
    private lateinit var about: View
    private lateinit var library: View
    private lateinit var menu: View
    private lateinit var dream: View
    private lateinit var dreamClock: View
    private lateinit var dock: LinearLayout
    private lateinit var grid: GridView
    private lateinit var shelfKicker: TextView
    private lateinit var shelfName: TextView
    private lateinit var stats: TextView
    private lateinit var weatherView: TextView
    private lateinit var outlookView: TextView
    private lateinit var wordWelcome: TextView
    private lateinit var wordJacob: TextView
    private lateinit var wordStatus: TextView
    private lateinit var closeButton: View
    private lateinit var menuTitle: TextView
    private lateinit var menuMoveLeft: View
    private lateinit var menuMoveRight: View
    private lateinit var menuRemove: View
    private lateinit var menuPin: View
    private lateinit var libraryAdapter: AppsAdapter
    private lateinit var wallpaper: ImageView
    private lateinit var debloat: View
    private lateinit var control: View
    private lateinit var debloatBody: TextView
    private var voice: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeak = false
    private var closingWelcome = false
    private var loadGen = 0
    private var apps: List<AppEntry> = emptyList()
    private var dockPkgs: MutableList<String> = mutableListOf()
    private var menuEntry: AppEntry? = null
    private var menuOnDock = false

    private val endWelcome = Runnable { finishWelcome() }
    private val showDream = Runnable { showScreensaver() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        welcome = findViewById(R.id.welcome)
        about = findViewById(R.id.about)
        library = findViewById(R.id.library)
        menu = findViewById(R.id.menu)
        debloat = findViewById(R.id.debloat)
        control = findViewById(R.id.control)
        debloatBody = findViewById(R.id.debloatBody)
        wallpaper = findViewById(R.id.wallpaper)
        dream = findViewById(R.id.dream)
        dreamClock = findViewById(R.id.dreamClock)
        dock = findViewById(R.id.dock)
        grid = findViewById(R.id.libraryGrid)
        shelfKicker = findViewById(R.id.shelfKicker)
        shelfName = findViewById(R.id.shelfName)
        stats = findViewById(R.id.stats)
        weatherView = findViewById(R.id.weather)
        outlookView = findViewById(R.id.outlook)
        wordWelcome = findViewById(R.id.wordWelcome)
        wordJacob = findViewById(R.id.wordJacob)
        wordStatus = findViewById(R.id.wordStatus)
        closeButton = findViewById(R.id.closeAbout)
        menuTitle = findViewById(R.id.menuTitle)
        menuMoveLeft = findViewById(R.id.menuMoveLeft)
        menuMoveRight = findViewById(R.id.menuMoveRight)
        menuRemove = findViewById(R.id.menuRemove)
        menuPin = findViewById(R.id.menuPin)
        libraryAdapter = AppsAdapter({ open(it) }, { showMenu(it, onDock = false) })
        grid.adapter = libraryAdapter
        grid.itemsCanFocus = true
        grid.setOnItemClickListener { _, _, position, _ ->
            open(libraryAdapter.getItem(position))
        }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            showMenu(libraryAdapter.getItem(position), onDock = false)
            true
        }
        findViewById<View>(R.id.replayWelcome).setOnClickListener { playWelcome() }
        closeButton.setOnClickListener { hideAbout() }
        about.setOnClickListener { hideAbout() }
        findViewById<View>(R.id.aboutCard).setOnClickListener { }
        welcome.setOnClickListener { finishWelcome() }
        dream.setOnClickListener { hideScreensaver() }
        findViewById<View>(R.id.debloatRun).setOnClickListener { tryDebloat() }
        findViewById<View>(R.id.debloatRestore).setOnClickListener { tryRestore() }
        findViewById<View>(R.id.debloatClose).setOnClickListener { hideDebloat() }
        debloat.setOnClickListener { hideDebloat() }
        findViewById<View>(R.id.clockHit).setOnClickListener { expand(control); findViewById<View>(R.id.controlInputs).requestFocus() }
        findViewById<View>(R.id.controlInputs).setOnClickListener { control.visibility = View.GONE; showInputEditor() }
        findViewById<View>(R.id.controlLink).setOnClickListener { control.visibility = View.GONE; openWifi() }
        findViewById<View>(R.id.controlSettings).setOnClickListener { control.visibility = View.GONE; showAbout() }
        findViewById<View>(R.id.controlCast).setOnClickListener { control.visibility = View.GONE; openCast() }
        findViewById<View>(R.id.controlWall).setOnClickListener { control.visibility = View.GONE; cycleWall() }
        findViewById<View>(R.id.controlDebloat).setOnClickListener { control.visibility = View.GONE; showDebloat() }
        val zipField = findViewById<EditText>(R.id.zipField)
        zipField.setText(prefs.getString(KEY_ZIP, "45414"))
        findViewById<View>(R.id.zipSave).setOnClickListener {
            val typed = zipField.text?.toString().orEmpty()
            val next = if (typed.length == 5 && typed.all { it.isDigit() }) typed else "45414"
            prefs.edit().putString(KEY_ZIP, next).apply()
            zipField.setText(next)
            refreshWeather()
        }
        findViewById<View>(R.id.controlCard).setOnClickListener { }
        control.setOnClickListener { control.visibility = View.GONE }
        applyWall(prefs.getString(KEY_WALL, "rocket") ?: "rocket")
        menuMoveLeft.setOnClickListener { nudgeMenu(-1) }
        menuMoveRight.setOnClickListener { nudgeMenu(1) }
        menuRemove.setOnClickListener { menuEntry?.let { unpin(dockKey(it)) } }
        menuPin.setOnClickListener { menuEntry?.let { pin(dockKey(it)) } }
        findViewById<View>(R.id.menuInfo).setOnClickListener { menuEntry?.let { openInfo(it) } }
        findViewById<View>(R.id.menuClose).setOnClickListener { hideMenu() }
        menu.setOnClickListener { hideMenu() }
        tts = TextToSpeech(this, this)
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        refreshApps()
        if (welcome.visibility != View.VISIBLE && shouldGreet()) playWelcome()
        bumpIdle()
    }

    override fun onPause() {
        handler.removeCallbacks(showDream)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        voice?.release()
        voice = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) bumpIdle()
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val back = event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (dream.visibility == View.VISIBLE) {
            hideScreensaver()
            return true
        }
        if (welcome.visibility == View.VISIBLE && (ok || back)) {
            finishWelcome()
            return true
        }
        if (about.visibility == View.VISIBLE && back) {
            hideAbout()
            return true
        }
        if (menu.visibility == View.VISIBLE && back) {
            hideMenu()
            return true
        }
        if (debloat.visibility == View.VISIBLE && back) {
            hideDebloat()
            return true
        }
        if (control.visibility == View.VISIBLE && back) {
            control.visibility = View.GONE
            focusDock()
            return true
        }
        if (library.visibility == View.VISIBLE && back) {
            hideLibrary()
            return true
        }
        if (clearHome() && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && dock.hasFocus()) {
            showLibrary()
            return true
        }
        if (clearHome() && event.keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            openInputs()
            return true
        }
        if (clearHome() && event.keyCode == KeyEvent.KEYCODE_SETTINGS) {
            openSettings()
            return true
        }
        if (clearHome() && event.keyCode == KeyEvent.KEYCODE_BUTTON_3) {
            launchPackage(YOUTUBE)
            return true
        }
        if (back) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        bumpIdle()
        if (dream.visibility == View.VISIBLE) {
            hideScreensaver()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts?.language = Locale.US
        ttsReady = true
        if (pendingSpeak && welcome.visibility == View.VISIBLE) {
            pendingSpeak = false
            speak()
        }
    }

    private fun clearHome(): Boolean {
        return welcome.visibility != View.VISIBLE &&
            about.visibility != View.VISIBLE &&
            menu.visibility != View.VISIBLE &&
            debloat.visibility != View.VISIBLE &&
            library.visibility != View.VISIBLE &&
            control.visibility != View.VISIBLE &&
            dream.visibility != View.VISIBLE
    }

    private fun shouldGreet(): Boolean {
        val seen = prefs.getLong(KEY_SEEN, 0L)
        return System.currentTimeMillis() - seen > welcomeAfterMs
    }

    private fun markSeen() {
        prefs.edit().putLong(KEY_SEEN, System.currentTimeMillis()).apply()
    }

    private fun playWelcome() {
        closingWelcome = false
        handler.removeCallbacks(endWelcome)
        handler.removeCallbacks(showDream)
        markSeen()
        about.visibility = View.GONE
        menu.visibility = View.GONE
        debloat.visibility = View.GONE
        library.visibility = View.GONE
        dream.visibility = View.GONE
        welcome.visibility = View.VISIBLE
        welcome.alpha = 0f
        welcome.animate().alpha(1f).setDuration(220).start()
        wordWelcome.alpha = 0f
        wordJacob.alpha = 0f
        wordJacob.translationY = 28f
        wordStatus.alpha = 0f
        wordWelcome.animate().alpha(1f).setStartDelay(360).setDuration(420).start()
        wordJacob.animate().alpha(1f).translationY(0f).setStartDelay(780).setDuration(520).start()
        wordStatus.animate().alpha(1f).setStartDelay(1280).setDuration(380).start()
        speak()
        handler.postDelayed(endWelcome, 4300)
    }

    private fun speak() {
        if (playVoice()) return
        if (ttsReady) tts?.speak(getString(R.string.tts_welcome), TextToSpeech.QUEUE_FLUSH, null, "welcome")
        else pendingSpeak = true
    }

    private fun playVoice(): Boolean {
        chirp()
        voice?.release()
        val player = MediaPlayer.create(this, R.raw.welcome) ?: return false
        voice = player
        player.setOnCompletionListener {
            if (voice == it) voice = null
            it.release()
        }
        player.start()
        return true
    }

    private fun chirp() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 160)
            handler.postDelayed({
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                handler.postDelayed({ tone.release() }, 200)
            }, 180)
        }
    }

    private fun stopVoice() {
        runCatching { voice?.stop() }
        voice?.release()
        voice = null
        tts?.stop()
    }

    private fun finishWelcome() {
        if (welcome.visibility != View.VISIBLE || closingWelcome) return
        closingWelcome = true
        handler.removeCallbacks(endWelcome)
        pendingSpeak = false
        stopVoice()
        welcome.animate().alpha(0f).setDuration(280).withEndAction {
            welcome.visibility = View.GONE
            welcome.alpha = 1f
            closingWelcome = false
            focusDock()
            bumpIdle()
        }.start()
    }

    private fun showAbout() {
        expand(about)
        closeButton.requestFocus()
    }

    private fun hideAbout() {
        about.visibility = View.GONE
        focusDock()
    }

    private fun showLibrary() {
        library.visibility = View.VISIBLE
        library.translationY = 80f
        library.animate().translationY(0f).setDuration(220).start()
        grid.setSelection(0)
        grid.post {
            val first = grid.getChildAt(0)
            if (first != null) first.requestFocus() else grid.requestFocus()
        }
        showShelf(getString(R.string.all_apps), getString(R.string.all_apps))
    }

    private fun hideLibrary() {
        library.visibility = View.GONE
        focusDock()
    }

    private fun showScreensaver() {
        if (!clearHome() && welcome.visibility != View.VISIBLE) return
        if (welcome.visibility == View.VISIBLE) return
        dream.visibility = View.VISIBLE
        dream.requestFocus()
        dreamClock.animate().translationX(160f).translationY(-90f).setDuration(14000).withEndAction {
            if (dream.visibility == View.VISIBLE) {
                dreamClock.animate().translationX(-140f).translationY(70f).setDuration(14000).start()
            }
        }.start()
    }

    private fun hideScreensaver() {
        dreamClock.animate().cancel()
        dreamClock.translationX = 0f
        dreamClock.translationY = 0f
        dream.visibility = View.GONE
        bumpIdle()
    }

    private fun bumpIdle() {
        handler.removeCallbacks(showDream)
        if (dream.visibility == View.VISIBLE || welcome.visibility == View.VISIBLE) return
        handler.postDelayed(showDream, dreamAfterMs)
    }

    private fun showMenu(entry: AppEntry, onDock: Boolean) {
        menuEntry = entry
        menuOnDock = onDock
        menuTitle.text = entry.label.uppercase(Locale.US)
        val dockActions = if (onDock) View.VISIBLE else View.GONE
        menuMoveLeft.visibility = dockActions
        menuMoveRight.visibility = dockActions
        menuRemove.visibility = dockActions
        menuPin.visibility = if (onDock) View.GONE else View.VISIBLE
        menu.visibility = View.VISIBLE
        expand(menu)
        (if (onDock) menuMoveLeft else menuPin).requestFocus()
    }

    private fun hideMenu() {
        menu.visibility = View.GONE
        menuEntry = null
        if (library.visibility == View.VISIBLE) grid.requestFocus() else focusDock()
    }

    private fun dockKey(entry: AppEntry): String {
        if (entry.kind == TileKind.BROWSER) return "app:browser"
        return entry.component?.packageName ?: ""
    }

    private fun nudgeMenu(dir: Int) {
        val pkg = menuEntry?.let { dockKey(it) } ?: return
        val index = dockPkgs.indexOf(pkg)
        val next = index + dir
        if (index < 0 || next < 0 || next >= dockPkgs.size) return
        dockPkgs.removeAt(index)
        dockPkgs.add(next, pkg)
        saveDock()
        renderDock()
        hideMenu()
        showShelf(menuTitle.text.toString(), getString(R.string.moving))
    }

    private fun pin(pkg: String) {
        if (pkg in dockPkgs || dockPkgs.size >= 8) {
            hideMenu()
            return
        }
        dockPkgs.add(pkg)
        saveDock()
        renderDock()
        hideMenu()
        hideLibrary()
    }

    private fun unpin(pkg: String) {
        dockPkgs.remove(pkg)
        saveDock()
        renderDock()
        hideMenu()
    }

    private fun open(entry: AppEntry) {
        when (entry.kind) {
            TileKind.ABOUT -> showAbout()
            TileKind.SETTINGS -> openSettings()
            TileKind.APP -> launch(entry.component)
            TileKind.BROWSER -> openBrowser()
        }
    }

    private fun openBrowser() {
        val installed = listOf(
            "com.android.chrome",
            "com.google.android.apps.chrome",
            "org.mozilla.tv.firefox",
            "org.mozilla.firefox",
            "com.brave.browser",
        )
        for (pkg in installed) {
            val entry = apps.find { it.component?.packageName == pkg } ?: continue
            launch(entry.component)
            return
        }
        startActivity(Intent(this, BrowserActivity::class.java))
    }

    private fun openCast() {
        val cast = Intent("android.settings.CAST_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(cast) }.isFailure) openSettings()
    }

    private fun showInputEditor() {
        val marks = listOf("ps5", "xbox", "switch", "pc", "cable", "disc", "live")
        val ports = listOf("HDMI 1", "HDMI 2", "HDMI 3", "HDMI 4", "AV", "Tuner")
        val raw = prefs.getString(KEY_INPUTS, "PS5|ps5|HDMI 1,Xbox|xbox|HDMI 2,Switch|switch|HDMI 3,PC|pc|HDMI 4,Cable|cable|AV") ?: ""
        val slots = raw.split(",").mapIndexedNotNull { index, item ->
            val parts = item.split("|")
            if (parts.size >= 2) {
                Triple(parts[0], parts[1], parts.getOrNull(2) ?: ports[index % ports.size])
            } else null
        }
        val root = window.decorView as ViewGroup
        val layer = android.widget.FrameLayout(this)
        layer.setBackgroundColor(0x6605070B.toInt())
        layer.setOnClickListener { root.removeView(layer) }
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_card)
        val pad = (24 * resources.displayMetrics.density).toInt()
        card.setPadding(pad, pad, pad, pad)
        card.setOnClickListener { }
        val title = TextView(this)
        title.text = "INPUTS"
        title.setTextColor(resources.getColor(R.color.cyan))
        title.letterSpacing = 0.3f
        card.addView(title)
        val names = mutableListOf<EditText>()
        val badges = mutableListOf<TextView>()
        val portViews = mutableListOf<TextView>()
        slots.forEach { slot ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            val badge = TextView(this)
            badge.text = slot.second
            badge.setTextColor(resources.getColor(R.color.gold))
            badge.setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            badge.setOnClickListener {
                val index = marks.indexOf(badge.text.toString())
                badge.text = marks[if (index < 0) 0 else (index + 1) % marks.size]
            }
            val name = EditText(this)
            name.setText(slot.first)
            name.setTextColor(resources.getColor(R.color.fg))
            name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val port = TextView(this)
            port.text = slot.third
            port.setTextColor(resources.getColor(R.color.cyan))
            port.setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            port.setOnClickListener {
                val index = ports.indexOf(port.text.toString())
                port.text = ports[if (index < 0) 0 else (index + 1) % ports.size]
            }
            port.setOnLongClickListener {
                openMappedInput(port.text.toString())
                true
            }
            val bar = TextView(this)
            bar.text = "BAR"
            bar.setTextColor(resources.getColor(R.color.gold))
            bar.setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            bar.setOnClickListener {
                pinInput(name.text.toString(), badge.text.toString(), port.text.toString())
            }
            row.addView(badge)
            row.addView(name)
            row.addView(port)
            row.addView(bar)
            card.addView(row)
            names += name
            badges += badge
            portViews += port
        }
        val done = TextView(this)
        done.text = "DONE"
        done.setTextColor(resources.getColor(R.color.gold))
        done.setPadding(0, pad, 0, 0)
        done.setOnClickListener {
            val encoded = names.indices.joinToString(",") { i -> "${names[i].text}|${badges[i].text}|${portViews[i].text}" }
            prefs.edit().putString(KEY_INPUTS, encoded).apply()
            root.removeView(layer)
        }
        card.addView(done)
        val cardParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        cardParams.gravity = android.view.Gravity.CENTER
        cardParams.leftMargin = pad
        cardParams.rightMargin = pad
        layer.addView(card, cardParams)
        root.addView(layer)
    }

    private fun pinInput(name: String, mark: String, port: String) {
        val token = "in:${name.trim()}|$mark|$port"
        if (token in dockPkgs || dockPkgs.size >= 9) return
        dockPkgs.add(token)
        saveDock()
        renderDock()
    }

    private fun openMappedInput(label: String) {
        val manager = getSystemService(TV_INPUT_SERVICE) as? android.media.tv.TvInputManager
        val match = manager?.tvInputList?.firstOrNull { info ->
            info.loadLabel(this)?.toString()?.contains(label, ignoreCase = true) == true
        }
        if (match != null) {
            val uri = android.media.tv.TvContract.buildChannelUriForPassthroughInput(match.id)
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
        openInputs()
    }
    private fun openSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun openWifi() {
        runCatching {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun openInputs() {
        val viewInputs = Intent("com.android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(viewInputs) }.isSuccess) return
        val candidates = listOf(
            ComponentName("com.android.tv.settings", "com.android.tv.settings.input.InputsActivity"),
            ComponentName("com.android.tv.settings", "com.android.tv.settings.system.InputsActivity"),
        )
        for (component in candidates) {
            val intent = Intent(Intent.ACTION_MAIN).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
        openSettings()
    }

    private fun openInfo(entry: AppEntry) {
        val pkg = entry.component?.packageName ?: return
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
        hideMenu()
    }

    private fun launch(component: ComponentName?) {
        if (component == null) return
        val leanback = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(leanback) }.isSuccess) return
        val launcher = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(launcher) }
    }

    private fun launchPackage(packages: List<String>) {
        for (pkg in packages) {
            val entry = apps.find { it.component?.packageName == pkg } ?: continue
            launch(entry.component)
            return
        }
    }

    private fun refreshApps() {
        val gen = ++loadGen
        Thread {
            val found = queryApps()
            runOnUiThread {
                if (gen != loadGen || isFinishing) return@runOnUiThread
                val next = found.map { info ->
                    val activity = info.activityInfo
                    AppEntry(
                        label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() } ?: activity.packageName,
                        kind = TileKind.APP,
                        icon = appIcon(packageManager, info),
                        component = ComponentName(activity.packageName, activity.name),
                    )
                }.sortedBy { it.label.lowercase(Locale.US) }
                val browser = AppEntry("Browser", TileKind.BROWSER, getDrawable(R.drawable.ic_web), null)
                apps = listOf(browser) + next
                libraryAdapter.setItems(apps)
                ensureDock()
                renderDock()
            }
        }.start()
    }

    private fun ensureDock() {
        val installed = apps.mapNotNull { it.component?.packageName }
        val saved = prefs.getString(KEY_DOCK, null)
        val chosen = if (saved == null) {
            val preferred = PREFERRED.filter { it in installed }
            (preferred + installed.filter { it !in preferred }).take(4)
        } else {
            saved.split(',').map { it.trim() }.filter { it in installed || it.startsWith("in:") || it == "app:browser" }
        }
        dockPkgs = chosen.toMutableList()
        if ("app:browser" !in dockPkgs && !prefs.getBoolean(KEY_BROWSER, false) && dockPkgs.size < 8) {
            dockPkgs.add("app:browser")
            prefs.edit().putBoolean(KEY_BROWSER, true).apply()
            saveDock()
        }
        if (saved == null) saveDock()
    }

    private fun saveDock() {
        prefs.edit().putString(KEY_DOCK, dockPkgs.joinToString(",")).apply()
    }

    private fun renderDock() {
        dock.removeAllViews()
        val pad = (8 * resources.displayMetrics.density).toInt()
        for (pkg in dockPkgs) {
            if (pkg == "app:browser") {
                val tile = layoutInflater.inflate(R.layout.item_app, dock, false)
                tile.findViewById<TextView>(R.id.label).text = "Browser"
                tile.findViewById<android.widget.ImageView>(R.id.icon).setImageResource(R.drawable.ic_web)
                tile.setBackgroundResource(R.drawable.bg_dock_item)
                val width = (92 * resources.displayMetrics.density).toInt()
                tile.layoutParams = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = pad
                }
                tile.setOnClickListener { openBrowser() }
                tile.setOnLongClickListener {
                    dockPkgs.remove(pkg)
                    prefs.edit().putBoolean(KEY_BROWSER, true).apply()
                    saveDock()
                    renderDock()
                    true
                }
                dock.addView(tile)
                continue
            }
            if (pkg.startsWith("in:")) {
                val parts = pkg.removePrefix("in:").split("|")
                val name = parts.getOrElse(0) { "Input" }
                val mark = parts.getOrElse(1) { "live" }
                val port = parts.getOrElse(2) { "HDMI 1" }
                val tile = layoutInflater.inflate(R.layout.item_app, dock, false)
                val icon = tile.findViewById<android.widget.ImageView>(R.id.icon)
                val res = when (mark) {
                    "ps5" -> R.drawable.logo_ps
                    "xbox" -> R.drawable.logo_xbox
                    else -> 0
                }
                if (res != 0) icon.setImageResource(res) else icon.setImageDrawable(null)
                tile.findViewById<TextView>(R.id.label).text = name
                tile.setBackgroundResource(R.drawable.bg_dock_item)
                val width = (92 * resources.displayMetrics.density).toInt()
                tile.layoutParams = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = pad
                }
                tile.setOnClickListener { openMappedInput(port) }
                tile.setOnLongClickListener {
                    dockPkgs.remove(pkg)
                    saveDock()
                    renderDock()
                    true
                }
                dock.addView(tile)
                continue
            }
            val entry = apps.find { it.component?.packageName == pkg } ?: continue
            val tile = layoutInflater.inflate(R.layout.item_app, dock, false)
            tile.findViewById<android.widget.ImageView>(R.id.icon).setImageDrawable(entry.icon)
            tile.findViewById<TextView>(R.id.label).text = entry.label
            tile.setBackgroundResource(R.drawable.bg_dock_item)
            tile.minimumHeight = 0
            val tight = (6 * resources.displayMetrics.density).toInt()
            tile.setPadding(tight, tight, tight, tight)
            tile.findViewById<TextView>(R.id.label).textSize = 12f
            val width = (92 * resources.displayMetrics.density).toInt()
            tile.layoutParams = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = pad
            }
            tile.setOnClickListener { open(entry) }
            tile.setOnLongClickListener {
                showMenu(entry, onDock = true)
                true
            }
            tile.setOnFocusChangeListener { _, focused ->
                if (focused) showShelf(entry.label, getString(R.string.focused))
            }
            dock.addView(tile)
        }
        val add = TextView(this)
        add.text = getString(R.string.add_app)
        add.setTextColor(resources.getColor(R.color.gold))
        add.textSize = 18f
        add.gravity = android.view.Gravity.CENTER
        add.setBackgroundResource(R.drawable.bg_dock_item)
        add.isFocusable = true
        add.isClickable = true
        val addWidth = (128 * resources.displayMetrics.density).toInt()
        val addHeight = (96 * resources.displayMetrics.density).toInt()
        add.layoutParams = LinearLayout.LayoutParams(addWidth, addHeight)
        add.setOnClickListener { showLibrary() }
        add.setOnFocusChangeListener { _, focused ->
            if (focused) showShelf(getString(R.string.add_app), getString(R.string.all_apps))
        }
        dock.addView(add)
    }

    private fun focusDock() {
        dock.post {
            val first = dock.getChildAt(0)
            if (first != null) first.requestFocus()
        }
    }

    private fun showShelf(label: String, kicker: String) {
        shelfName.text = label.uppercase(Locale.US)
        shelfKicker.text = kicker
    }

    private fun applyWall(id: String) {
        prefs.edit().putString(KEY_WALL, id).apply()
        when (id) {
            "coast" -> wallpaper.setImageResource(R.drawable.bg_coast)
            "void" -> wallpaper.setImageDrawable(null)
            else -> wallpaper.setImageResource(R.drawable.bg_rocket)
        }
    }

    private fun cycleWall() {
        val order = listOf("rocket", "coast", "void")
        val current = prefs.getString(KEY_WALL, "rocket") ?: "rocket"
        val next = order[(order.indexOf(current) + 1).mod(order.size)]
        applyWall(next)
        val label = when (next) {
            "coast" -> "COAST"
            "void" -> "VOID"
            else -> "ROCKET"
        }
        showShelf(label, getString(R.string.wall))
    }

    private fun isOurHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(home, 0)
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun installedPackage(pkg: String): Boolean {
        return runCatching { packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

    private fun debloatTargets(): List<String> {
        val quiet = BLOAT.filter { installedPackage(it) }
        val launchers = if (isOurHome()) LAUNCHERS.filter { installedPackage(it) } else emptyList()
        return (quiet + launchers).filter { it !in NEVER }
    }

    private fun showDebloat() {
        val found = debloatTargets()
        val saved = prefs.getStringSet(KEY_QUIET, emptySet()) ?: emptySet()
        val home = if (isOurHome()) "This app is Home." else "Stock launcher stays until this app is Home."
        debloatBody.text = buildString {
            append("Disable only. Nothing is uninstalled. Cast and setup stay on. ")
            append(home)
            append("\n")
            if (found.isEmpty()) append("\nNone of the quiet packages are installed.")
            else found.forEach { append("\n").append(it.substringAfterLast('.')) }
            if (saved.isNotEmpty()) append("\n\nQuiet now: ").append(saved.size)
        }
        expand(debloat)
        findViewById<View>(R.id.debloatRun).requestFocus()
    }

    private fun expand(view: View) {
        view.visibility = View.VISIBLE
        view.scaleX = 0.86f
        view.scaleY = 0.86f
        view.alpha = 0f
        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).start()
    }

    private fun hideDebloat() {
        debloat.visibility = View.GONE
        focusDock()
    }

    private fun tryDebloat() {
        debloatBody.text = "Working."
        Thread {
            val applied = (prefs.getStringSet(KEY_QUIET, emptySet()) ?: emptySet()).toMutableSet()
            val lines = debloatTargets().map { pkg ->
                val code = runCatching {
                    Runtime.getRuntime().exec(arrayOf("pm", "disable-user", "--user", "0", pkg)).waitFor()
                }.getOrDefault(1)
                if (code == 0) {
                    applied.add(pkg)
                    "off ${pkg.substringAfterLast('.')}"
                } else {
                    "blocked ${pkg.substringAfterLast('.')}"
                }
            }
            prefs.edit().putStringSet(KEY_QUIET, applied).apply()
            val text = if (lines.isEmpty()) {
                "Nothing on this set matches the quiet list."
            } else if (lines.none { it.startsWith("off") }) {
                "This TV blocked the disable. Nothing was uninstalled. From a computer: tools/debloat.sh. tools/restore.sh puts them back."
            } else {
                lines.joinToString("\n")
            }
            runOnUiThread { if (!isFinishing) debloatBody.text = text }
        }.start()
    }

    private fun tryRestore() {
        val saved = (prefs.getStringSet(KEY_QUIET, emptySet()) ?: emptySet()).toList()
        if (saved.isEmpty()) {
            debloatBody.text = "Nothing from this app is quiet. tools/restore.sh still reads the computer record."
            return
        }
        debloatBody.text = "Working."
        Thread {
            val left = saved.toMutableSet()
            val lines = saved.map { pkg ->
                val code = runCatching {
                    Runtime.getRuntime().exec(arrayOf("pm", "enable", "--user", "0", pkg)).waitFor()
                }.getOrDefault(1)
                if (code == 0) {
                    left.remove(pkg)
                    "on ${pkg.substringAfterLast('.')}"
                } else {
                    "blocked ${pkg.substringAfterLast('.')}"
                }
            }
            prefs.edit().putStringSet(KEY_QUIET, left).apply()
            runOnUiThread { if (!isFinishing) debloatBody.text = lines.joinToString("\n") }
        }.start()
    }

    private fun refreshStats() {
        val mem = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        val freeGb = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
        }.getOrDefault(0.0)
        val link = linkLabel()
        stats.text = String.format(Locale.US, "%.1f GB FREE  ·  %d MB  ·  %s", freeGb, mem.availMem / (1024 * 1024), link)
        refreshWeather()
    }

    private fun refreshWeather() {
        Thread {
            val line = runCatching {
                val zip = prefs.getString(KEY_ZIP, "45414") ?: "45414"
                val geo = runCatching { get("https://api.zippopotam.us/us/$zip") }.getOrDefault("")
                val lat = Regex("\"latitude\":\"([^\"]+)\"").find(geo)?.groupValues?.get(1) ?: "39.7589"
                val lon = Regex("\"longitude\":\"([^\"]+)\"").find(geo)?.groupValues?.get(1) ?: "-84.1916"
                val point = get("https://api.weather.gov/points/$lat,$lon")
                val forecast = Regex("\"forecast\":\"(https:[^\"]+)\"").find(point)?.groupValues?.get(1)
                    ?: return@runCatching ""
                val body = get(forecast)
                val temp = Regex("\"temperature\":(\\d+)").find(body)?.groupValues?.get(1) ?: return@runCatching ""
                val words = Regex("\"shortForecast\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: "WEATHER"
                val label = words.split(" ").take(2).joinToString(" ").uppercase(Locale.US)
                val names = Regex("\"name\":\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
                val temps = Regex("\"temperature\":(\\d+)").findAll(body).map { it.groupValues[1] }.toList()
                val peek = names.zip(temps).filter { (name, _) ->
                    !name.contains("night", true)
                }.take(3).joinToString("  ") { (name, value) ->
                    val short = if (name.startsWith("This", true)) "TODAY" else name.take(3).uppercase(Locale.US)
                    "$short $value°"
                }
                "$temp° $label|$peek"
            }.getOrDefault("")
            if (line.isNotEmpty()) runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val parts = line.split("|")
                weatherView.text = parts[0]
                outlookView.text = parts.getOrElse(1) { "" }
            }
        }.start()
    }

    private fun get(address: String): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.setRequestProperty("User-Agent", "Home/1.0 (dbrush95/googletvdebloatlauncher)")
        conn.setRequestProperty("Accept", "application/geo+json")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return body
    }

    private fun linkLabel(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val info = cm.activeNetworkInfo ?: return "OFFLINE"
        @Suppress("DEPRECATION")
        if (!info.isConnected) return "OFFLINE"
        @Suppress("DEPRECATION")
        return when (info.type) {
            ConnectivityManager.TYPE_WIFI -> "WI-FI"
            ConnectivityManager.TYPE_ETHERNET -> "ETHERNET"
            else -> "LINK"
        }
    }

    private fun queryApps(): List<ResolveInfo> {
        val pm = packageManager
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        fun collect(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val resolved = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            for (info in resolved) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == packageName || pkg in HIDDEN) continue
                if (category == Intent.CATEGORY_LEANBACK_LAUNCHER || !byPackage.containsKey(pkg)) {
                    byPackage[pkg] = info
                }
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER)
        collect(Intent.CATEGORY_LAUNCHER)
        return byPackage.values.toList()
    }

    private fun appIcon(pm: PackageManager, info: ResolveInfo): Drawable {
        val activity = info.activityInfo
        PACK[activity.packageName]?.let { return markIcon(it) }
        val loaded = listOfNotNull(
            runCatching { if (activity.icon != 0) activity.loadIcon(pm) else null }.getOrNull(),
            runCatching { if (activity.applicationInfo.icon != 0) activity.applicationInfo.loadIcon(pm) else null }.getOrNull(),
            runCatching { info.loadIcon(pm) }.getOrNull(),
        ).firstOrNull { it.intrinsicWidth > 0 && it.intrinsicHeight > 0 }
        if (loaded != null) return loaded
        val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: activity.packageName
        return markIcon(Mark(label.take(2).uppercase(Locale.US), 0xFF5FD0E6.toInt()))
    }

    private fun markIcon(mark: Mark): Drawable {
        val size = (96 * resources.displayMetrics.density).toInt().coerceAtLeast(96)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val inset = size * 0.08f
        paint.color = (mark.color and 0x00FFFFFF) or 0x33000000
        val radius = size * 0.22f
        canvas.drawRoundRect(inset, inset, size - inset, size - inset, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.035f
        paint.color = mark.color
        canvas.drawRoundRect(inset, inset, size - inset, size - inset, radius, radius, paint)
        paint.style = Paint.Style.FILL
        paint.color = mark.color
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * (if (mark.text.length > 2) 0.22f else 0.34f)
        val bounds = android.graphics.Rect()
        paint.getTextBounds(mark.text, 0, mark.text.length, bounds)
        canvas.drawText(mark.text, size / 2f, size / 2f - bounds.exactCenterY(), paint)
        return BitmapDrawable(resources, bitmap)
    }

    companion object {
        private const val KEY_SEEN = "welcomeSeenAt"
        private const val KEY_DOCK = "dock"
        private const val KEY_WALL = "wall"
        private const val KEY_ZIP = "zip"
        private const val KEY_INPUTS = "inputs"
        private const val KEY_QUIET = "quiet"
        private const val KEY_BROWSER = "browserOffered"
        private data class Mark(val text: String, val color: Int)
        private val PACK = mapOf(
            "com.netflix.ninja" to Mark("N", 0xFFE50914.toInt()),
            "com.google.android.youtube.tv" to Mark("YT", 0xFFFF4E45.toInt()),
            "com.google.android.youtube" to Mark("YT", 0xFFFF4E45.toInt()),
            "com.amazon.amazonvideo.livingroom" to Mark("prime", 0xFF00A8E1.toInt()),
            "com.amazon.avod.thirdpartyclient" to Mark("prime", 0xFF00A8E1.toInt()),
            "com.disney.disneyplus" to Mark("D+", 0xFF6EA8FF.toInt()),
            "com.android.vending" to Mark("▶", 0xFF5FD0E6.toInt()),
            "com.nvidia.geforcenow" to Mark("GFN", 0xFF76B900.toInt()),
            "com.limelight" to Mark("☾", 0xFF7EB6FF.toInt()),
            "org.xbmc.kodi" to Mark("K", 0xFF17B2E7.toInt()),
            "com.apple.atve.androidtv.appletv" to Mark("tv+", 0xFFF2F2F2.toInt()),
            "com.cbs.ott" to Mark("P+", 0xFF0064FF.toInt()),
            "com.spotify.tv.android" to Mark("♫", 0xFF1DB954.toInt()),
            "com.hulu.livingroomplus" to Mark("hulu", 0xFF1CE783.toInt()),
            "com.peacocktv.peacockandroid" to Mark("P", 0xFFF5C518.toInt()),
            "com.plexapp.android" to Mark("plex", 0xFFE5A00D.toInt()),
            "com.tubitv" to Mark("tubi", 0xFFC084FC.toInt()),
            "com.wbd.stream" to Mark("max", 0xFF002BE7.toInt()),
        )
        private val PREFERRED = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.android.vending",
            "com.nvidia.geforcenow",
            "com.limelight",
            "com.spotify.tv.android",
        )
        private val YOUTUBE = listOf(
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
        )
        private val HIDDEN = setOf(
            "com.google.android.tvrecommendations",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
            "com.google.android.backdrop",
            "com.google.android.apps.tv.dreamx",
            "com.google.android.tungsten.setupwraith",
        )
        private val BLOAT = listOf(
            "com.google.android.tvrecommendations",
            "com.google.android.backdrop",
            "com.google.android.apps.tv.dreamx",
            "com.google.android.feedback",
            "com.google.android.partnersetup",
            "com.google.android.onetimeinitializer",
            "com.google.android.play.games",
        )
        private val LAUNCHERS = listOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
        )
        private val NEVER = setOf(
            "com.google.android.tungsten.setupwraith",
            "com.google.android.apps.mediashell",
            "com.android.vending",
            "com.google.android.gms",
        )
    }
}
