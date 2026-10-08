/*
 * Copyright (C) 2026 Aperture Project
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aperture.core

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CapsuleDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val notifId = intent?.getIntExtra("notification_id", -1) ?: return
        if (notifId != -1) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(notifId)
        }
    }
}
