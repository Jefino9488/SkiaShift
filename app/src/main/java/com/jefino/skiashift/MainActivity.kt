package com.jefino.skiashift

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private fun exportConfig(context: Context) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(SkiaShiftProvider.PREFS_NAME, Context.MODE_PRIVATE)
            val json = org.json.JSONObject()
            for ((key, value) in prefs.all) {
                json.put(key, value.toString())
            }
            val jsonString = json.toString()
            
            val cacheFile = java.io.File(context.cacheDir, "config.json")
            cacheFile.writeText(jsonString)
            
            val globalRenderer = prefs.getString(SkiaShiftProvider.KEY_GLOBAL_RENDERER, "skiavk") ?: "skiavk"
            val isVk = globalRenderer == "skiavk"
            
            val setProps = """
                resetprop -n debug.hwui.renderer $globalRenderer
                resetprop -n debug.renderengine.backend ${if (isVk) "skiavkthreaded" else "skiaglthreaded"}
                resetprop -n debug.hwui.use_buffer_age ${if (isVk) "true" else "false"}
                resetprop -n debug.hwui.skia_use_perf_hint true
                resetprop -n ro.hwui.use_vulkan ${if (isVk) "true" else "false"}
                ${if (isVk) "resetprop -n renderthread.skia.reduceopstasksplitting true" else "resetprop -n --delete renderthread.skia.reduceopstasksplitting"}
            """.trimIndent()
            
            val cacheScript = java.io.File(context.cacheDir, "apply_props.sh")
            cacheScript.writeText(setProps)
            
            val cmd = "cp ${cacheFile.absolutePath} /data/local/tmp/skiashift_config.json && chmod 0644 /data/local/tmp/skiashift_config.json && cp ${cacheScript.absolutePath} /data/adb/post-fs-data.d/skiashift_props.sh && chmod 0755 /data/adb/post-fs-data.d/skiashift_props.sh && sh /data/adb/post-fs-data.d/skiashift_props.sh"
            
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Config Exported & Global Applied!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun importConfig(context: Context) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val cmd = "cat /data/local/tmp/skiashift_config.json"
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val jsonString = p.inputStream.bufferedReader().readText()
            p.waitFor()

            if (jsonString.isNotEmpty()) {
                val json = org.json.JSONObject(jsonString)
                val prefs = context.getSharedPreferences(SkiaShiftProvider.PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.clear()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    editor.putString(key, json.getString(key))
                }
                editor.commit()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Config Imported Successfully!", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No saved config found to import", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error importing config", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MiuixTheme(colors = colors) {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(SkiaShiftProvider.PREFS_NAME, Context.MODE_PRIVATE)
    
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var globalRenderer by remember { mutableStateOf(prefs.getString(SkiaShiftProvider.KEY_GLOBAL_RENDERER, "skiavk") ?: "skiavk") }

    var hasRoot by remember { mutableStateOf(true) }
    var showLsposedWarning by remember { mutableStateOf(true) }

    val tabs = listOf("User Apps", "System Apps")
    var selectedTab by remember { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            val map = mutableMapOf<String, String>()
            for ((k, v) in prefs.all) {
                map[k] = v.toString()
            }
            val jsonString = org.json.JSONObject(map as Map<*, *>).toString(4)
            context.contentResolver.openOutputStream(it)?.use { out ->
                out.write(jsonString.toByteArray())
            }
            coroutineScope.launch { snackbarHostState.showSnackbar("Config Exported Successfully", withDismissAction = true) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val jsonString = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
                if (!jsonString.isNullOrEmpty()) {
                    val json = org.json.JSONObject(jsonString)
                    val editor = prefs.edit()
                    editor.clear()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        editor.putString(key, json.getString(key))
                    }
                    editor.commit()
                    globalRenderer = prefs.getString(SkiaShiftProvider.KEY_GLOBAL_RENDERER, "skiavk") ?: "skiavk"
                    coroutineScope.launch { snackbarHostState.showSnackbar("Config Imported Successfully", withDismissAction = true) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                coroutineScope.launch { snackbarHostState.showSnackbar("Error importing config", withDismissAction = true) }
            }
        }
    }

    val exportLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -d -v threadtime"))
                    val reader = p.inputStream.bufferedReader()
                    val stringBuilder = java.lang.StringBuilder()
                    reader.forEachLine { line ->
                        if (line.contains("SkiaShift", ignoreCase = true) || 
                            line.contains("HWUI", ignoreCase = true) || 
                            line.contains("RenderThread", ignoreCase = true)) {
                            stringBuilder.append(line).append("\n")
                        }
                    }
                    p.waitFor()
                    val logs = stringBuilder.toString()

                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(logs.toByteArray())
                    }
                    launch(Dispatchers.Main) { snackbarHostState.showSnackbar("Logs Exported Successfully", withDismissAction = true) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    launch(Dispatchers.Main) { snackbarHostState.showSnackbar("Error exporting logs", withDismissAction = true) }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                hasRoot = p.waitFor() == 0
            } catch (e: Exception) {
                e.printStackTrace()
                hasRoot = false
            }
        }
        apps = AppManager.getInstalledApps(context)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(title = "SkiaShift")
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    exportConfig(context)
                    coroutineScope.launch { snackbarHostState.showSnackbar("Root Config Applied", withDismissAction = true) }
                }
            ) {
                Text("Save", modifier = Modifier.padding(horizontal = 16.dp), color = Color.White)
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            
            var expandedGlobal by remember { mutableStateOf(false) }

            // Global Settings Card
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.defaultColors()
            ) {
                Column {
                    Box {
                        BasicComponent(
                            title = "Global Default Renderer",
                            summary = "Applied to apps with 'Default' setting",
                            endActions = {
                                Text(
                                    text = if (globalRenderer == "skiavk") "Vulkan" else "OpenGL",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                DropdownArrowEndAction(
                                    actionColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            },
                            onClick = { expandedGlobal = true }
                        )

                        OverlayListPopup(
                            show = expandedGlobal,
                            onDismissRequest = { expandedGlobal = false }
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    text = "Vulkan",
                                    optionSize = 2,
                                    isSelected = globalRenderer == "skiavk",
                                    index = 0,
                                    onSelectedIndexChange = {
                                        globalRenderer = "skiavk"
                                        prefs.edit().putString(SkiaShiftProvider.KEY_GLOBAL_RENDERER, "skiavk").commit()
                                        expandedGlobal = false
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Global config saved", withDismissAction = true) }
                                    }
                                )
                                DropdownImpl(
                                    text = "OpenGL",
                                    optionSize = 2,
                                    isSelected = globalRenderer == "skiagl",
                                    index = 1,
                                    onSelectedIndexChange = {
                                        globalRenderer = "skiagl"
                                        prefs.edit().putString(SkiaShiftProvider.KEY_GLOBAL_RENDERER, "skiagl").commit()
                                        expandedGlobal = false
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Global config saved", withDismissAction = true) }
                                    }
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { exportLauncher.launch("skiashift_config.json") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors()
                        ) {
                            Text("Export", color = MiuixTheme.colorScheme.onSurface)
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            Text("Import", color = Color.White)
                        }
                    }

                    if (BuildConfig.DEBUG) {
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
                            Button(
                                onClick = { exportLogLauncher.launch("skiashift_debug.log") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text("Export Debug Logs", color = Color.White)
                            }
                        }
                    }
                }
            }

            // Warning
            if (!hasRoot) {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = Color(0xFFF8D7DA))
                ) {
                    Text(
                        text = "Root Permission Missing. Please grant root access for SkiaShift to apply configuration.",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF721C24),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (showLsposedWarning) {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = Color(0xFFFFF3CD))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Global renderer is applied natively to the whole system on boot. Use LSPosed to check apps you want to force onto a different renderer.",
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF856404),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "✕",
                            modifier = Modifier.padding(start = 8.dp).clickable { showLsposedWarning = false },
                            color = Color(0xFF856404),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs
            TabRow(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            var searchQuery by remember { mutableStateOf("") }
            SearchBar(
                inputField = {
                    InputField(
                        searchQuery,
                        { searchQuery = it },
                        { },
                        false,
                        { },
                        Modifier.fillMaxWidth(),
                        "Search apps"
                    )
                },
                expanded = false,
                onExpandedChange = { },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {}

            Spacer(modifier = Modifier.height(8.dp))

            // App List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredApps = remember(apps, selectedTab, searchQuery) {
                    apps.filter { if (selectedTab == 0) !it.isSystemApp else it.isSystemApp }
                        .filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
                        .sortedWith(compareByDescending<AppInfo> { prefs.contains(it.packageName) }.thenBy { it.name.lowercase() })
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppRow(app, prefs, snackbarHostState)
                    }
                }
            }
        }
    }
}

