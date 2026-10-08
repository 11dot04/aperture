package com.microtag.shizuku

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.IInterface
import android.util.Log
import com.aperture.inspect.ProcessTextActivity
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuClipboardWatcher {
    private const val TAG = "ApertureShizuku"
    private var isListening = false
    private var lastObservedClip = ""

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Shizuku permission granted. Initializing listener.")
            startListeningInternal()
        }
    }

    fun init(context: Context) {
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku service is not running.")
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            startListeningInternal()
        } else {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.requestPermission(1001)
        }
    }

    private fun startListeningInternal() {
        if (isListening) return
        try {
            val binder = SystemServiceHelper.getSystemService("clipboard")
            if (binder == null) {
                Log.e(TAG, "Unable to acquire clipboard system service via Shizuku.")
                return
            }

            // In AOSP, clipboard service handles primary clip via IOnPrimaryClipChangedListener
            val wrappedBinder = ShizukuBinderWrapper(binder)
            Log.d(TAG, "Hooked privileged clipboard binder: ${wrappedBinder.interfaceDescriptor}")
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed binding clipboard listener: ${e.message}")
        }
    }

    // Called when high-intent tokens pass clipboard heuristic
    fun inspectIfActionable(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed == lastObservedClip || trimmed.length < 2) return
        lastObservedClip = trimmed

        // High-intent filter
        val isOtp = Regex("""\b\d{4,8}\b""").matches(trimmed)
        val isHex = Regex("""(?i)^#?([0-9a-f]{6}|[0-9a-f]{3})$""").matches(trimmed)
        val isMath = Regex("""^\s*(-?\d+)\s*([\+\-\*\/])\s*(-?\d+)\s*$""").matches(trimmed)
        val isUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")

        if (isOtp || isHex || isMath || isUrl) {
            val intent = Intent(context, ProcessTextActivity::class.java).apply {
                action = Intent.ACTION_PROCESS_TEXT
                type = "text/plain"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(Intent.EXTRA_PROCESS_TEXT, trimmed)
            }
            context.startActivity(intent)
        }
    }
}
