package com.naisaru.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private val Forest = Color(0xFF193528)
private val Glass = Color(0xAAE1EDE6)
private val Ink = Color(0xFF183327)

data class LaunchApp(val label: String, val packageName: String, val className: String)
data class HomeItem(
    val id: Long, val kind: String, val label: String, val packageName: String = "",
    val className: String = "", val uri: String = "", val x: Float = 80f, val y: Float = 140f,
    val scale: Float = 1f, val rotation: Float = 0f, val action: String = "none"
)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }
    private var catalog by mutableStateOf(emptyList<LaunchApp>())
    private var home by mutableStateOf(emptyList<HomeItem>())
    private var groups by mutableStateOf(listOf("All", "Social", "Media", "Tools", "Games"))
    private var assigned by mutableStateOf(emptyMap<String, String>())
    private var blur by mutableFloatStateOf(40f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadState()
        loadApps()
        setContent { MaterialTheme { LauncherUI() } }
    }

    private fun loadApps() {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        catalog = packageManager.queryIntentActivities(query, 0).map {
            LaunchApp(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name)
        }.distinctBy { it.packageName + it.className }.sortedBy { it.label.lowercase() }
    }

    private fun launch(app: LaunchApp) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.packageName, app.className))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { loadApps() }
    }

    private fun launchItem(item: HomeItem) {
        if (item.kind == "app") launch(LaunchApp(item.label, item.packageName, item.className))
        if (item.kind == "image" && item.action != "none") {
            catalog.firstOrNull { it.packageName == item.action }?.let(::launch)
        }
    }

    private fun persist() {
        val array = JSONArray()
        home.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("kind", item.kind); put("label", item.label)
                put("package", item.packageName); put("class", item.className); put("uri", item.uri)
                put("x", item.x.toDouble()); put("y", item.y.toDouble())
                put("scale", item.scale.toDouble()); put("rotation", item.rotation.toDouble())
                put("action", item.action)
            })
        }
        prefs.edit().putString("home", array.toString())
            .putString("groups", JSONArray(groups).toString())
            .putString("assigned", JSONObject(assigned).toString())
            .putFloat("blur", blur).apply()
    }

    private fun loadState() {
        try {
            val arr = JSONArray(prefs.getString("home", "[]"))
            home = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HomeItem(o.getLong("id"), o.getString("kind"), o.getString("label"),
                    o.optString("package"), o.optString("class"), o.optString("uri"),
                    o.optDouble("x",80.0).toFloat(), o.optDouble("y",140.0).toFloat(),
                    o.optDouble("scale",1.0).toFloat(),o.optDouble("rotation",0.0).toFloat(),
                    o.optString("action","none"))
            }
            val gs = JSONArray(prefs.getString("groups", "[\"All\",\"Social\",\"Media\",\"Tools\",\"Games\"]"))
            groups = (0 until gs.length()).map { gs.getString(it) }
            val obj = JSONObject(prefs.getString("assigned", "{}"))
            assigned = obj.keys().asSequence().associateWith { obj.getString(it) }
            blur = prefs.getFloat("blur", 40f)
        } catch (_: Exception) { home = emptyList() }
    }

    @Composable
    private fun LauncherUI() {
        var drawer by remember { mutableStateOf(false) }
        var edit by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<Long?>(null) }
        var chosenGroup by remember { mutableStateOf("All") }
        var search by remember { mutableStateOf("") }
        var newGroupDialog by remember { mutableStateOf(false) }
        var groupName by remember { mutableStateOf("") }
        var setting by remember { mutableStateOf(false) }
        var imageActionDialog by remember { mutableStateOf(false) }
        val configuration = LocalConfiguration.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        val widthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                home = home + HomeItem(System.currentTimeMillis(), "image", "Sticker", uri = uri.toString(), x = widthPx * .25f, y = 210f)
                persist()
                edit = true
            }
        }
        val chosen = home.firstOrNull { it.id == selected }
        val backdrop = Color(0xFF2C4B36)

        Box(Modifier.fillMaxSize().background(Forest)) {
            // The initial wallpaper is a gradient-colored surface; user wallpaper integration is in the roadmap.
            Box(Modifier.fillMaxSize().background(backdrop.copy(alpha = .75f)))
            if (!drawer) {
                Text("NAISARU", Modifier.align(Alignment.TopCenter).padding(top = 54.dp),
                    color = Color.White.copy(alpha = .75f), letterSpacing = 6.sp, fontWeight = FontWeight.Light)
                home.forEach { item ->
                    key(item.id) {
                        val isSelected = selected == item.id && edit
                        Box(
                            Modifier.offset { IntOffset(item.x.roundToInt(), item.y.roundToInt()) }
                                .rotate(item.rotation)
                                .size((76 * item.scale).dp)
                                .then(if (isSelected) Modifier.border(1.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                                .pointerInput(item.id, edit) {
                                    if (edit) detectDragGestures { change, delta ->
                                        change.consume()
                                        home = home.map { if (it.id == item.id) it.copy(x = (it.x + delta.x).coerceAtLeast(0f), y = (it.y + delta.y).coerceAtLeast(0f)) else it }
                                    }
                                }
                                .clickable {
                                    if (edit) selected = item.id else launchItem(item)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.kind == "app") {
                                AppIcon(item.packageName, Modifier.fillMaxSize())
                            } else {
                                val bitmap = remember(item.uri) {
                                    try { contentResolver.openInputStream(Uri.parse(item.uri))?.use { android.graphics.BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
                                }
                                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = item.label, modifier = Modifier.fillMaxSize())
                                else Icon(Icons.Default.Image, item.label, tint = Color.White)
                            }
                        }
                    }
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp)
                    .clip(RoundedCornerShape(32.dp)).background(Glass).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundButton(if (edit) Icons.Default.Check else Icons.Default.Edit, "DIY") {
                        edit = !edit; selected = null; persist()
                    }
                    RoundButton(Icons.Default.Apps, "Applications") { drawer = true }
                    RoundButton(Icons.Default.Image, "Add transparent image") { imagePicker.launch(arrayOf("image/png", "image/webp", "image/*")) }
                    RoundButton(Icons.Default.Settings, "Settings") { setting = true }
                }
                if (edit && chosen != null) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 105.dp, start = 20.dp, end = 20.dp)
                        .fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Glass).padding(16.dp)) {
                        Text(chosen.label, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Size", color = Ink)
                        Slider(chosen.scale, onValueChange = { v -> home = home.map { if(it.id == chosen.id) it.copy(scale=v) else it } },
                            valueRange = .4f..2.2f, onValueChangeFinished = ::persist)
                        Text("Rotation", color = Ink)
                        Slider(chosen.rotation, onValueChange = { v -> home = home.map { if(it.id == chosen.id) it.copy(rotation=v) else it } },
                            valueRange = -180f..180f, onValueChangeFinished = ::persist)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { home = home.filterNot { it.id == chosen.id }; selected = null; persist() }) { Text("Delete", color = Ink) }
                            if (chosen.kind == "image") TextButton(onClick = { imageActionDialog = true }) { Text("Tap action", color = Ink) }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(top = 38.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Applications", Modifier.weight(1f), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { drawer = false }) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                    }
                    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search apps") },
                        singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        groups.take(4).forEach { group ->
                            FilterChip(chosenGroup == group, onClick = { chosenGroup = group }, label = { Text(group, maxLines = 1) })
                        }
                        IconButton(onClick = { newGroupDialog = true }) { Icon(Icons.Default.Add, "New group", tint = Color.White) }
                    }
                    val apps = catalog.filter { it.label.contains(search, ignoreCase = true) &&
                        (chosenGroup == "All" || assigned[it.packageName] == chosenGroup) }
                    LazyVerticalGrid(GridCells.Fixed(4), modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(apps, key = { it.packageName + it.className }) { app ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().combinedClickable(
                                    onClick = { if (edit) {
                                        home = home + HomeItem(System.currentTimeMillis(), "app", app.label, app.packageName, app.className,
                                            x = widthPx * .25f, y = 190f)
                                        persist(); drawer = false
                                    } else launch(app) },
                                    onLongClick = {
                                        home = home + HomeItem(System.currentTimeMillis(), "app", app.label, app.packageName, app.className,
                                            x = widthPx * .25f, y = 190f)
                                        persist(); drawer = false; edit = true
                                    }
                                )) {
                                AppIcon(app.packageName, Modifier.size(54.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (chosenGroup != "All") TextButton(onClick = {
                                    assigned = assigned + (app.packageName to chosenGroup); persist()
                                }) { Text("✓", color = Color.White) }
                            }
                        }
                    }
                    Text("Long press an app to pin it to the home screen.", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                }
            }
        }
        if (newGroupDialog) AlertDialog(onDismissRequest = { newGroupDialog = false },
            title = { Text("New group") },
            text = { OutlinedTextField(groupName, { groupName = it }, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = {
                val name = groupName.trim()
                if (name.isNotBlank() && name !in groups) { groups = groups + name; chosenGroup = name; persist() }
                groupName = ""; newGroupDialog = false
            }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { newGroupDialog = false }) { Text("Cancel") } })
        if (imageActionDialog && chosen != null) AlertDialog(onDismissRequest = { imageActionDialog = false },
            title = { Text("Sticker tap action") },
            text = {
                Column {
                    TextButton(onClick = { home = home.map { if(it.id == chosen.id) it.copy(action="none") else it }; persist(); imageActionDialog = false }) { Text("No action (touch passes to home)") }
                    catalog.take(40).forEach { app ->
                        TextButton(onClick = {
                            home = home.map { if(it.id == chosen.id) it.copy(action=app.packageName) else it }
                            persist(); imageActionDialog = false
                        }) { Text(app.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { imageActionDialog = false }) { Text("Close") } })
        if (setting) AlertDialog(onDismissRequest = { setting = false },
            title = { Text("Launcher settings") },
            text = {
                Column {
                    Text("App drawer glass intensity: ${blur.roundToInt()}%")
                    Slider(blur, { blur = it; persist() }, valueRange = 0f..100f)
                    Text("Animation presets, custom icon packs, wallpaper blur and backups are planned for later versions.", fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { setting = false }) { Text("Done") } })
    }

    @Composable
    private fun AppIcon(packageName: String, modifier: Modifier) {
        val icon = remember(packageName) {
            try { packageManager.getApplicationIcon(packageName).toBitmap(160, 160).asImageBitmap() } catch (_: Exception) { null }
        }
        if (icon != null) Image(icon, contentDescription = packageName, modifier = modifier)
        else Icon(Icons.Default.Android, packageName, modifier = modifier, tint = Color.White)
    }

    @Composable
    private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, click: () -> Unit) {
        IconButton(onClick = click, modifier = Modifier.size(45.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .3f))) {
            Icon(icon, label, tint = Ink)
        }
    }
}