@Composable
fun AppRow(app: AppInfo, prefs: android.content.SharedPreferences, snackbarHostState: SnackbarHostState) {
    var expanded by remember { mutableStateOf(false) }
    var currentRenderer by remember { mutableStateOf(prefs.getString(app.packageName, "default") ?: "default") }
    val context = LocalContext.current
    var iconDrawable by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(app.packageName) {
        withContext(Dispatchers.IO) {
            iconDrawable = AppManager.loadIcon(context, app.packageName)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors()
    ) {
        Box {
            BasicComponent(
                title = app.name,
                summary = app.packageName,
                startAction = {
                    if (iconDrawable != null) {
                        Image(
                            painter = rememberDrawablePainter(iconDrawable),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(modifier = Modifier.size(48.dp).background(Color.LightGray, RoundedCornerShape(8.dp)))
                    }
                },
                endActions = {
                    Text(
                        text = when(currentRenderer) {
                            "skiavk" -> "Vulkan"
                            "skiagl" -> "OpenGL"
                            else -> "Default"
                        },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    DropdownArrowEndAction(
                        actionColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                },
                onClick = { expanded = true }
            )

            OverlayListPopup(
                show = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ListPopupColumn {
                        DropdownImpl(
                            item = DropdownItem(text = "Default"),
                            optionSize = 3,
                            isSelected = currentRenderer == "default",
                            index = 0,
                            onSelectedIndexChange = {
                                currentRenderer = "default"
                                prefs.edit().remove(app.packageName).commit()
                                expanded = false
                                coroutineScope.launch { snackbarHostState.showSnackbar("Config saved", withDismissAction = true) }
                            }
                        )
                        DropdownImpl(
                            item = DropdownItem(text = "Vulkan"),
                            optionSize = 3,
                            isSelected = currentRenderer == "skiavk",
                            index = 1,
                            onSelectedIndexChange = {
                                currentRenderer = "skiavk"
                                prefs.edit().putString(app.packageName, "skiavk").commit()
                                expanded = false
                                coroutineScope.launch { snackbarHostState.showSnackbar("Config saved", withDismissAction = true) }
                            }
                        )
                        DropdownImpl(
                            item = DropdownItem(text = "OpenGL"),
                            optionSize = 3,
                            isSelected = currentRenderer == "skiagl",
                            index = 2,
                            onSelectedIndexChange = {
                                currentRenderer = "skiagl"
                                prefs.edit().putString(app.packageName, "skiagl").commit()
                                expanded = false
                                coroutineScope.launch { snackbarHostState.showSnackbar("Config saved", withDismissAction = true) }
                            }
                        )
                }
            }
        }
    }
}
