package com.microtag.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import com.microtag.listener.MicrotagLogActivity
import com.microtag.rules.RuleEngine
import com.microtag.shizuku.ShizukuClipboardWatcher
import rikka.shizuku.Shizuku

// ==========================================
// DESIGN TOKENS
// Black canvas, one acid-lime accent, mist-grey outlines.
// Swap TagFont for Montserrat (res/font) to match the sketch exactly.
// ==========================================

private val Lime = Color(0xFFC8FF00)
private val Mist = Color(0xFFC5CDDA)
private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFF4F4EE)
private val Charcoal = Color(0xFF1C1C1C)
private val TagFont = FontFamily.SansSerif
private val TileShape = RoundedCornerShape(28.dp)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        RuleEngine.init(applicationContext)
        ShizukuClipboardWatcher.init(applicationContext)

        setContent {
            MicrotagTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    val prefs = remember { MicrotagPrefs(applicationContext) }
                    var onboardingDone by remember { mutableStateOf(prefs.hasCompletedOnboarding) }

                    AnimatedContent(
                        targetState = onboardingDone,
                        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
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

@Composable
fun MicrotagTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime,
            onPrimary = Ink,
            secondary = Mist,
            onSecondary = Ink,
            background = Ink,
            onBackground = Color.White,
            surface = Ink,
            onSurface = Color.White
        ),
        content = content
    )
}

// ==========================================
// SHARED PIECES
// ==========================================

/** Springy press feedback for any tappable surface. Apply before clip/background. */
@Composable
private fun Modifier.springClick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "PressScale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
private fun TagLogo() {
    // Replace with your real wordmark drawable when you have it.
    Text(
        text = "TAG",
        color = Lime,
        fontSize = 46.sp,
        fontFamily = TagFont,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        letterSpacing = (-3).sp
    )
}

private data class AppToggle(
    val title: String,
    val caption: String,
    val key: String,
    val icon: ImageVector
)

// Placeholder glyphs: swap for the real brand vectors via painterResource.
private val ToggleDiscord = AppToggle("Discord", "Calls", MicrotagPrefs.KEY_DISCORD, Icons.Default.Call)
private val ToggleStrava = AppToggle("Strava", "Workout", MicrotagPrefs.KEY_STRAVA, Icons.Default.Favorite)
private val ToggleOtp = AppToggle("SMS & OTPs", "Codes", MicrotagPrefs.KEY_OTP, Icons.Default.Lock)
private val ToggleDownloads = AppToggle("Downloads", "Progress", MicrotagPrefs.KEY_DOWNLOADS, Icons.Default.Notifications)
private val ToggleMaps = AppToggle("Google Maps", "Navigation", MicrotagPrefs.KEY_MAPS, Icons.Default.LocationOn)
private val ToggleTeams = AppToggle("Microsoft Teams", "Calls & meetings", MicrotagPrefs.KEY_TEAMS, Icons.Default.Person)
private val ToggleProton = AppToggle("Proton VPN", "Connection", MicrotagPrefs.KEY_PROTON, Icons.Default.Warning)
private val ToggleClipboard = AppToggle("Shizuku Clipboard", "Math, units, URLs", MicrotagPrefs.KEY_CLIPBOARD, Icons.Default.Star)

private val DefaultGroup = listOf(ToggleOtp, ToggleDownloads, ToggleMaps, ToggleTeams)
private val AllToggles = listOf(
    ToggleDiscord, ToggleStrava, ToggleOtp, ToggleDownloads,
    ToggleMaps, ToggleTeams, ToggleProton, ToggleClipboard
)

