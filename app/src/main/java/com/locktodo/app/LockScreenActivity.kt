package com.locktodo.app

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The to-do screen that appears over the phone's lock screen, on your own wallpaper. */
class LockScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val store = TodoStore.get(this)
        val showNotifs = AppSettings.showNotifications(this)
        setContent {
            LockScreenView(
                store = store,
                showNotifications = showNotifs,
                onUnlock = { unlock() },
                onOpenNotification = { openNotification(it) },
            )
        }
    }

    /** Hands over to the system lock screen (PIN / fingerprint / swipe) and gets out of the way. */
    private fun unlock(then: (() -> Unit)? = null) {
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    then?.invoke()
                    finish()
                }
            })
        } else {
            then?.invoke()
            finish()
        }
    }

    /** Unlocks the phone, then opens the app the notification came from. */
    private fun openNotification(n: LockNotif) {
        unlock {
            runCatching {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 34) {
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                n.contentIntent?.send(this, 0, null, null, null, null, options.toBundle())
            }
            if (n.autoCancel) NotificationsService.dismiss(n.key)
        }
    }
}

@Composable
private fun LockScreenView(
    store: TodoStore,
    showNotifications: Boolean,
    onUnlock: () -> Unit,
    onOpenNotification: (LockNotif) -> Unit,
) {
    val context = LocalContext.current
    val todos by store.todos.collectAsState()
    val notifs by NotificationHub.items.collectAsState()
    val hasAccess = remember { NotificationsService.hasAccess(context) }
    val wallpaperVersion by Wallpaper.version.collectAsState()
    val metrics = context.resources.displayMetrics
    val wallpaper by produceState<ImageBitmap?>(null, wallpaperVersion) {
        value = Wallpaper.load(context, metrics.widthPixels, metrics.heightPixels)
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val time = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }

    val sorted = remember(todos) { todos.sortedForDisplay() }
    val pending = todos.count { !it.done }
    val visibleNotifs = if (showNotifications && hasAccess) notifs else emptyList()

    var dragTotal by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onVerticalDrag = { _, dy -> dragTotal += dy },
                    onDragEnd = { if (dragTotal < -250f) onUnlock() },
                )
            }
    ) {
        // Background: your photo, or a default gradient.
        val bmp = wallpaper
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF4A148C), Color(0xFF880E4F)))
                )
            )
        }
        // Scrim so text stays readable on bright photos.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.6f))
                )
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Text(time, color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(date, color = Color.White.copy(alpha = 0.9f), fontSize = 22.sp)
            Spacer(Modifier.height(24.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                // ---- Tasks
                item(key = "tasks") {
                    Surface(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(vertical = 20.dp)) {
                            Text(
                                text = when (pending) {
                                    0 -> "All done ✓"
                                    1 -> "1 task left"
                                    else -> "$pending tasks left"
                                },
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                            )
                            if (sorted.isEmpty()) {
                                Text(
                                    "Nothing to do — add tasks in the app.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 17.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                )
                            }
                            sorted.forEach { todo -> LockRow(todo) { store.toggle(todo.id) } }
                        }
                    }
                }

                // ---- Notifications from other apps
                if (visibleNotifs.isNotEmpty()) {
                    item(key = "notif-header") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp, top = 18.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Notifications, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Notifications",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (visibleNotifs.any { it.clearable }) {
                                TextButton(onClick = { NotificationsService.dismissAll() }) {
                                    Text("Clear all", color = Color.White)
                                }
                            }
                        }
                    }
                    items(visibleNotifs, key = { it.key }) { n ->
                        NotifCard(
                            n = n,
                            now = now,
                            onOpen = { onOpenNotification(n) },
                            onDismiss = { NotificationsService.dismiss(n.key) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = 32.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Text("Swipe up or tap to unlock", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun LockRow(todo: Todo, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (todo.done) {
            Icon(Icons.Default.CheckCircle, "Done", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
        } else {
            Box(Modifier.size(32.dp).padding(2.dp).border(2.5.dp, Color.White, CircleShape))
        }
        Spacer(Modifier.width(18.dp))
        Text(
            todo.title,
            color = if (todo.done) Color.White.copy(alpha = 0.55f) else Color.White,
            fontSize = 21.sp,
            textDecoration = if (todo.done) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NotifCard(
    n: LockNotif,
    now: Long,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ago = remember(n.time, now / 60_000) {
        DateUtils.getRelativeTimeSpanString(n.time, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.clickable(onClick = onOpen).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val icon = n.appIcon
            if (icon != null) {
                Image(icon, null, Modifier.size(36.dp).clip(CircleShape))
            } else {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${n.appName} · $ago",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    n.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (n.text.isNotBlank()) {
                    Text(
                        n.text,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (n.clearable) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss", tint = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}
