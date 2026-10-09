package com.microtag.shizuku

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.util.Log
import com.microtag.inspect.ProcessTextActivity
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Proxy

object ShizukuClipboardWatcher {
    private const val TAG = "MicrotagClipboard"
    private var isListening = false
    private var lastObservedClip = ""
    private var appContext: Context? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkAndAttach()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
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

    fun checkAndAttach() {
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
        val context = appContext ?: return

        try {
            // Get clipboard service binder via Shizuku binder wrapper
            val rawBinder = SystemServiceHelper.getSystemService(Context.CLIPBOARD_SERVICE) ?: return
            val wrappedBinder = ShizukuBinderWrapper(rawBinder)

            val iClipboardClass = Class.forName("android.content.IClipboard")
            val stubClass = Class.forName("android.content.IClipboard\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val clipboardService = asInterface.invoke(null, wrappedBinder)

            val listenerClass = Class.forName("android.content.IOnPrimaryClipChangedListener")

            // Dynamic proxy to bypass missing AOSP stub interfaces at compile time
            val listenerProxy = Proxy.newProxyInstance(
                context.classLoader,
                arrayOf(listenerClass)
            ) { _, method, _ ->
                if (method.name == "dispatchPrimaryClipChanged") {
                    fetchAndProcessClip(clipboardService)
                }
                null
            }

            // Find addPrimaryClipChangedListener on IClipboard
            val methods = iClipboardClass.methods.filter { it.name == "addPrimaryClipChangedListener" }
            val targetMethod = methods.firstOrNull() ?: return

            val args = arrayOfNulls<Any>(targetMethod.parameterTypes.size)
            for (i in targetMethod.parameterTypes.indices) {
                val paramType = targetMethod.parameterTypes[i]
                when {
                    paramType.isAssignableFrom(listenerClass) -> args[i] = listenerProxy
                    paramType == String::class.java -> args[i] = "com.android.shell"
                    paramType == Int::class.javaPrimitiveType -> args[i] = 0 // USER_ALL / USER_SYSTEM
                    paramType.name.contains("AttributionSource") -> args[i] = null
                }
            }

            targetMethod.invoke(clipboardService, *args)
            isListening = true
            Log.d(TAG, "Privileged IClipboard listener attached successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed hooking privileged clipboard: ${e.message}", e)
        }
    }

    private fun fetchAndProcessClip(clipboardService: Any) {
        try {
            val getClipMethod = clipboardService.javaClass.methods.firstOrNull { it.name == "getPrimaryClip" } ?: return
            val args = arrayOfNulls<Any>(getClipMethod.parameterTypes.size)
            for (i in getClipMethod.parameterTypes.indices) {
                val paramType = getClipMethod.parameterTypes[i]
                when {
                    paramType == String::class.java -> args[i] = "com.android.shell"
                    paramType == Int::class.javaPrimitiveType -> args[i] = 0
                    paramType.name.contains("AttributionSource") -> args[i] = null
                }
            }

            val clipData = getClipMethod.invoke(clipboardService, *args) as? ClipData ?: return
            val text = clipData.getItemAt(0)?.coerceToText(appContext)?.toString()
            if (!text.isNullOrBlank()) {
                evaluateClip(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching clip: ${e.message}")
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
