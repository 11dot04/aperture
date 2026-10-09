package com.microtag.core

import android.content.Context
import android.content.SharedPreferences

class MicrotagPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("microtag_prefs", Context.MODE_PRIVATE)

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit().putBoolean("onboarding_complete", value).apply()

    var interceptCount: Int
        get() = prefs.getInt("intercept_count", 0)
        set(value) = prefs.edit().putInt("intercept_count", value).apply()

    fun incrementInterceptCount() {
        interceptCount += 1
    }

    fun isAppEnabled(key: String, default: Boolean = true): Boolean {
        return prefs.getBoolean("app_toggle_$key", default)
    }

    fun setAppEnabled(key: String, enabled: Boolean) {
        prefs.edit().putBoolean("app_toggle_$key", enabled).apply()
    }

    companion object {
        const val KEY_STRAVA = "strava"
        const val KEY_DISCORD = "discord"
        const val KEY_OTP = "otp"
        const val KEY_DOWNLOADS = "downloads"
        const val KEY_PROTON = "proton"
        const val KEY_TEAMS = "teams"
        const val KEY_MAPS = "maps"
        const val KEY_CLIPBOARD = "clipboard"
    }
}
