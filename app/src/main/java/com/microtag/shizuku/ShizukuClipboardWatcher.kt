package com.microtag.shizuku

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
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
    private var appContext: Context? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder acquired.")
        checkAndAttach()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku service died or was terminated.")
        isListening = false
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            attachClipboardHook()
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    private fun checkAndAttach() {
        if (!Shizuku.pingBinder()) return

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

            // Implement IOnPrimaryClipChangedListener via generic Binder to avoid hidden SDK stubs
            val listener = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    // TRANSACTION_dispatchPrimaryClipChanged = FIRST_CALL_TRANSACTION
                    if (code == IBinder.FIRST_CALL_TRANSACTION) {
                        data.enforceInterface("android.content.IOnPrimaryClipChangedListener")
                        fetchPrimaryClip(wrappedBinder)
                        return true
                    }
                    return super.onTransact(code, data, reply, flags)
                }

                override fun getInterfaceDescriptor(): String {
                    return "android.content.IOnPrimaryClipChangedListener"
                }
            }

            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken("android.content.IClipboard")
                data.writeStrongBinder(listener)
                data.writeString("com.android.shell")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    data.writeString(null)
                }
                data.writeInt(0) // USER_SYSTEM

                // IBinder.FIRST_CALL_TRANSACTION + 5: addPrimaryClipChangedListener
                wrappedBinder.transact(IBinder.FIRST_CALL_TRANSACTION + 5, data, reply, 0)
                reply.readException()
                isListening = true
                Log.d(TAG, "Privileged clipboard listener registered.")
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
                data.writeString(null)
            }
            data.writeInt(0)

            // IBinder.FIRST_CALL_TRANSACTION + 1: getPrimaryClip
            binder.transact(IBinder.FIRST_CALL_TRANSACTION + 1, data, reply, 0)
            reply.readException()

            if (reply.readInt() != 0) {
                val clipData = ClipData.CREATOR.createFromParcel(reply)
                val text = clipData.getItemAt(0)?.coerceToText(appContext)?.toString()
                if (!text.isNullOrBlank()) {
                    evaluateClip(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetching clip: ${e.message}")
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun evaluateClip(text: String) {
        val trimmed = text.trim()
        if (trimmed == lastObservedClip || trimmed.length < 2) return
        lastObservedClip = trimmed

        val isOtp = Regex("""\b\d{4,8}\b""").matches(trimmed)
        val isHex = Regex("""(?i)^#?([0-9a-f]{6}|[0-9a-f]{3})$""").matches(trimmed)
        val isMath = Regex("""^\s*(-?\d+)\s*([\+\-\*\/])\s*(-?\d+)\s*$""").matches(trimmed)
        val isUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")
        val isUnit = Regex("""(?i)^\s*(\d+(?:\.\d+)?)\s*(lbs?|pounds?|kg|kilograms?|mi|miles?|km|kilometers?|f|fahrenheit|c|celsius|psi|bar)\s*$""").matches(trimmed)

        if (isOtp || isHex || isMath || isUrl || isUnit) {
            val ctx = appContext ?: return
            val intent = Intent(ctx, ProcessTextActivity::class.java).apply {
                action = Intent.ACTION_PROCESS_TEXT
                type = "text/plain"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra(Intent.EXTRA_PROCESS_TEXT, trimmed)
            }
            ctx.startActivity(intent)
        }
    }
}
