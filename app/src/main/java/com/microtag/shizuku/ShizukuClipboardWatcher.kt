package com.microtag.shizuku

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.util.Log
import com.microtag.inspect.ProcessTextActivity
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuClipboardWatcher {
    private const val TAG = "MicrotagClipboard"
    private var isListening = false
    private var lastObservedClip = ""

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Shizuku granted. Registering clipboard observer.")
            attachClipboardHook()
        }
    }

    fun init(context: Context) {
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku service unreachable or not installed.")
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            attachClipboardHook()
        } else {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.requestPermission(1001)
        }
    }

    private fun attachClipboardHook() {
        if (isListening) return
        try {
            val rawBinder = SystemServiceHelper.getSystemService("clipboard") ?: return
            val wrappedBinder = ShizukuBinderWrapper(rawBinder)

            // Register primary clip changed listener via direct IPC
            // Interface token: android.content.IClipboard
            val listener = object : android.content.IOnPrimaryClipChangedListener.Stub() {
                override fun dispatchPrimaryClipChanged() {
                    fetchPrimaryClip(wrappedBinder)
                }
            }

            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken("android.content.IClipboard")
                data.writeStrongBinder(listener.asBinder())
                // In AOSP, "com.android.shell" provides privileged caller context through Shizuku
                data.writeString("com.android.shell")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    data.writeString(null) // attributionTag
                }
                data.writeInt(0) // userId: USER_SYSTEM

                // IBinder.FIRST_CALL_TRANSACTION + 5 corresponds to addPrimaryClipChangedListener in standard AOSP IClipboard
                // Shizuku transacts with elevated UID 2000 (shell)
                wrappedBinder.transact(IBinder.FIRST_CALL_TRANSACTION + 5, data, reply, 0)
                reply.readException()
                isListening = true
                Log.d(TAG, "Registered clipboard change listener via privileged IPC.")
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed hooking IClipboard: ${e.message}")
        }
    }

    private fun fetchPrimaryClip(binder: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("android.content.IClipboard")
            data.writeString("com.android.shell")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                data.writeString(null) // attributionTag
            }
            data.writeInt(0) // userId: USER_SYSTEM

            // IBinder.FIRST_CALL_TRANSACTION + 1 corresponds to getPrimaryClip in standard AOSP IClipboard
            binder.transact(IBinder.FIRST_CALL_TRANSACTION + 1, data, reply, 0)
            reply.readException()

            if (reply.readInt() != 0) {
                val clipData = ClipData.CREATOR.createFromParcel(reply)
                val text = clipData.getItemAt(0)?.coerceToText(null)?.toString()
                if (!text.isNullOrBlank()) {
                    evaluateClip(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetching primary clip: ${e.message}")
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun evaluateClip(text: String) {
        val trimmed = text.trim()
        if (trimmed == lastObservedClip || trimmed.length < 2) return
        lastObservedClip = trimmed

        // High-intent triggers (instant regex triage)
        val isOtp = Regex("""\b\d{4,8}\b""").matches(trimmed)
        val isHex = Regex("""(?i)^#?([0-9a-f]{6}|[0-9a-f]{3})$""").matches(trimmed)
        val isMath = Regex("""^\s*(-?\d+)\s*([\+\-\*\/])\s*(-?\d+)\s*$""").matches(trimmed)
        val isUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")
        val isUnit = Regex("""(?i)^\s*(\d+(?:\.\d+)?)\s*(lbs?|pounds?|kg|kilograms?|mi|miles?|km|kilometers?|f|fahrenheit|c|celsius|psi|bar)\s*$""").matches(trimmed)

        if (isOtp || isHex || isMath || isUrl || isUnit) {
            val appCtx = Shizuku.getBinder()?.let { null } // Context placeholder
            // Triggers ProcessTextActivity headlessly when high-intent content is captured
        }
    }
}
