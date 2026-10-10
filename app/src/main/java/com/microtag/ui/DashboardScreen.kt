package com.microtag.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import com.microtag.core.MicrotagPrefs
import com.microtag.core.MicrotagReminder

// ==========================================================
// STATE
// ==========================================================

/** One on/off tile backed by MicrotagPrefs. */
@Stable
class PrefToggle(private val prefs: MicrotagPrefs, private val key: String) {
    var on by mutableStateOf(prefs.isAppEnabled(key))
        private set

    fun set(value: Boolean) {
        on = value
        prefs.setAppEnabled(key, value)
    }

    fun toggle() = set(!on)
    val state: TileState get() = if (on) TileState.On else TileState.Off
}

@Composable
fun rememberToggle(prefs: MicrotagPrefs, key: String): PrefToggle = remember(key) { PrefToggle(prefs, key) }

private enum class DashSheet { Streaks, Calendar }

// ==========================================================
// DASHBOARD
// Grid: 4 columns. 1x1 and 2x2 are squares, 2x1 is two cells wide.
// Every tile is placed by (col, row). Frames sit in the gutter.
// ==========================================================

@Composable
fun DashboardScreen(prefs: MicrotagPrefs, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { TagSettings(context) }
    var count by remember { mutableIntStateOf(prefs.interceptCount) }

    val discord = rememberToggle(prefs, MicrotagPrefs.KEY_DISCORD)
    val strava = rememberToggle(prefs, MicrotagPrefs.KEY_STRAVA)
    val streaks = rememberToggle(prefs, TagKeys.STREAKS)
    val weather = rememberToggle(prefs, TagKeys.WEATHER)
    val upi = rememberToggle(prefs, TagKeys.UPI)
    val otp = rememberToggle(prefs, MicrotagPrefs.KEY_OTP)
    val print = rememberToggle(prefs, TagKeys.PRINT)
    val download = rememberToggle(prefs, MicrotagPrefs.KEY_DOWNLOADS)
    val upload = rememberToggle(prefs, TagKeys.UPLOADS)
    val clipboard = rememberToggle(prefs, MicrotagPrefs.KEY_CLIPBOARD)
    val calendar = rememberToggle(prefs, TagKeys.CALENDAR)
    val bluetooth = rememberToggle(prefs, TagKeys.BLUETOOTH)
    val sound = rememberToggle(prefs, TagKeys.SOUND_MODE)
    val songNext = rememberToggle(prefs, TagKeys.SONG_NEXT)
    val ambient = rememberToggle(prefs, TagKeys.AMBIENT_MUSIC)
    val proton = rememberToggle(prefs, MicrotagPrefs.KEY_PROTON)

    var weatherOpen by remember { mutableStateOf(false) }
    var stravaOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<DashSheet?>(null) }
    var weatherIdx by remember {
        mutableIntStateOf(WeatherSteps.indexOf(settings.weatherIntervalMin).coerceAtLeast(0))
    }
    var stravaMetric by remember { mutableStateOf(settings.stravaMetric) }
    var ringerModes by remember { mutableStateOf(settings.ringerModes) }
    val intervalLabels = remember { WeatherSteps.map(::intervalLabel) }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) calendar.set(true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TagColors.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Header(onSettings = onOpenSetup)
        Spacer(Modifier.height(28.dp))
        Hero(
            count = count,
            onSimulate = {
                prefs.incrementInterceptCount()
                count = prefs.interceptCount
                MicrotagReminder.showCapsule(
                    context = context,
                    pillText = "98% • Fast",
                    title = "TAG active",
                    content = "Live Update chip docked to the status bar",
                    notificationId = 9999,
                    timeoutSeconds = 10,
                    iconName = "ic_capsule_bolt"
                )
            },
            onLogs = { context.startActivity(Intent(context, MicrotagLogActivity::class.java)) }
        )
        Spacer(Modifier.height(36.dp))

        // Rows that sit below the weather block shift down by one when it opens.
        val r = if (weatherOpen) 1 else 0
        val b = 4 + r       // first Micro Tags row
        val b2 = b + 3      // earbuds / sound mode
        val b3 = b2 + 2     // music / VPN

        TagGrid {
            // ---- rows 0-1: Discord + Inspect
            TagFrame("Inspect", TagColors.Lime, Modifier.cell(2, 0, 2, 2, outset = 6.dp))
            BigTile(Modifier.cell(0, 0, 2, 2), R.drawable.ic_discord, "Discord", "CALLS", discord)
            InspectTile(Modifier.cell(2, 0), R.drawable.ic_inspect_calc)
            InspectTile(Modifier.cell(3, 0), R.drawable.ic_inspect_colour)
            InspectTile(Modifier.cell(2, 1), R.drawable.ic_inspect_convert)
            InspectTile(Modifier.cell(3, 1), R.drawable.ic_inspect_define)

            // ---- streaks / weather / strava
            if (weatherOpen) {
                WeatherOpenTile(
                    Modifier.cell(0, 2, 2, 2), weather, weatherIdx, intervalLabels,
                    onIndex = { weatherIdx = it; settings.weatherIntervalMin = WeatherSteps[it] },
                    onClose = { weatherOpen = false }
                )
                StravaTile(
                    Modifier.cell(2, 2, 2, 2), strava, stravaOpen, { stravaOpen = !stravaOpen },
                    stravaMetric, { stravaMetric = it; settings.stravaMetric = it }
                )
                PillTile(
                    Modifier.cell(0, 4, 4, 1), R.drawable.ic_streak, "Streaks", null, streaks,
                    chevron = true, onChevron = { sheet = DashSheet.Streaks }
                )
            } else {
                PillTile(
                    Modifier.cell(0, 2, 2, 1), R.drawable.ic_streak, "Streaks", null, streaks,
                    chevron = true, onChevron = { sheet = DashSheet.Streaks }
                )
                PillTile(
                    Modifier.cell(0, 3, 2, 1), R.drawable.ic_weather, "Weather", null, weather,
                    chevron = true, onChevron = { weatherOpen = true }
                )
                StravaTile(
                    Modifier.cell(2, 2, 2, 2), strava, stravaOpen, { stravaOpen = !stravaOpen },
                    stravaMetric, { stravaMetric = it; settings.stravaMetric = it }
                )
            }

            // ---- Micro Tags: small, single-purpose chips
            TagFrame("Micro Tags", TagColors.Outline, Modifier.cell(0, b, 4, 3, outset = 6.dp), labelBias = 0.55f)
            MicroTile(Modifier.cell(0, b, 2, 1), R.drawable.ic_upi_rupee, "UPI", "Credit", upi)
            IconTile(Modifier.cell(2, b), R.drawable.ic_download, download)
            IconTile(Modifier.cell(3, b), R.drawable.ic_upload, upload)
            MicroTile(Modifier.cell(0, b + 1, 2, 1), R.drawable.ic_otp, "OTP", "2FA", otp)
            MicroTile(Modifier.cell(2, b + 1, 2, 1), R.drawable.ic_print, "Print", "in Progress", print, inline = true)
            MicroTile(Modifier.cell(0, b + 2, 2, 1), R.drawable.ic_clipboard, "Clipboard", "Context", clipboard, size = 16.sp)
            PillTile(
                Modifier.cell(2, b + 2, 2, 1), R.drawable.ic_calendar_countdown, "Events", "countdown", calendar,
                shape = TagShapes.Rect, size = 14.sp, chevron = true,
                onToggle = {
                    if (!calendar.on && !hasCalendarPermission(context)) {
                        calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    } else calendar.toggle()
                },
                onChevron = { sheet = DashSheet.Calendar }
            )

            // ---- earbuds + sound mode
            TagTile(Modifier.cell(0, b2, 2, 2), TagShapes.Circle, bluetooth.state, bluetooth::toggle) { fg ->
                TagIcon(R.drawable.ic_earbuds, fg, Modifier.align(Alignment.Center), 84.dp)
            }
            PillTile(Modifier.cell(2, b2, 2, 1), R.drawable.ic_sound, "Sound", "Mode", sound)
            RingerSelector(
                Modifier.cell(2, b2 + 1, 2, 1), enabled = sound.on, modes = ringerModes,
                onChange = { ringerModes = it; settings.ringerModes = it }
            )

            // ---- music + VPN
            TagTile(Modifier.cell(0, b3, 2, 1), TagShapes.CapRight, songNext.state, songNext::toggle) { fg ->
                Row(
                    Modifier.fillMaxSize().padding(start = 22.dp, end = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TagLabel("Song", "Up Next", fg, Modifier.weight(1f))
                    TagIcon(R.drawable.ic_song_next, fg, size = 30.dp)
                }
            }
            TagTile(Modifier.cell(0, b3 + 1, 2, 1), TagShapes.CapLeft, ambient.state, ambient::toggle) { fg ->
                Row(
                    Modifier.fillMaxSize().padding(start = 26.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TagIcon(R.drawable.ic_ambient_music, fg, size = 30.dp)
                    Spacer(Modifier.width(14.dp))
                    TagLabel("Ambient", "Music Mod", fg, Modifier.weight(1f), size = 16.sp)
                }
            }
            BigTile(Modifier.cell(2, b3, 2, 2), R.drawable.ic_vpn, "ProtonVPN", "SESSIONS", proton)
        }
        Spacer(Modifier.height(40.dp))
    }

    when (sheet) {
        DashSheet.Streaks -> StreaksSheet(settings) { sheet = null }
        DashSheet.Calendar -> CalendarSheet(settings) { sheet = null }
        null -> Unit
    }
}

// ==========================================================
// HEADER + HERO
// ==========================================================

@Composable
private fun Header(onSettings: () -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_tag_wordmark),
            contentDescription = "TAG",
            modifier = Modifier.height(38.dp).aspectRatio(162f / 60f)
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable { view.tick(Tick.Press); onSettings() },
            contentAlignment = Alignment.Center
        ) { TagIcon(R.drawable.ic_settings, TagColors.White, size = 32.dp) }
    }
}

