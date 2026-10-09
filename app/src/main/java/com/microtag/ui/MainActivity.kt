package com.microtag.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import com.microtag.rules.RuleEngine
import com.microtag.shizuku.ShizukuClipboardWatcher
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuleEngine.init(applicationContext)
        ShizukuClipboardWatcher.init(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) {
                    dynamicDarkColorScheme(this)
                } else {
                    dynamicLightColorScheme(this)
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs = remember { MicrotagPrefs(applicationContext) }
                    var onboardingDone by remember { mutableStateOf(prefs.hasCompletedOnboarding) }

                    AnimatedContent(
                        targetState = onboardingDone,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith
                                    fadeOut(animationSpec = tween(400))
                        },
                        label = "ScreenSwitch"
                    ) { isDone ->
                        if (!isDone) {
                            OnboardingScreen(
                                onFinish = {
                                    prefs.hasCompletedOnboarding = true
                                    onboardingDone = true
                                }
                            )
                        } else {
                            DashboardScreen(prefs = prefs)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. ONBOARDING SCREEN (PUNCHHOLE ARROW & SETUP)
// ==========================================

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasListener by remember { mutableStateOf(checkListenerPermission(context)) }
    var hasNotifPermission by remember { mutableStateOf(checkPostNotifPermission(context)) }
    var hasShizuku by remember { mutableStateOf(checkShizukuPermission()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasListener = checkListenerPermission(context)
                hasNotifPermission = checkPostNotifPermission(context)
                hasShizuku = checkShizukuPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Gentle bounce animation for the punchhole arrow
    val infiniteTransition = rememberInfiniteTransition(label = "ArrowBounce")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Offset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Punchhole Arrow Pointer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = arrowOffset.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Cutout target",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Eyes up here 👆",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Welcome to Microtag",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your notch is about to get a lot more interesting. Microtag lifts background activities and notifications directly into dynamic status bar capsules.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
        )

        // Permission Setup Toggles
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            PermissionTile(
                title = "Notification Interception",
                desc = "Required to catch live events, progress bars, and OTPs.",
                isGranted = hasListener,
                actionLabel = "Enable",
                onAction = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionTile(
                    title = "Post Notifications",
                    desc = "Allows Microtag to dock the live status chips.",
                    isGranted = hasNotifPermission,
                    actionLabel = "Allow",
                    onAction = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            }

            PermissionTile(
                title = "Privileged Shizuku Hook",
                desc = "Instant zero-click clipboard capture for math, units, and OTPs.",
                isGranted = hasShizuku,
                actionLabel = "Authorize",
                onAction = {
                    if (Shizuku.pingBinder()) {
                        Shizuku.requestPermission(1001)
                    }
                }
            )
        }

        Button(
            onClick = onFinish,
            enabled = hasListener,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (hasListener) "Step Inside" else "Grant Interception to Continue",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionTile(
    title: String,
    desc: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isGranted) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Granted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// 2. MAIN DASHBOARD SCREEN
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(prefs: MicrotagPrefs) {
    val context = LocalContext.current
    var interceptCount by remember { mutableStateOf(prefs.interceptCount) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MICROTAG",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        fontSize = 19.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Big Bold Interception Metric Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = interceptCount.toString(),
                            fontSize = 68.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            lineHeight = 72.sp
                        )
                        Text(
                            text = "NOTIFICATIONS DOCKED & PARSED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Test Simulation Trigger
            item {
                OutlinedButton(
                    onClick = {
                        prefs.incrementInterceptCount()
                        interceptCount = prefs.interceptCount
                        MicrotagReminder.showCapsule(
                            context = context,
                            pillText = "98% • Fast",
                            title = "Microtag Active",
                            content = "Live Updates chip docked to status bar",
                            notificationId = 9999,
                            timeoutSeconds = 10,
                            iconName = "ic_capsule_bolt"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Simulate Live Capsule", fontWeight = FontWeight.Bold)
                }
            }

            // Toggles Header
            item {
                Text(
                    text = "Supported Apps & Instances",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // App Toggles
            item {
                AppInstanceToggle(
                    title = "Strava & Workouts",
                    subtitle = "Live split pace & elapsed running timer",
                    key = MicrotagPrefs.KEY_STRAVA,
                    prefs = prefs
                )
            }

            item {
                AppInstanceToggle(
                    title = "Discord Mentions",
                    subtitle = "Priority @mentions and private direct messages",
                    key = MicrotagPrefs.KEY_DISCORD,
                    prefs = prefs
                )
            }

            item {
                AppInstanceToggle(
                    title = "SMS & Verification OTPs",
                    subtitle = "One-tap copy pill for transient authentication codes",
                    key = MicrotagPrefs.KEY_OTP,
                    prefs = prefs
                )
            }

            item {
                AppInstanceToggle(
                    title = "Downloads & Progress",
                    subtitle = "Real-time percentage progress bar pills",
                    key = MicrotagPrefs.KEY_DOWNLOADS,
                    prefs = prefs
                )
            }

            item {
                AppInstanceToggle(
                    title = "Proton VPN",
                    subtitle = "Active connection status & connected server node",
                    key = MicrotagPrefs.KEY_PROTON,
                    prefs = prefs
                )
            }

            item {
                AppInstanceToggle(
                    title = "Microsoft Teams",
                    subtitle = "Active call states and scheduled meetings",
                    key = MicrotagPrefs.KEY_TEAMS,
                    prefs = prefs
                )
            }

            item {
                AppInstanceToggle(
                    title = "Google Maps",
                    subtitle = "Turn-by-turn navigation prompt capsules",
                    key = MicrotagPrefs.KEY_MAPS,
                    prefs = prefs
                )
            }

            item {
                AppInstanceToggle(
                    title = "Shizuku Clipboard Hook",
                    subtitle = "Instant evaluation for math, units, and URLs",
                    key = MicrotagPrefs.KEY_CLIPBOARD,
                    prefs = prefs
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun AppInstanceToggle(
    title: String,
    subtitle: String,
    key: String,
    prefs: MicrotagPrefs
) {
    var isEnabled by remember { mutableStateOf(prefs.isAppEnabled(key)) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    isEnabled = checked
                    prefs.setAppEnabled(key, checked)
                }
            )
        }
    }
}

fun checkListenerPermission(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return flat != null && flat.contains(context.packageName)
}

fun checkPostNotifPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun checkShizukuPermission(): Boolean {
    return try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }
}
