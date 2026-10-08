package com.microtag.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.core.MicrotagReminder
import com.microtag.listener.MicrotagNotificationService
import com.microtag.rules.RuleEngine
import com.microtag.shizuku.ShizukuClipboardWatcher
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize core engines
        RuleEngine.init(applicationContext)
        ShizukuClipboardWatcher.init(applicationContext)

        setContent {
            MicrotagTheme {
                DashboardScreen()
            }
        }
    }
}

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    var hasNotifAccess by remember { mutableStateOf(checkNotificationListenerAccess(context)) }
    var hasShizukuAccess by remember { mutableStateOf(checkShizukuPermission()) }
    val ruleCount = RuleEngine.getActiveRules().size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp)
            .systemBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(modifier = Modifier.padding(top = 28.dp)) {
            Text(
                text = "MICROTAG",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "STATUS CAPSULE ENGINE",
                color = Color(0xFF666666),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Status Indicators
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusCard(
                label = "NOTIFICATION LISTENER",
                status = if (hasNotifAccess) "ACTIVE" else "UNAUTHORIZED",
                isActive = hasNotifAccess,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )

            StatusCard(
                label = "SHIZUKU PRIVILEGED IPC",
                status = if (hasShizukuAccess) "CONNECTED" else "DISCONNECTED",
                isActive = hasShizukuAccess,
                onClick = {
                    if (Shizuku.pingBinder()) {
                        Shizuku.requestPermission(1001)
                    }
                }
            )

            StatusCard(
                label = "LOADED RULES",
                status = "$ruleCount COMPILED",
                isActive = ruleCount > 0,
                onClick = {}
            )
        }

        // Test Capsule Action
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .clickable {
                        MicrotagReminder.showCapsule(
                            context = context,
                            pillText = "LIVE",
                            title = "Microtag Test",
                            content = "Minimal status capsule active",
                            notificationId = 1001,
                            timeoutSeconds = 8
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EMIT TEST CAPSULE",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun StatusCard(
    label: String,
    status: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0A0A), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = label,
                    color = Color(0xFF777777),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status,
                    color = if (isActive) Color(0xFF00FF66) else Color(0xFFFF3333),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isActive) Color(0xFF00FF66) else Color(0xFFFF3333),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

private fun checkNotificationListenerAccess(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val cn = ComponentName(context, MicrotagNotificationService::class.java)
    return flat != null && flat.contains(cn.flattenToString())
}

private fun checkShizukuPermission(): Boolean {
    return try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }
}

@Composable
fun MicrotagTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color(0xFF0A0A0A),
            primary = Color.White
        ),
        content = content
    )
}
