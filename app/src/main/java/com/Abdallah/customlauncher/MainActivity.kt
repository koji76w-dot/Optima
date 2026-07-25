package com.Abdallah.customlauncher

import android.Manifest
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val category: String
)

data class NotificationActionItem(
    val title: String,
    val action: android.app.Notification.Action
)

data class NotifItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    val title: String,
    val text: String,
    val actions: List<NotificationActionItem>,
    val rawNotification: android.service.notification.StatusBarNotification?
)

class MainActivity : ComponentActivity() {

    companion object {
        var activeNotificationsState = mutableStateOf<List<NotifItem>>(emptyList())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val packageManager = context.packageManager
                val view = LocalView.current
                val sharedPreferences = remember {
                    context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
                }

                LaunchedEffect(Unit) {
                    val window = (view.context as? ComponentActivity)?.window
                    if (window != null) {
                        val windowInsetsController = WindowCompat.getInsetsController(window, view)
                        windowInsetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }

                // Perpetual check for Notification Access permission & trigger initial/active fetch
                var hasNotificationAccess by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    while (true) {
                        val listenerSetting = Settings.Secure.getString(
                            context.contentResolver,
                            "enabled_notification_listeners"
                        )
                        val packageName = context.packageName
                        hasNotificationAccess = listenerSetting != null && listenerSetting.contains(packageName)

                        if (hasNotificationAccess) {
                            CustomNotificationListener.instance?.triggerRefresh()
                        }

                        delay(1000L)
                    }
                }

                // Trigger refresh when the notifications panel opens
                var isDrawerOpen by remember { mutableStateOf(false) }
                var isNotificationsOpen by remember { mutableStateOf(false) }

                LaunchedEffect(isNotificationsOpen) {
                    if (isNotificationsOpen) {
                        CustomNotificationListener.instance?.triggerRefresh()
                    }
                }

                // Load Installed Apps
                var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }

                fun loadApps() {
                    val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                    }

                    val apps = packages.mapNotNull { packageInfo ->
                        val packageName = packageInfo.packageName
                        val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null

                        val label = try {
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) { "" }

                        val hasNoValidName = label.isBlank() || label.startsWith("com.") || label == packageName
                        val isSystemApp = hasNoValidName ||
                                ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                                        packageManager.getLaunchIntentForPackage(packageName) == null)

                        if (hasNoValidName && packageManager.getLaunchIntentForPackage(packageName) == null) {
                            return@mapNotNull null
                        }

                        val finalLabel = if (label.isBlank()) packageName.substringAfterLast('.') else label
                        val icon = try { packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null }

                        val category = when {
                            packageName.contains("game", true) -> "Games"
                            packageName.contains("video", true) || packageName.contains("audio", true) || packageName.contains("music", true) || packageName.contains("gallery", true) || packageName.contains("media", true) || packageName.contains("photos", true) -> "Media"
                            packageName.contains("calculator", true) || packageName.contains("docs", true) || packageName.contains("office", true) || packageName.contains("settings", true) || packageName.contains("work", true) || packageName.contains("notepad", true) || packageName.contains("notes", true) || packageName.contains("gmail", true) || packageName.contains("email", true) || packageName.contains("mail", true) || packageName.contains("vending", true) || packageName.contains("market", true) -> "Work"
                            isSystemApp -> "System"
                            else -> "All"
                        }

