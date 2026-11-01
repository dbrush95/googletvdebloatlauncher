package com.dbrush.jacobstv

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
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
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
        debloatBody = findViewById(R.id.debloatBody)
        wallpaper = findViewById(R.id.wallpaper)
        dream = findViewById(R.id.dream)
        dreamClock = findViewById(R.id.dreamClock)
        dock = findViewById(R.id.dock)
        grid = findViewById(R.id.libraryGrid)
        shelfKicker = findViewById(R.id.shelfKicker)
        shelfName = findViewById(R.id.shelfName)
        stats = findViewById(R.id.stats)
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
        findViewById<View>(R.id.chipInputs).setOnClickListener { openInputs() }
        findViewById<View>(R.id.chipLink).apply {
            setOnClickListener { openWifi() }
            setOnFocusChangeListener { _, focused -> if (focused) showShelf(getString(R.string.link), getString(R.string.focused)) }
        }
        findViewById<View>(R.id.chipSettings).apply {
            setOnClickListener { openSettings() }
            setOnFocusChangeListener { _, focused -> if (focused) showShelf(getString(R.string.settings), getString(R.string.focused)) }
        }
        findViewById<View>(R.id.chipAbout).apply {
            setOnClickListener { showAbout() }
            setOnFocusChangeListener { _, focused -> if (focused) showShelf(getString(R.string.about), getString(R.string.focused)) }
        }
        findViewById<View>(R.id.chipWall).setOnClickListener { cycleWall() }
        findViewById<View>(R.id.chipDebloat).setOnClickListener { showDebloat() }
        findViewById<View>(R.id.debloatRun).setOnClickListener { tryDebloat() }
        findViewById<View>(R.id.debloatClose).setOnClickListener { hideDebloat() }
        debloat.setOnClickListener { hideDebloat() }
        applyWall(prefs.getString(KEY_WALL, "rocket") ?: "rocket")
        findViewById<View>(R.id.chipInputs).setOnFocusChangeListener { _, focused ->
            if (focused) showShelf(getString(R.string.inputs), getString(R.string.focused))
        }
        menuMoveLeft.setOnClickListener { nudgeMenu(-1) }
        menuMoveRight.setOnClickListener { nudgeMenu(1) }
        menuRemove.setOnClickListener { menuEntry?.component?.packageName?.let { unpin(it) } }
        menuPin.setOnClickListener { menuEntry?.component?.packageName?.let { pin(it) } }
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
        about.visibility = View.VISIBLE
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
        grid.requestFocus()
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
        (if (onDock) menuMoveLeft else menuPin).requestFocus()
    }

    private fun hideMenu() {
        menu.visibility = View.GONE
        menuEntry = null
        if (library.visibility == View.VISIBLE) grid.requestFocus() else focusDock()
    }

    private fun nudgeMenu(dir: Int) {
        val pkg = menuEntry?.component?.packageName ?: return
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
        if (pkg in dockPkgs || dockPkgs.size >= 7) {
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
        }
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
            val next = queryApps()
            runOnUiThread {
                if (gen != loadGen || isFinishing) return@runOnUiThread
                apps = next
                libraryAdapter.setItems(next)
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
            saved.split(',').map { it.trim() }.filter { it in installed }
        }
        dockPkgs = chosen.toMutableList()
        if (saved == null) saveDock()
    }

    private fun saveDock() {
        prefs.edit().putString(KEY_DOCK, dockPkgs.joinToString(",")).apply()
    }

    private fun renderDock() {
        dock.removeAllViews()
        val pad = (8 * resources.displayMetrics.density).toInt()
        for (pkg in dockPkgs) {
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
            else findViewById<View>(R.id.chipInputs).requestFocus()
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

    private fun showDebloat() {
        debloatBody.text = getString(R.string.debloat_body)
        debloat.visibility = View.VISIBLE
        findViewById<View>(R.id.debloatRun).requestFocus()
    }

    private fun hideDebloat() {
        debloat.visibility = View.GONE
        focusDock()
    }

    private fun tryDebloat() {
        debloatBody.text = "Working."
        Thread {
            val lines = DEBLOAT.map { pkg ->
                val code = runCatching {
                    Runtime.getRuntime().exec(arrayOf("pm", "disable-user", "--user", "0", pkg)).waitFor()
                }.getOrDefault(1)
                if (code == 0) "disabled $pkg" else "blocked $pkg"
            }
            val text = if (lines.none { it.startsWith("disabled") }) {
                "This TV blocked the disable. Nothing was uninstalled. From a computer, with USB debugging on, run tools/debloat.sh. tools/restore.sh puts them back."
            } else {
                lines.joinToString("\n")
            }
            runOnUiThread { if (!isFinishing) debloatBody.text = text }
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
        findViewById<TextView>(R.id.chipLink).text = link
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

    private fun queryApps(): List<AppEntry> {
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
        return byPackage.values.map { info ->
            val activity = info.activityInfo
            AppEntry(
                label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: activity.packageName,
                kind = TileKind.APP,
                icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                component = ComponentName(activity.packageName, activity.name),
            )
        }.sortedBy { it.label.lowercase(Locale.US) }
    }

    companion object {
        private const val KEY_SEEN = "welcomeSeenAt"
        private const val KEY_DOCK = "dock"
        private const val KEY_WALL = "wall"
        private val PREFERRED = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
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
        private val DEBLOAT = listOf(
            "com.google.android.tvrecommendations",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.google.android.backdrop",
            "com.google.android.apps.tv.dreamx",
        )
    }
}
