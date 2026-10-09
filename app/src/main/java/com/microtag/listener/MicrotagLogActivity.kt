package com.microtag.listener

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.microtag.core.MicrotagLog

/**
 * Plain-View log screen (no Compose/RecyclerView dependency).
 * Open with: startActivity(Intent(context, MicrotagLogActivity::class.java))
 */
class MicrotagLogActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var countView: TextView
    private var promotedOnly = false

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MicrotagLog.init(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(
                    dp(12) + bars.left, dp(8) + bars.top,
                    dp(12) + bars.right, dp(8) + bars.bottom
                )
            }
            insets
        }

        root.addView(TextView(this).apply {
            text = "Microtag log"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
        })

        countView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        root.addView(countView)

        val toggles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toggles.addView(Switch(this).apply {
            text = "Logging  "
            isChecked = MicrotagLog.enabled
            setOnCheckedChangeListener { _, checked -> MicrotagLog.setEnabled(checked) }
        })
        toggles.addView(CheckBox(this).apply {
            text = "Promoted only"
            setOnCheckedChangeListener { _, checked ->
                promotedOnly = checked
                refresh()
            }
        })
        root.addView(toggles)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val weight = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        buttons.addView(Button(this).apply {
            text = "Copy"
            setOnClickListener { copyToClipboard() }
        }, weight)
        buttons.addView(Button(this).apply {
            text = "Clear"
            setOnClickListener { MicrotagLog.clear() }
        }, weight)
        root.addView(buttons)

        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.typeface = Typeface.MONOSPACE
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                view.setPadding(0, dp(8), 0, dp(8))
                return view
            }
        }
        root.addView(
            ListView(this).apply {
                this.adapter = this@MicrotagLogActivity.adapter
                divider = null
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        MicrotagLog.onChanged = { handler.post { refresh() } }
        refresh()
    }

    override fun onStop() {
        MicrotagLog.onChanged = null
        super.onStop()
    }

    private fun visibleEntries(): List<MicrotagLog.Entry> =
        MicrotagLog.snapshot().filter {
            !promotedOnly || it.decision == MicrotagLog.Decision.PROMOTED
        }

    private fun refresh() {
        val list = visibleEntries()
        adapter.clear()
        adapter.addAll(list.map { it.format() })
        countView.text = "${list.size} entries (newest first)"
    }

    private fun copyToClipboard() {
        val text = MicrotagLog.asText(visibleEntries())
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Microtag log", text))
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }
}