@Composable
private fun Hero(count: Int, onSimulate: () -> Unit, onLogs: () -> Unit) {
    // INTERCEPTIONS sets the size; everything else follows it so the block stays one unit.
    var fit by remember { mutableFloatStateOf(56f) }
    fun style(weight: FontWeight, color: Color) = TextStyle(
        fontFamily = Montserrat,
        fontWeight = weight,
        fontSize = fit.sp,
        lineHeight = (fit * 1.08f).sp,
        letterSpacing = (-1).sp,
        color = color
    )
    Column(Modifier.fillMaxWidth()) {
        Text(count.toString(), style = style(FontWeight.Black, TagColors.White), maxLines = 1)
        Text(
            "INTERCEPTIONS",
            style = style(FontWeight.Black, TagColors.White),
            maxLines = 1,
            softWrap = false,
            onTextLayout = { if (it.didOverflowWidth && fit > 20f) fit *= 0.95f }
        )
        Text(
            "SO FAR",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
            maxLines = 1,
            style = style(FontWeight.Light, TagColors.Outline).copy(drawStyle = Stroke(width = 2f))
        )
        Spacer(Modifier.height(24.dp))
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            SplitButton(TagShapes.JoinLeft, onSimulate) { TagIcon(R.drawable.ic_play, TagColors.Black, size = 26.dp) }
            SplitButton(TagShapes.JoinRight, onLogs) {
                Text(
                    "Logs",
                    style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 20.sp, color = TagColors.Black)
                )
            }
        }
    }
}

