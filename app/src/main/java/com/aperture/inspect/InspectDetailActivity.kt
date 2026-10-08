package com.aperture.inspect

import android.app.Activity
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class InspectDetailActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes.blurBehindRadius = 60
        }
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val notifId = intent.getIntExtra("EXTRA_NOTIFICATION_ID", -1)
        if (notifId != -1) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(notifId)
        }

        val domainTag = intent.getStringExtra("EXTRA_DOMAIN") ?: "LXCN"
        val titleText = intent.getStringExtra("EXTRA_TITLE") ?: ""
        val subtitleText = intent.getStringExtra("EXTRA_SUBTITLE") ?: ""
        val bodyContent = intent.getStringExtra("EXTRA_FULL_CONTENT") ?: ""
        val copyPayload = intent.getStringExtra("EXTRA_COPY_PAYLOAD") ?: bodyContent

        val accentColor = when (domainTag) {
            "URL" -> Color.parseColor("#2E5BFF")
            "CALC" -> Color.parseColor("#F9E800")
            else -> Color.parseColor("#CCFF00") // Core Neon
        }

        val density = resources.displayMetrics.density

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#4D000000"))
            setOnClickListener { finish() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val padH = (20 * density).toInt()
            val padV = (22 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2101114"))
                cornerRadius = 24 * density
            }
            isClickable = true
        }

        val cardParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            val marginH = (16 * density).toInt()
            val marginB = (28 * density).toInt()
            setMargins(marginH, 0, marginH, marginB)
        }

        val spine = VerticalSpineView(this, domainTag.uppercase(), accentColor)
        val spineParams = LinearLayout.LayoutParams(
            (26 * density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginEnd = (14 * density).toInt()
        }
        card.addView(spine, spineParams)

        val contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val headline = TextView(this).apply {
            text = titleText
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = 28f
            letterSpacing = -0.02f
            setPadding(0, 0, 0, if (subtitleText.isBlank()) (12 * density).toInt() else 0)
        }
        contentColumn.addView(headline)

        if (subtitleText.isNotBlank()) {
            val sub = TextView(this).apply {
                text = subtitleText
                textSize = 13f
                setTextColor(accentColor)
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 0, (12 * density).toInt())
            }
            contentColumn.addView(sub)
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * density).toInt() }
            isVerticalScrollBarEnabled = false
        }

        val body = TextView(this).apply {
            text = bodyContent
            textSize = 14f
            setTextColor(Color.parseColor("#E0E0E0"))
            setLineSpacing(0f, 1.38f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextIsSelectable(true)
        }
        scroll.addView(body)
        contentColumn.addView(scroll)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val copyBtn = Button(this).apply {
            text = "COPY"
            textSize = 11f
            setTextColor(Color.BLACK)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = 14 * density
            }
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (36 * density).toInt()
            ).apply { marginEnd = (10 * density).toInt() }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cb?.setPrimaryClip(ClipData.newPlainText("Aperture", copyPayload))
                Toast.makeText(this@InspectDetailActivity, "Copied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        actions.addView(copyBtn)

        val closeBtn = Button(this).apply {
            text = "DISMISS"
            textSize = 11f
            setTextColor(Color.parseColor("#CCCCCC"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222226"))
                cornerRadius = 14 * density
            }
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (36 * density).toInt()
            )
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                finish()
            }
        }
        actions.addView(closeBtn)

        contentColumn.addView(actions)
        card.addView(contentColumn)
        rootLayout.addView(card, cardParams)
        setContentView(rootLayout)
    }

    private class VerticalSpineView(context: Context, private val text: String, color: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 32f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.22f
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val textWidth = paint.measureText(text)
            setMeasuredDimension((28 * resources.displayMetrics.density).toInt(), textWidth.toInt() + 30)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.save()
            canvas.translate(width / 2f + 8f, height.toFloat() - 8f)
            canvas.rotate(-90f)
            canvas.drawText(text, 0f, 0f, paint)
            canvas.restore()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}
