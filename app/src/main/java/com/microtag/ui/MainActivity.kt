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
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder
import com.microtag.rules.RuleEngine
import com.microtag.listener.MicrotagLogActivity
import com.microtag.shizuku.ShizukuClipboardWatcher
import rikka.shizuku.Shizuku
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RuleEngine.init(applicationContext)
        ShizukuClipboardWatcher.init(applicationContext)

        setContent {
            MicrotagTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs = remember { MicrotagPrefs(applicationContext) }
                    var onboardingDone by remember { mutableStateOf(prefs.hasCompletedOnboarding) }

                    AnimatedContent(
                        targetState = onboardingDone,
                        transitionSpec = {
                            (fadeIn(tween(350)) + scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                            )) togetherWith fadeOut(tween(200))
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
// THEME · Material 3 Expressive language
// Bold type, oversized asymmetric shapes, tonal colour blocking, springy motion.
// ==========================================

@Composable
fun MicrotagTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = ExpressiveTypography,
        content = content
    )
}

private val ExpressiveTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-2).sp),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold)
    )
}

/** Scalloped "cookie" shape, the wavy circle seen in the Expressive breathing screen. */
class ScallopShape(
    private val lobes: Int = 12,
    private val amplitude: Float = 0.07f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = min(size.width, size.height) / 2f * (1f - amplitude)
        val steps = 240
        for (i in 0..steps) {
            val theta = (2.0 * PI * i / steps).toFloat()
            val r = base * (1f + amplitude * cos(lobes * theta))
            val x = cx + r * cos(theta)
            val y = cy + r * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

// Asymmetric "bento" corner sets, big rounds against tight corners.
private val BentoA = RoundedCornerShape(topStart = 44.dp, topEnd = 14.dp, bottomEnd = 44.dp, bottomStart = 14.dp)
private val BentoB = RoundedCornerShape(topStart = 14.dp, topEnd = 44.dp, bottomEnd = 14.dp, bottomStart = 44.dp)
private val BentoC = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp, bottomEnd = 14.dp, bottomStart = 44.dp)
private val BentoD = RoundedCornerShape(topStart = 14.dp, topEnd = 44.dp, bottomEnd = 44.dp, bottomStart = 44.dp)
private val BentoShapes = listOf(BentoA, BentoB, BentoC, BentoD)

private data class TonePair(val container: Color, val content: Color)

@Composable
private fun tonePalette(): List<TonePair> {
    val c = MaterialTheme.colorScheme
    return listOf(
        TonePair(c.primaryContainer, c.onPrimaryContainer),
        TonePair(c.tertiaryContainer, c.onTertiaryContainer),
        TonePair(c.secondaryContainer, c.onSecondaryContainer),
        TonePair(c.inversePrimary, c.onSurface)
    )
}

/** Button with a bouncy, squishy press response. */
@Composable
fun SpringButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "PressScale"
    )
    val padding = PaddingValues(horizontal = 22.dp, vertical = 18.dp)
    val animated = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    if (tonal) {
        FilledTonalButton(
            onClick = onClick,
            modifier = animated,
            shape = shape,
            interactionSource = source,
            contentPadding = padding
        ) { content() }
    } else {
        Button(
            onClick = onClick,
            modifier = animated,
            shape = shape,
            interactionSource = source,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors()
        ) { content() }
    }
}

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

    val infinite = rememberInfiniteTransition(label = "Onboarding")
    val arrowOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ArrowBounce"
    )
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
        label = "BadgeSpin"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Hero pointing at the punch-hole
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BentoC)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 56.dp, y = (-56).dp)
                        .graphicsLayer { rotationZ = spin }
                        .clip(ScallopShape(lobes = 10))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                )
                Column {
                    Column(
                        modifier = Modifier.offset(y = arrowOffset.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Cutout target",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Eyes up here",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Your notch,\nupgraded.",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 50.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Microtag lifts background activity into live status-bar capsules.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            PermissionTile(
                title = "Notification Interception",
                desc = "Catches live events, progress bars and OTPs.",
                isGranted = hasListener,
                actionLabel = "Enable",
                tone = 1,
                shape = BentoA,
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
                    tone = 2,
                    shape = BentoB,
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
                desc = "Zero-click clipboard capture for math, units and OTPs.",
                isGranted = hasShizuku,
                actionLabel = "Authorize",
                tone = 0,
                shape = BentoD,
                onAction = {
                    if (Shizuku.pingBinder()) Shizuku.requestPermission(1001)
                }
            )
            Spacer(Modifier.height(4.dp))
        }

        SpringButton(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(68.dp),
            shape = RoundedCornerShape(34.dp)
        ) {
            Text(
                text = if (hasListener) "Step inside" else "Grant interception to continue",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
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
    onAction: () -> Unit,
    tone: Int = 0,
    shape: Shape = BentoA
) {
    val palette = tonePalette()[tone % 3]
    val container by animateColorAsState(
        targetValue = if (isGranted) palette.container else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "TileColor"
    )
    val content = if (isGranted) palette.content else MaterialTheme.colorScheme.onSurface

    Surface(color = container, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = content)
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            if (isGranted) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ScallopShape(lobes = 8))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                SpringButton(
                    onClick = onAction,
                    tonal = true,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ==========================================
// 2. DASHBOARD
// ==========================================

private data class AppToggle(
    val title: String,
    val subtitle: String,
    val key: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val appToggles = listOf(
    AppToggle("Strava & Workouts", "Live split pace and elapsed timer", MicrotagPrefs.KEY_STRAVA, Icons.Default.Favorite),
    AppToggle("Discord", "Priority @mentions and DMs", MicrotagPrefs.KEY_DISCORD, Icons.Default.Email),
    AppToggle("SMS & OTPs", "One-tap copy pill for codes", MicrotagPrefs.KEY_OTP, Icons.Default.Lock),
    AppToggle("Downloads", "Real-time progress pills", MicrotagPrefs.KEY_DOWNLOADS, Icons.Default.Notifications),
    AppToggle("Proton VPN", "Connection status and server node", MicrotagPrefs.KEY_PROTON, Icons.Default.Warning),
    AppToggle("Microsoft Teams", "Active calls and meetings", MicrotagPrefs.KEY_TEAMS, Icons.Default.Phone),
    AppToggle("Google Maps", "Turn-by-turn capsules", MicrotagPrefs.KEY_MAPS, Icons.Default.LocationOn),
    AppToggle("Shizuku Clipboard", "Instant math, units and URLs", MicrotagPrefs.KEY_CLIPBOARD, Icons.Default.Star)
)

@Composable
fun DashboardScreen(prefs: MicrotagPrefs) {
    val context = LocalContext.current
    var interceptCount by remember { mutableStateOf(prefs.interceptCount) }

    val infinite = rememberInfiniteTransition(label = "Dashboard")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)),
        label = "HeroSpin"
    )

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "microtag",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer { rotationZ = spin }
                            .clip(ScallopShape(lobes = 9))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            // Hero metric
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(BentoC)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 90.dp, y = (-70).dp)
                            .graphicsLayer { rotationZ = spin }
                            .clip(ScallopShape(lobes = 12))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
                    )
                    Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp)) {
                        Text(
                            text = "DOCKED & PARSED",
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = interceptCount.toString(),
                            fontSize = 96.sp,
                            lineHeight = 100.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "notifications turned into capsules",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpringButton(
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
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(
                            topStart = 40.dp, bottomStart = 40.dp, topEnd = 12.dp, bottomEnd = 12.dp
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Simulate", fontWeight = FontWeight.ExtraBold)
                    }
                    SpringButton(
                        onClick = {
                            context.startActivity(Intent(context, MicrotagLogActivity::class.java))
                        },
                        modifier = Modifier.weight(1f),
                        tonal = true,
                        shape = RoundedCornerShape(
                            topStart = 12.dp, bottomStart = 12.dp, topEnd = 40.dp, bottomEnd = 40.dp
                        )
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Log", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // Section title
            item {
                Text(
                    text = "Capsules",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                )
            }

            // Bento grid of app toggles: rows of two with alternating widths
            val rows = appToggles.chunked(2)
            itemsIndexed(rows) { rowIndex, pair ->
                val wide = if (rowIndex % 2 == 0) 1.3f else 1f
                val narrow = if (rowIndex % 2 == 0) 1f else 1.3f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEachIndexed { colIndex, item ->
                        val globalIndex = rowIndex * 2 + colIndex
                        AppInstanceToggle(
                            item = item,
                            prefs = prefs,
                            tone = globalIndex % 3,
                            shape = BentoShapes[globalIndex % BentoShapes.size],
                            modifier = Modifier
                                .weight(if (colIndex == 0) wide else narrow)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun AppInstanceToggle(
    item: AppToggle,
    prefs: MicrotagPrefs,
    tone: Int,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    var isEnabled by remember { mutableStateOf(prefs.isAppEnabled(item.key)) }
    val palette = tonePalette()[tone % 3]

    val container by animateColorAsState(
        targetValue = if (isEnabled) palette.container else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "ToggleColor"
    )
    val content by animateColorAsState(
        targetValue = if (isEnabled) palette.content else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "ToggleContent"
    )

    Surface(
        shape = shape,
        color = container,
        modifier = modifier.heightIn(min = 168.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(if (isEnabled) ScallopShape(lobes = 8) else CircleShape)
                        .background(content.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = content)
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    isEnabled = checked
                    prefs.setAppEnabled(item.key, checked)
                },
                thumbContent = if (isEnabled) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else null
            )
        }
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