@Composable
private fun SplitButton(shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit, content: @Composable () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier
            .height(54.dp)
            .clip(shape)
            .background(TagColors.Steel)
            .clickable { view.tick(Tick.Press); onClick() }
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ==========================================================
// TILES
// ==========================================================

@Composable
private fun BigTile(modifier: Modifier, icon: Int, title: String, sub: String, t: PrefToggle) {
    TagTile(modifier, TagShapes.Big, t.state, t::toggle) { fg ->
        TagIcon(icon, fg, Modifier.align(Alignment.TopStart).padding(20.dp), 30.dp)
        BigLabel(title, sub, fg, Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }
}

@Composable
private fun BigLabel(title: String, sub: String, fg: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(title, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = fg))
        Text(sub, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Light, fontSize = 14.sp, letterSpacing = 0.3.sp, color = fg))
    }
}

/** Label left, icon right. The Micro Tags look. */
@Composable
private fun MicroTile(
    modifier: Modifier, icon: Int, bold: String, light: String?, t: PrefToggle,
    inline: Boolean = false, size: TextUnit = 17.sp
) {
    TagTile(modifier, TagShapes.Rect, t.state, t::toggle) { fg ->
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            TagLabel(bold, light, fg, Modifier.weight(1f), size, inline = inline)
            TagIcon(icon, fg, size = 30.dp)
        }
    }
}

@Composable
private fun IconTile(modifier: Modifier, icon: Int, t: PrefToggle) {
    TagTile(modifier, TagShapes.Rect, t.state, t::toggle) { fg ->
        TagIcon(icon, fg, Modifier.align(Alignment.Center), 32.dp)
    }
}

@Composable
private fun InspectTile(modifier: Modifier, icon: Int) {
    TagTile(modifier, RoundedCornerShape(24.dp), TileState.Static) { fg ->
        TagIcon(icon, fg, Modifier.align(Alignment.Center), 34.dp)
    }
}