                        AppItem(finalLabel, packageName, icon, isSystemApp, category)
                    }.sortedBy { it.label.lowercase() }

                    installedApps = apps
                }

                LaunchedEffect(Unit) {
                    loadApps()
                }

                var sortOrder by remember { mutableStateOf("A-Z") }
                var appFilter by remember { mutableStateOf("User Only") }
                var searchQuery by remember { mutableStateOf("") }
                var selectedTab by remember { mutableStateOf("All") }
                val tabs = listOf("All", "Games", "Media", "Work", "System")

                // Clock customization state saved in SharedPreferences
                var clockYOffset by remember { mutableStateOf(sharedPreferences.getFloat("clock_y_offset", 100f)) }
                var clockFontSize by remember { mutableStateOf(sharedPreferences.getFloat("clock_font_size", 54f)) }
                var clockFontWeightIndex by remember { mutableStateOf(sharedPreferences.getInt("clock_font_weight_index", 1)) }
                var showClockAdjustDialog by remember { mutableStateOf(false) }

                val weightList = listOf(
                    FontWeight.Thin,
                    FontWeight.Light,
                    FontWeight.Normal,
                    FontWeight.Medium,
                    FontWeight.Bold,
                    FontWeight.ExtraBold,
                    FontWeight.Black
                )
                val weightNames = listOf("Thin", "Light", "Normal", "Medium", "Bold", "Extra Bold", "Black")

                LaunchedEffect(clockYOffset, clockFontSize, clockFontWeightIndex) {
                    sharedPreferences.edit()
                        .putFloat("clock_y_offset", clockYOffset)
                        .putFloat("clock_font_size", clockFontSize)
                        .putInt("clock_font_weight_index", clockFontWeightIndex)
                        .apply()
                }

                // Load saved dock package names or resolve smart defaults
                var dockPackageNames by remember {
                    mutableStateOf(
                        try {
                            val savedString = sharedPreferences.getString("dock_packages", null)
                            if (savedString != null) {
                                val list = savedString.split(",").filter { it.isNotBlank() }.toMutableList()
                                val playStorePkg = "com.android.vending"
                                val gmailPkg = "com.google.android.gm"
                                if (!list.contains(playStorePkg)) list.add(0, playStorePkg)
                                if (!list.contains(gmailPkg)) list.add(1, gmailPkg)
                                list
                            } else {
                                val defaults = mutableListOf("com.android.vending", "com.google.android.gm")

                                val candidateKeywords = listOf(
                                    listOf("notepad", "notes", "keep"),
                                    listOf("contacts", "people"),
                                    listOf("calculator", "calc"),
                                    listOf("photos", "gallery")
                                )

                                for (keywords in candidateKeywords) {
                                    val matched = packageManager.getInstalledApplications(0).find { appInfo ->
                                        val pkg = appInfo.packageName
                                        val hasLaunch = packageManager.getLaunchIntentForPackage(pkg) != null
                                        val matches = keywords.any { kw -> pkg.contains(kw, true) }
                                        hasLaunch && matches
                                    }
                                    if (matched != null && !defaults.contains(matched.packageName)) {
                                        defaults.add(matched.packageName)
                                    }
                                }

                                if (defaults.size <= 2) {
                                    listOf("com.android.vending", "com.google.android.gm", "com.google.android.contacts", "com.google.android.calculator", "com.google.android.apps.photos")
                                } else {
                                    defaults
                                }
                            }
                        } catch (e: Exception) {
                            listOf("com.android.vending", "com.google.android.gm", "com.google.android.contacts", "com.google.android.calculator", "com.google.android.apps.photos")
                        }
                    )
                }

                LaunchedEffect(dockPackageNames) {
                    try {
                        sharedPreferences.edit().putString("dock_packages", dockPackageNames.joinToString(",")).apply()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Parallax depth offsets calculation (increased wallpaper movement so it's clearly noticeable)
                val baseShiftDp = if (isDrawerOpen) 100.dp else if (isNotificationsOpen) (-100).dp else 0.dp

                // 1. Wallpaper background shift (increased to 100dp)
                val wallpaperShift by animateDpAsState(
                    targetValue = baseShiftDp,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    label = "wallpaperShift"
                )

                // 2. Clock and Dock shift (kept at 160dp to maintain clear depth separation ahead of wallpaper)
                val foregroundShift by animateDpAsState(
                    targetValue = if (isDrawerOpen) 160.dp else if (isNotificationsOpen) (-160).dp else 0.dp,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    label = "foregroundShift"
                )

                val backgroundBlur by animateDpAsState(
                    targetValue = if (isDrawerOpen || isNotificationsOpen) 8.dp else 0.dp,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    label = "backgroundBlur"
                )

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // --- CLOCK ADJUST DIALOG ---
                    if (showClockAdjustDialog) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x99000000))
                                .zIndex(200f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(320.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xE6222222))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Adjust Clock",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = "Y Position: ${clockYOffset.toInt()} dp", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                        Slider(
                                            value = clockYOffset,
                                            onValueChange = { clockYOffset = it },
                                            valueRange = 0f..1200f
                                        )
                                    }

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = "Size: ${clockFontSize.toInt()} sp", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                        Slider(
                                            value = clockFontSize,
                                            onValueChange = { clockFontSize = it },
                                            valueRange = 12f..240f
                                        )
                                    }

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(text = "Thickness: ${weightNames[clockFontWeightIndex]}", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                        Slider(
                                            value = clockFontWeightIndex.toFloat(),
                                            onValueChange = { clockFontWeightIndex = it.toInt().coerceIn(0, weightList.size - 1) },
                                            valueRange = 0f..(weightList.size - 1).toFloat(),
                                            steps = weightList.size - 2
                                        )
                                    }

                                    Button(
                                        onClick = { showClockAdjustDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2))
                                    ) {
                                        Text(text = "Done", color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // --- PERMISSION OVERLAY BLOCKER IF NOT GRANTED ---
                    if (!hasNotificationAccess) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x99000000))
                                .zIndex(100f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(340.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF222222))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Notification Access Required",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "This launcher requires Notification Access to display your incoming alerts on the right panel. Please grant permission to continue.",
                                        color = Color(0xFFCCCCCC),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2))
                                    ) {
                                        Text(text = "Grant Permission", color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // --- BACKGROUND LAYER ---
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(backgroundBlur)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        // Wallpaper Layer with loop filler (moves at baseline wallpaperShift)
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = wallpaperShift)
                        ) {
                            val screenWidth = maxWidth

                            Image(
                                painter = painterResource(id = R.drawable.my_wallpaper),
                                contentDescription = "Wallpaper Loop Filler Left",
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(screenWidth)
                                    .offset(x = -screenWidth)
                                    .graphicsLayer(scaleX = -1f),
                                contentScale = ContentScale.Crop
                            )

                            Image(
                                painter = painterResource(id = R.drawable.my_wallpaper),
                                contentDescription = "Wallpaper",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            Image(
                                painter = painterResource(id = R.drawable.my_wallpaper),
                                contentDescription = "Wallpaper Loop Filler Right",
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(screenWidth)
                                    .offset(x = screenWidth)
                                    .graphicsLayer(scaleX = -1f),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Top Center Clock (moves at foregroundShift for parallax depth)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .offset(x = foregroundShift, y = clockYOffset.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TopCenterClock(
                                fontSize = clockFontSize.sp,
                                fontWeight = weightList[clockFontWeightIndex],
                                onLongClick = { showClockAdjustDialog = true }
                            )
                        }

                        // Dock Container (moves at foregroundShift along with the clock)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(x = foregroundShift)
                                .padding(bottom = 20.dp)
                                .wrapContentWidth(Alignment.CenterHorizontally)
                                .background(Color(0x66FFFFFF), RoundedCornerShape(28.dp))
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                dockPackageNames.forEach { pkgName ->
                                    val launchIntent = packageManager.getLaunchIntentForPackage(pkgName)
                                    val appInfo = try { packageManager.getApplicationInfo(pkgName, 0) } catch (e: Exception) { null }
                                    val appLabel = appInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: pkgName.substringAfterLast('.')
                                    val appIcon = appInfo?.let { try { packageManager.getApplicationIcon(pkgName) } catch (e: Exception) { null } }

                                    if (launchIntent != null) {
                                        DockAppIcon(
                                            icon = appIcon,
                                            label = appLabel,
                                            packageName = pkgName,
                                            isInDock = true,
                                            onUninstall = { pkg ->
                                                context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
                                            },
                                            onAddToDock = {},
                                            onRemoveFromDock = { dockPackageNames = dockPackageNames - pkgName }
                                        ) {
                                            context.startActivity(launchIntent)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- FOREGROUND LAYER ---
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. Left Apps Drawer Panel
                        val drawerPanelInteractionSource = remember { MutableInteractionSource() }
                        val isDrawerPanelHovered by drawerPanelInteractionSource.collectIsHoveredAsState()

                        val leftEdgeTriggerSource = remember { MutableInteractionSource() }
                        val isLeftEdgeHovered by leftEdgeTriggerSource.collectIsHoveredAsState()

                        LaunchedEffect(isLeftEdgeHovered, isDrawerPanelHovered) {
                            if (isLeftEdgeHovered) {
                                isDrawerOpen = true
                            } else if (!isDrawerPanelHovered && isDrawerOpen) {
                                delay(150L)
                                if (!isDrawerPanelHovered) {
                                    isDrawerOpen = false
                                }
                            }
                        }

                        if (!isDrawerOpen && !isNotificationsOpen) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .hoverable(interactionSource = leftEdgeTriggerSource)
                            )
                        }

                        AnimatedVisibility(
                            visible = isDrawerOpen,
                            enter = slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(600)),
                            exit = slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(500))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(380.dp)
                                    .background(Color(0xC4FFFFFF))
                                    .hoverable(interactionSource = drawerPanelInteractionSource)
                                    .padding(start = 24.dp, top = 24.dp, bottom = 24.dp, end = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 24.dp, top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0x26000000))
                                                .padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (searchQuery.isEmpty()) {
                                                Text(text = "Search apps...", color = Color(0x77222222), fontSize = 14.sp)
                                            }
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                singleLine = true,
                                                textStyle = TextStyle(color = Color(0xFF222222), fontSize = 14.sp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp, bottom = 16.dp)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        tabs.forEach { tabName ->
                                            DrawerTab(text = tabName, isSelected = selectedTab == tabName) { selectedTab = tabName }
                                        }
                                        Spacer(modifier = Modifier.width(20.dp))
                                    }

                                    val filteredApps = installedApps.filter { app ->
                                        val matchesAppFilter = when (appFilter) {
                                            "User Only" -> !app.isSystem
                                            else -> true
                                        }
                                        val matchesTab = when (selectedTab) {
                                            "All" -> true
                                            else -> app.category == selectedTab
                                        }
                                        val matchesSearch = if (searchQuery.isBlank()) true else containsAllCharsIgnoreCase(app.label, searchQuery)
                                        matchesAppFilter && matchesTab && matchesSearch
                                    }.let { list ->
                                        if (sortOrder == "A-Z") list.sortedBy { it.label.lowercase() }
                                        else list.sortedByDescending { it.label.lowercase() }
                                    }

                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(4),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(end = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(filteredApps) { app ->
                                            val isInDock = dockPackageNames.contains(app.packageName)
                                            DrawerAppItem(
                                                app = app,
                                                isInDock = isInDock,
                                                onClick = {
                                                    val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                                                    if (launchIntent != null) {
                                                        context.startActivity(launchIntent)
                                                        isDrawerOpen = false
                                                    }
                                                },
                                                onUninstall = { pkg -> context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) },
                                                onAddToDock = { if (!dockPackageNames.contains(app.packageName)) dockPackageNames = dockPackageNames + app.packageName },
                                                onRemoveFromDock = { dockPackageNames = dockPackageNames - app.packageName }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Right Notifications Panel
                        val notifPanelInteractionSource = remember { MutableInteractionSource() }
                        val isNotifPanelHovered by notifPanelInteractionSource.collectIsHoveredAsState()

                        val rightEdgeTriggerSource = remember { MutableInteractionSource() }
                        val isRightEdgeHovered by rightEdgeTriggerSource.collectIsHoveredAsState()

                        LaunchedEffect(isRightEdgeHovered, isNotifPanelHovered) {
                            if (isRightEdgeHovered) {
                                isNotificationsOpen = true
                            } else if (!isNotifPanelHovered && isNotificationsOpen) {
                                delay(150L)
                                if (!isNotifPanelHovered) {
                                    isNotificationsOpen = false
                                }
                            }
                        }

                        if (!isDrawerOpen && !isNotificationsOpen) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .hoverable(interactionSource = rightEdgeTriggerSource)
                            )
                        }

                        AnimatedVisibility(
                            visible = isNotificationsOpen,
                            enter = slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(600)),
                            exit = slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(500)),
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(380.dp)
                                    .background(Color(0xC4FFFFFF))
                                    .hoverable(interactionSource = notifPanelInteractionSource)
                                    .padding(start = 24.dp, top = 24.dp, bottom = 24.dp, end = 24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp, top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Notifications",
                                            color = Color(0xFF222222),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        // Battery Indicator next to "Notifications" text
                                        BatteryIndicator()
                                    }

                                    val notifications = activeNotificationsState.value

                                    if (notifications.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No notifications",
                                                color = Color(0x88222222),
                                                fontSize = 14.sp
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(notifications) { notif ->
                                                NotificationCard(notif = notif)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom Battery Indicator Composable with sleek battery icon + percentage text
@Composable
fun BatteryIndicator() {
    val context = LocalContext.current
    var batteryLevel by remember { mutableIntStateOf(100) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level != -1 && scale != -1) {
                        batteryLevel = ((level / scale.toFloat()) * 100).toInt()
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(receiver, filter)
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                batteryLevel = ((level / scale.toFloat()) * 100).toInt()
            }
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(
            modifier = Modifier.size(width = 24.dp, height = 12.dp)
        ) {
            val bodyWidth = size.width - 3.dp.toPx()
            val bodyHeight = size.height
            val cornerRadius = 3.dp.toPx()

            // Battery Body Outline
            drawRoundRect(
                color = Color(0xFF222222),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Battery Tip (Positive Terminal)
            drawRect(
                color = Color(0xFF222222),
                topLeft = Offset(bodyWidth, bodyHeight / 2f - 2.dp.toPx()),
                size = Size(2.dp.toPx(), 4.dp.toPx())
            )

            // Battery Fill Level
            val padding = 2.dp.toPx()
            val fillMaxWidth = (bodyWidth - (padding * 2)) * (batteryLevel / 100f)
            if (fillMaxWidth > 0f) {
                drawRoundRect(
                    color = Color(0xFF222222),
                    topLeft = Offset(padding, padding),
                    size = Size(fillMaxWidth, bodyHeight - (padding * 2)),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Fill
                )
            }
        }

        Text(
            text = "$batteryLevel%",
            color = Color(0xFF222222),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Notification Card with Black text styling
@Composable
fun NotificationCard(notif: NotifItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x66FFFFFF))
            .padding(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x22000000)),
                    contentAlignment = Alignment.Center
                ) {
                    if (notif.appIcon != null) {
                        val bitmap = notif.appIcon.toBitmap(width = 60, height = 60)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = notif.appName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Text(
                    text = notif.appName,
                    color = Color(0xFF222222),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (notif.title.isNotBlank()) {
                Text(
                    text = notif.title,
                    color = Color(0xFF222222),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (notif.text.isNotBlank()) {
                Text(
                    text = notif.text,
                    color = Color(0xFF333333),
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (notif.actions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    notif.actions.forEach { actionItem ->
                        Button(
                            onClick = {
                                try {
                                    actionItem.action.actionIntent.send()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x22000000)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = actionItem.title,
                                color = Color(0xFF222222),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// Notification Listener Service with explicit component setting so Android lists it properly
class CustomNotificationListener : android.service.notification.NotificationListenerService() {

    companion object {
        var instance: CustomNotificationListener? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateNotifications()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        updateNotifications()
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        updateNotifications()
    }

    fun triggerRefresh() {
        updateNotifications()
    }

    private fun updateNotifications() {
        try {
            val list = activeNotifications ?: return
            val pm = packageManager
            val mapped = list.mapNotNull { sbn ->
                val pkg = sbn.packageName
                if (pkg == packageName) return@mapNotNull null

                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

                if (title.isBlank() && text.isBlank()) return@mapNotNull null

                val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
                val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg.substringAfterLast('.')
                val appIcon = appInfo?.let { try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null } }

                val rawActions = sbn.notification.actions
                val actionItems = mutableListOf<NotificationActionItem>()
                if (rawActions != null) {
                    for (action in rawActions) {
                        val actionTitle = action.title?.toString() ?: "Action"
                        actionItems.add(NotificationActionItem(actionTitle, action))
                    }
                }

                NotifItem(
                    key = sbn.key,
                    packageName = pkg,
                    appName = appName,
                    appIcon = appIcon,
                    title = title,
                    text = text,
                    actions = actionItems,
                    rawNotification = sbn
                )
            }
            MainActivity.activeNotificationsState.value = mapped
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopCenterClock(fontSize: androidx.compose.ui.unit.TextUnit, fontWeight: FontWeight, onLongClick: () -> Unit) {
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            timeText = SimpleDateFormat("hh:mm", Locale.getDefault()).format(Date())
            delay(1000L)
        }
    }
    if (timeText.isNotEmpty()) {
        Box(
            modifier = Modifier
                .combinedClickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = timeText,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.5.sp
            )
        }
    }
}

fun containsAllCharsIgnoreCase(target: String, query: String): Boolean {
    val targetClean = target.lowercase()
    val queryClean = query.lowercase().filter { !it.isWhitespace() }
    if (queryClean.isEmpty()) return true
    return queryClean.all { char -> targetClean.contains(char) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerAppItem(app: AppItem, isInDock: Boolean, onClick: () -> Unit, onUninstall: (String) -> Unit, onAddToDock: () -> Unit, onRemoveFromDock: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x22000000)),
                contentAlignment = Alignment.Center
            ) {
                if (app.icon != null) {
                    val bitmap = app.icon.toBitmap(width = 120, height = 120)
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = app.label, modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                }
            }
            Text(text = app.label, color = Color(0xFF222222), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { showMenu = false; onUninstall(app.packageName) })
            if (!isInDock) {
                DropdownMenuItem(text = { Text("Add to dock") }, onClick = { showMenu = false; onAddToDock() })
            } else {
                DropdownMenuItem(text = { Text("Remove from dock") }, onClick = { showMenu = false; onRemoveFromDock() })
            }
        }
    }
}

@Composable
fun MenuOptionItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bg by animateColorAsState(targetValue = when { isSelected -> Color(0x33000000); isHovered -> Color(0x1A000000); else -> Color.Transparent }, label = "menu_opt")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .hoverable(interactionSource = interactionSource)
            .clickable(indication = null, interactionSource = interactionSource) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(text = text, color = if (isSelected) Color(0xFF222222) else Color(0xFF444444), fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun DrawerTab(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor by animateColorAsState(targetValue = when { isSelected -> Color(0x33000000); isHovered -> Color(0x1A000000); else -> Color.Transparent }, label = "tab_bg")
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .hoverable(interactionSource = interactionSource)
            .clickable(indication = null, interactionSource = interactionSource) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (isSelected) Color(0xFF222222) else Color(0xFF555555), fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockAppIcon(icon: Drawable?, label: String, packageName: String, isInDock: Boolean, onUninstall: (String) -> Unit, onAddToDock: () -> Unit, onRemoveFromDock: () -> Unit, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.size(52.dp).hoverable(interactionSource = interactionSource), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.align(Alignment.TopCenter).wrapContentSize(unbounded = true).offset(y = (-55).dp), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = isHovered && !showMenu,
                enter = fadeIn(animationSpec = tween(durationMillis = 150)),
                exit = fadeOut(animationSpec = tween(durationMillis = 100))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp)).background(Color(0xFFE8E8E8), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(text = label, color = Color(0xFF222222), fontSize = 13.sp)
                    }
                    Canvas(modifier = Modifier.size(10.dp, 5.dp)) {
                        val trianglePath = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close() }
                        drawPath(path = trianglePath, color = Color(0xFFE8E8E8))
                    }
                }
            }
        }

        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                val bitmap = icon.toBitmap(width = 120, height = 120)
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { showMenu = false; onUninstall(packageName) })
            if (!isInDock) {
                DropdownMenuItem(text = { Text("Add to dock") }, onClick = { showMenu = false; onAddToDock() })
            } else {
                DropdownMenuItem(text = { Text("Remove from dock") }, onClick = { showMenu = false; onRemoveFromDock() })
            }
        }
    }
}