// ==========================================
// 1. ONBOARDING
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

    val infinite = rememberInfiniteTransition(label = "Arrow")
    val arrowOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ArrowBounce"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 33.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            TagLogo()
            Spacer(Modifier.height(8.dp))

            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Cutout target",
                tint = Lime,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.End)
                    .offset(y = arrowOffset.dp)
            )
            Text(
                text = buildAnnotatedString {
                    append("YOUR NOTCH,\n")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("UPGRADED.") }
                },
                color = Color.White,
                fontFamily = TagFont,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Microtag lifts background activity into live status-bar capsules.",
                color = Mist,
                fontSize = 15.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            PermissionTile(
                title = "Notification Interception",
                desc = "Catches live events, progress bars and OTPs.",
                isGranted = hasListener,
                actionLabel = "Enable",
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionTile(
                    title = "Post Notifications",
                    desc = "Lets Microtag dock the live status chips.",
                    isGranted = hasNotifPermission,
                    actionLabel = "Allow",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                )
            }
            PermissionTile(
                title = "Privileged Shizuku Hook",
                desc = "Zero-click clipboard capture for math, units and OTPs.",
                isGranted = hasShizuku,
                actionLabel = "Authorize",
                onAction = { if (Shizuku.pingBinder()) Shizuku.requestPermission(1001) }
            )
            Spacer(Modifier.height(8.dp))
        }

        val ctaColor by animateColorAsState(if (hasListener) Lime else Charcoal, label = "Cta")
        Box(
            modifier = Modifier
                .padding(horizontal = 33.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(64.dp)
                .then(if (hasListener) Modifier.springClick(onFinish) else Modifier)
                .clip(RoundedCornerShape(32.dp))
                .background(ctaColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (hasListener) "STEP INSIDE" else "GRANT INTERCEPTION TO CONTINUE",
                color = if (hasListener) Ink else Mist,
                fontFamily = TagFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (hasListener) 20.sp else 14.sp
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
    val bg by animateColorAsState(if (isGranted) Lime else Ink, label = "PermBg")
    val border by animateColorAsState(if (isGranted) Lime else Mist, label = "PermBorder")
    val fg = if (isGranted) Ink else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(bg)
            .border(2.dp, border, TileShape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(desc, color = fg.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        if (isGranted) {
            Icon(Icons.Default.Check, contentDescription = "Granted", tint = Ink, modifier = Modifier.size(30.dp))
        } else {
            Box(
                modifier = Modifier
                    .springClick(onAction)
                    .clip(RoundedCornerShape(50))
                    .background(Mist)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(actionLabel, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
    }
}

// ==========================================
// 2. DASHBOARD
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(prefs: MicrotagPrefs) {
    val context = LocalContext.current
    var interceptCount by remember { mutableStateOf(prefs.interceptCount) }
    var showSettings by remember { mutableStateOf(false) }

    // Single source of truth for every toggle, shared by tiles and the settings sheet.
    val enabled = remember {
        mutableStateMapOf<String, Boolean>().apply {
            AllToggles.forEach { put(it.key, prefs.isAppEnabled(it.key)) }
        }
    }
    val setEnabled: (String, Boolean) -> Unit = { key, value ->
        enabled[key] = value
        prefs.setAppEnabled(key, value)
    }
    val isOn: (AppToggle) -> Boolean = { enabled[it.key] == true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, end = 36.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TagLogo()
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier
                    .size(34.dp)
                    .springClick { showSettings = true }
            )
        }

        Spacer(Modifier.height(24.dp))
        CountMarquee(count = interceptCount)

        Text(
            text = buildAnnotatedString {
                append("INTERCEPTIONS\n& PROMOTIONS\nMADE ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("SO FAR") }
            },
            color = Color.White,
            fontFamily = TagFont,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 36.dp, top = 4.dp)
        )

        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 36.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            SplitPill(
                onPlay = {
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
                onLogs = {
                    context.startActivity(Intent(context, MicrotagLogActivity::class.java))
                }
            )
        }

        Spacer(Modifier.height(32.dp))

        // Bento row 1: Discord | On-by-default group
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 33.dp)
                .height(156.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppTile(
                toggle = ToggleDiscord,
                on = isOn(ToggleDiscord),
                onClick = { setEnabled(ToggleDiscord.key, !isOn(ToggleDiscord)) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            DefaultGroupTile(
                items = DefaultGroup,
                isOn = isOn,
                onToggle = { toggle -> setEnabled(toggle.key, !isOn(toggle)) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Spacer(Modifier.height(16.dp))

        // Bento row 2: Streaks + Weather stacked | Strava
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 33.dp)
                .height(156.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // TODO: point these at real destinations. Extended icons offer Whatshot / Cloud.
                NavRow(
                    label = "Streaks",
                    icon = Icons.Default.Favorite,
                    onClick = { Toast.makeText(context, "Streaks coming soon", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                NavRow(
                    label = "Weather",
                    icon = Icons.Default.Info,
                    onClick = { Toast.makeText(context, "Weather coming soon", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
            AppTile(
                toggle = ToggleStrava,
                on = isOn(ToggleStrava),
                onClick = { setEnabled(ToggleStrava.key, !isOn(ToggleStrava)) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            containerColor = Color(0xFF0E0E0E),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 8.dp)
            ) {
                Text(
                    "CAPSULES",
                    color = Lime,
                    fontFamily = TagFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                AllToggles.forEach { toggle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(toggle.icon, contentDescription = null, tint = Mist, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(toggle.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(toggle.caption, color = Mist, fontSize = 13.sp)
                        }
                        Switch(
                            checked = isOn(toggle),
                            onCheckedChange = { setEnabled(toggle.key, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = Lime,
                                uncheckedThumbColor = Mist,
                                uncheckedTrackColor = Ink,
                                uncheckedBorderColor = Mist
                            )
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Slow-scrolling row of the count: outlined numerals with the occasional solid one. */
@Composable
private fun CountMarquee(count: Int) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = 0, initialFirstVisibleItemScrollOffset = 60)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            if (last != 0L) state.scrollBy(((now - last) / 1_000_000_000f) * 36f)
            last = now
        }
    }
    LazyRow(
        state = state,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        items(100_000) { index ->
            val solid = index % 6 == 1
            Text(
                text = count.toString(),
                style = if (solid) {
                    TextStyle(
                        color = Color.White,
                        fontFamily = TagFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 66.sp
                    )
                } else {
                    TextStyle(
                        color = Color.White,
                        fontFamily = TagFont,
                        fontWeight = FontWeight.Light,
                        fontSize = 66.sp,
                        drawStyle = Stroke(width = 2.5f)
                    )
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = 18.dp)
            )
        }
    }
}

/** Play + Logs as one split pill: big outer radius, tight inner radius. */
@Composable
private fun SplitPill(onPlay: () -> Unit, onLogs: () -> Unit) {
    val big = 50.dp
    val small = 6.dp
    Row(
        modifier = Modifier.height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .width(76.dp)
                .fillMaxHeight()
                .springClick(onPlay)
                .clip(RoundedCornerShape(topStart = big, bottomStart = big, topEnd = small, bottomEnd = small))
                .background(Mist),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Simulate capsule", tint = Ink, modifier = Modifier.size(38.dp))
        }
        Box(
            modifier = Modifier
                .width(112.dp)
                .fillMaxHeight()
                .springClick(onLogs)
                .clip(RoundedCornerShape(topStart = small, bottomStart = small, topEnd = big, bottomEnd = big))
                .background(Mist),
            contentAlignment = Alignment.Center
        ) {
            Text("Logs", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        }
    }
}

/** Outlined when off, filled lime when on. Icon top-left, label bottom-right. */
@Composable
private fun AppTile(
    toggle: AppToggle,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(if (on) Lime else Ink, label = "TileBg")
    val border by animateColorAsState(if (on) Lime else Mist, label = "TileBorder")
    val fg = if (on) Ink else Color.White

    Box(
        modifier = modifier
            .springClick(onClick)
            .clip(TileShape)
            .background(bg)
            .border(2.dp, border, TileShape)
    ) {
        Icon(
            imageVector = toggle.icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
                .size(34.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(toggle.title, color = fg, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1)
            Text(toggle.caption.uppercase(), color = fg, fontWeight = FontWeight.Light, fontSize = 17.sp, maxLines = 1)
        }
    }
}

/** Lime-outlined cluster with a label notched into the top border. */
@Composable
private fun DefaultGroupTile(
    items: List<AppToggle>,
    isOn: (AppToggle) -> Boolean,
    onToggle: (AppToggle) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp)
                .clip(TileShape)
                .border(2.dp, Lime, TileShape)
                .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { toggle ->
                        val on = isOn(toggle)
                        val bg by animateColorAsState(if (on) Paper else Charcoal, label = "IconBg")
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .springClick { onToggle(toggle) }
                                .clip(RoundedCornerShape(20.dp))
                                .background(bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = toggle.icon,
                                contentDescription = toggle.title,
                                tint = if (on) Ink else Color(0xFF6B6B6B),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "ON BY DEFAULT",
            color = Mist,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Ink)
                .padding(horizontal = 6.dp)
        )
    }
}

@Composable
private fun NavRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .springClick(onClick)
            .clip(TileShape)
            .border(2.dp, Mist, TileShape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

// ==========================================
// PERMISSION HELPERS
// ==========================================

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