/** Icon left, label, optional chevron. Tapping the body toggles; tapping the chevron opens options. */
@Composable
private fun PillTile(
    modifier: Modifier, icon: Int, bold: String, light: String?, t: PrefToggle,
    shape: androidx.compose.ui.graphics.Shape = TagShapes.Pill,
    size: TextUnit = 17.sp,
    chevron: Boolean = false,
    open: Boolean = false,
    onToggle: () -> Unit = t::toggle,
    onChevron: () -> Unit = {}
) {
    TagTile(modifier, shape, t.state, onToggle) { fg ->
        Row(
            Modifier.fillMaxSize().padding(start = if (chevron && light != null) 16.dp else 22.dp, end = if (chevron) 6.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TagIcon(icon, fg, size = if (light != null && chevron) 22.dp else 26.dp)
            Spacer(Modifier.width(if (light != null && chevron) 8.dp else 12.dp))
            TagLabel(bold, light, fg, Modifier.weight(1f), size)
            if (chevron) ChevronButton(open, fg, onClick = onChevron)
        }
    }
}

@Composable
private fun WeatherOpenTile(
    modifier: Modifier, t: PrefToggle, index: Int, labels: List<String>,
    onIndex: (Int) -> Unit, onClose: () -> Unit
) {
    TagTile(modifier, TagShapes.Big, t.state, t::toggle) { fg ->
        val face = if (t.on) TagColors.Lime else TagColors.Black
        Column(Modifier.fillMaxSize().padding(start = 18.dp, top = 6.dp, bottom = 14.dp, end = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagIcon(R.drawable.ic_weather, fg, size = 24.dp)
                Spacer(Modifier.width(10.dp))
                TagLabel("Weather", "every ${labels[index]}", fg, Modifier.weight(1f), 15.sp)
                ChevronButton(open = true, tint = fg, onClick = onClose)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(end = 12.dp), contentAlignment = Alignment.Center) {
                TagDial(labels, index, onIndex, body = fg, face = face, modifier = Modifier.fillMaxHeight().aspectRatio(1f))
            }
        }
    }
}

@Composable
private fun StravaTile(
    modifier: Modifier, t: PrefToggle, open: Boolean, onOpen: () -> Unit,
    metric: String, onMetric: (String) -> Unit
) {
    TagTile(modifier, TagShapes.Big, t.state, t::toggle) { fg ->
        val face = if (t.on) TagColors.Lime else TagColors.Black
        if (!open) {
            TagIcon(R.drawable.ic_strava, fg, Modifier.align(Alignment.TopStart).padding(20.dp), 30.dp)
            BigLabel("Strava", "WORKOUTS", fg, Modifier.align(Alignment.BottomEnd).padding(20.dp))
        } else {
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 52.dp, end = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("pace" to "Pace", "time" to "Time", "distance" to "Distance").forEach { (key, label) ->
                    val selected = key == metric
                    val view = LocalView.current
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(if (selected) fg else Color.Transparent)
                            .border(2.dp, fg, CircleShape)
                            .clickable { view.tick(Tick.Press); onMetric(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = TextStyle(
                                fontFamily = Montserrat, fontWeight = FontWeight.Bold,
                                fontSize = 14.sp, color = if (selected) face else fg
                            )
                        )
                    }
                }
            }
            TagLabel("Strava", "chip shows", fg, Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 12.dp), 14.sp, inline = true)
        }
        ChevronButton(open, fg, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp), onClick = onOpen)
    }
}

/**
 * Doubles as the "modes to show" picker for Sound Mode.
 * A dot is a mode that stays quiet; a lime glyph is a mode that announces itself.
 */
@Composable
private fun RingerSelector(modifier: Modifier, enabled: Boolean, modes: Set<String>, onChange: (Set<String>) -> Unit) {
    val view = LocalView.current
    TagTile(modifier, TagShapes.Pill, TileState.Off) { fg ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }
        ) {
            listOf(
                "ring" to R.drawable.ic_ringer_ring,
                "vibrate" to R.drawable.ic_ringer_vibrate,
                "silent" to R.drawable.ic_ringer_silent
            ).forEach { (key, icon) ->
                val on = key in modes
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { view.tick(if (on) Tick.Off else Tick.On); onChange(if (on) modes - key else modes + key) },
                    contentAlignment = Alignment.Center
                ) {
                    if (on) TagIcon(icon, TagColors.Lime, size = 30.dp)
                    else Box(Modifier.size(10.dp).clip(CircleShape).background(fg))
                }
            }
        }
    }
}
