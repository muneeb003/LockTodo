package com.locktodo.app

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locktodo.app.ui.LockTodoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so permission-dependent UI re-reads its state. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = TodoStore.get(this)

        setContent {
            LockTodoTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val notifPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { AppSettings.apply(this@MainActivity) }

                LaunchedEffect(Unit) {
                    if (needsNotificationPermission(this@MainActivity)) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        resumeTick = resumeTick,
                        onBack = { showSettings = false },
                        requestNotificationPermission = {
                            if (needsNotificationPermission(this@MainActivity)) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                } else {
                    TodoScreen(store, resumeTick, onOpenSettings = { showSettings = true })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        AppSettings.apply(this) // picks up a freshly granted overlay permission
    }
}

private fun needsNotificationPermission(c: Context) =
    Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun openOverlaySettings(c: Context) {
    c.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))
    )
}

private fun openNotificationAccess(c: Context) {
    c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

private fun previewLockScreen(c: Context) {
    c.startActivity(Intent(c, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ---------------------------------------------------------------- Task list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoScreen(store: TodoStore, resumeTick: Int, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val todos by store.todos.collectAsStateWithLifecycle()
    val sorted = remember(todos) { todos.sortedForDisplay() }
    val doneCount = todos.count { it.done }
    var text by rememberSaveable { mutableStateOf("") }
    val lockViewOn = remember(resumeTick) { AppSettings.lockViewActive(context) }

    fun submit() {
        store.add(text)
        text = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                actions = {
                    if (doneCount > 0) TextButton(onClick = { store.clearDone() }) { Text("Clear done") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!lockViewOn) {
                Card(
                    onClick = onOpenSettings,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Put your tasks on your lock screen", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Turn on the lock screen view and pick your own wallpaper.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a task") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                    Icon(Icons.Default.Add, "Add")
                }
            }

            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(sorted, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            onToggle = { store.toggle(todo.id) },
                            onDelete = { store.delete(todo.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(todo: Todo, onToggle: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = todo.done, onCheckedChange = { onToggle() })
        Text(
            todo.title,
            modifier = Modifier.weight(1f).alpha(if (todo.done) 0.5f else 1f),
            textDecoration = if (todo.done) TextDecoration.LineThrough else null,
            style = MaterialTheme.typography.bodyLarge,
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    resumeTick: Int,
    onBack: () -> Unit,
    requestNotificationPermission: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lockView by remember(resumeTick) { mutableStateOf(AppSettings.lockViewEnabled(context)) }
    var notif by remember(resumeTick) { mutableStateOf(AppSettings.notificationEnabled(context)) }
    val hasOverlay = remember(resumeTick) { AppSettings.hasOverlayPermission(context) }
    val notifBlocked = remember(resumeTick) { needsNotificationPermission(context) }
    var showNotifs by remember(resumeTick) { mutableStateOf(AppSettings.showNotifications(context)) }
    var hideContent by remember(resumeTick) { mutableStateOf(AppSettings.hideNotificationContent(context)) }
    val hasNotifAccess = remember(resumeTick) { NotificationsService.hasAccess(context) }

    val wallpaperVersion by Wallpaper.version.collectAsState()
    val thumb by produceState<ImageBitmap?>(null, wallpaperVersion) {
        value = Wallpaper.load(context, 300, 600)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { Wallpaper.set(context, uri) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("Lock screen")

            SwitchRow(
                title = "Show tasks on lock screen",
                subtitle = "Your to-do list and notifications appear over the lock screen every time you wake your phone. " +
                    "Tick tasks off right there, then swipe up to unlock.",
                checked = lockView,
                onChange = {
                    lockView = it
                    AppSettings.setLockViewEnabled(context, it)
                    if (it && !hasOverlay) openOverlaySettings(context)
                },
            )
            if (lockView && !hasOverlay) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "One more step: allow LockTodo to \"Display over other apps\" so it can open on your lock screen.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { openOverlaySettings(context) }) { Text("Grant permission") }
                    }
                }
            }

            SwitchRow(
                title = "Show my notifications",
                subtitle = "Messages and alerts from your other apps appear under your tasks. " +
                    "Tap one to unlock and open it, or ✕ to dismiss it.",
                checked = showNotifs,
                onChange = {
                    showNotifs = it
                    AppSettings.setShowNotifications(context, it)
                    if (it && !hasNotifAccess) openNotificationAccess(context)
                },
            )
            if (showNotifs && !hasNotifAccess) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Allow LockTodo \"Notification access\" so it can show your notifications on the lock screen view.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { openNotificationAccess(context) }) { Text("Grant access") }
                    }
                }
            }
            if (showNotifs) {
                SwitchRow(
                    title = "Hide message content",
                    subtitle = "Show only the app name, so people near your phone can't read your messages.",
                    checked = hideContent,
                    onChange = {
                        hideContent = it
                        AppSettings.setHideNotificationContent(context, it)
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Wallpaper", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 72.dp, height = 128.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val t = thumb
                    if (t != null) {
                        Image(t, "Current wallpaper", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text("Default", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Choose photo") }
                    if (thumb != null) {
                        OutlinedButton(onClick = { Wallpaper.clear(context) }) { Text("Use default") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { previewLockScreen(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Preview lock screen")
            }

            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Lock screen notification",
                subtitle = "Also list pending tasks in your notifications (visible on the lock screen).",
                checked = notif,
                onChange = {
                    notif = it
                    AppSettings.setNotificationEnabled(context, it)
                    if (it) requestNotificationPermission()
                },
            )
            if (notif && notifBlocked) {
                TextButton(onClick = requestNotificationPermission) { Text("Allow notifications") }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SectionTitle("Home screen widget")
            Text(
                "Shows your tasks on the home screen — tap a task in the widget to tick it off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val widgetManager = remember { AppWidgetManager.getInstance(context) }
            if (widgetManager.isRequestPinAppWidgetSupported) {
                Button(onClick = {
                    widgetManager.requestPinAppWidget(ComponentName(context, TodoWidget::class.java), null, null)
                }) { Text("Add widget to home screen") }
            } else {
                Text(
                    "Long-press your home screen → Widgets → LockTodo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
