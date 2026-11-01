package com.dbrush.jacobstv

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class BrowserActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var field: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF05070B.toInt())
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val pad = (12 * resources.displayMetrics.density).toInt()
        bar.setPadding(pad, pad, pad, pad)
        field = EditText(this)
        field.setText("https://www.youtube.com")
        field.setSingleLine(true)
        field.setTextColor(0xFFE7EEF2.toInt())
        field.setHintTextColor(0xFF8EA0AB.toInt())
        field.hint = "Address"
        val go = TextView(this)
        go.text = "GO"
        go.setTextColor(0xFFE4B15A.toInt())
        go.setPadding(pad, pad / 2, pad, pad / 2)
        go.isFocusable = true
        go.setOnClickListener { load(field.text.toString()) }
        bar.addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(go)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.isFocusable = true
        root.addView(bar)
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        load(field.text.toString())
    }

    private fun load(raw: String) {
        val next = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        field.setText(next)
        web.loadUrl(next)
        web.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
