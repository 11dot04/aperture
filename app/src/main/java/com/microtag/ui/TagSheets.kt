package com.microtag.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TagColors.Black,
        contentColor = TagColors.White,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(TagColors.Outline)
            )
        }
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            TagLabel(title, subtitle, TagColors.White, size = 26.sp)
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

@Composable
private fun TagChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) TagColors.Lime else TagColors.Black)
            .border(2.dp, if (selected) TagColors.Lime else TagColors.Outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            style = TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (selected) TagColors.Black else TagColors.White
            )
        )
    }
}

// ==========================================================
// STREAKS: which apps to watch
// ==========================================================

private class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap)

@Composable
private fun rememberLaunchableApps(): State<List<AppEntry>> {
    val ctx = LocalContext.current
    return produceState<List<AppEntry>>(emptyList()) {
        value = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map {
                    AppEntry(
                        label = it.loadLabel(pm).toString(),
                        pkg = it.activityInfo.packageName,
                        icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                    )
                }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }
}

@Composable
fun StreaksSheet(settings: TagSettings, onDismiss: () -> Unit) {
    val apps by rememberLaunchableApps()
    var selected by remember { mutableStateOf(settings.streakApps) }

    TagSheet("Streaks", "pick the apps to watch", onDismiss) {
        if (apps.isEmpty()) {
            Text(
                "Loading your apps",
                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Light, fontSize = 15.sp, color = TagColors.Outline)
            )
        }
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(apps, key = { it.pkg }) { app ->
                val on = app.pkg in selected
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        app.label,
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        maxLines = 1,
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = TagColors.White)
                    )
                    TagSwitch(checked = on, onChange = { v ->
                        selected = if (v) selected + app.pkg else selected - app.pkg
                        settings.streakApps = selected
                    })
                }
            }
        }
    }
}

// ==========================================================
// CALENDAR: lead time and which calendars count
// ==========================================================

private class CalEntry(val id: String, val name: String, val account: String, val color: Int)

private fun loadCalendars(ctx: Context): List<CalEntry> = try {
    ctx.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        ),
        null, null,
        "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                add(CalEntry(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3)))
            }
        }
    } ?: emptyList()
} catch (_: SecurityException) {
    emptyList()
}

private val LeadTimes = listOf(5, 15, 30, 60, 120)

fun hasCalendarPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

@Composable
fun CalendarSheet(settings: TagSettings, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasCalendarPermission(ctx)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var lead by remember { mutableIntStateOf(settings.calendarLeadMin) }
    var excluded by remember { mutableStateOf(settings.calendarExcluded) }
    val calendars by produceState(emptyList<CalEntry>(), granted) {
        value = if (granted) withContext(Dispatchers.IO) { loadCalendars(ctx) } else emptyList()
    }

    TagSheet("Countdown", "starts before each event", onDismiss) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeadTimes.forEach { m ->
                TagChip(intervalLabel(m), selected = lead == m) {
                    lead = m
                    settings.calendarLeadMin = m
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (!granted) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(TagColors.Lime)
                    .clickable { permission.launch(Manifest.permission.READ_CALENDAR) }
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                TagLabel("Allow calendar access", color = TagColors.Black, size = 15.sp)
            }
        } else {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(calendars, key = { it.id }) { cal ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(cal.color)))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(
                                cal.name, maxLines = 1,
                                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = TagColors.White)
                            )
                            Text(
                                cal.account, maxLines = 1,
                                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Light, fontSize = 12.sp, color = TagColors.Outline)
                            )
                        }
                        TagSwitch(checked = cal.id !in excluded, onChange = { on ->
                            excluded = if (on) excluded - cal.id else excluded + cal.id
                            settings.calendarExcluded = excluded
                        })
                    }
                }
            }
        }
    }
}
