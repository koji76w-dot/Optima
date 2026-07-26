@file:OptIn(ExperimentalFoundationApi::class)

package com.Abdallah.customlauncher

import android.app.Notification
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class AppItem(
    val label: String,
    val packageName: String,
    val iconBitmap: ImageBitmap?,
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
    val appIconBitmap: ImageBitmap?,
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
                LauncherApp()
            }
        }
    }
}

@Composable
fun LauncherApp() {
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

    var hasNotificationAccess by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var wasAccessGranted = false
        while (true) {
            val listenerSetting = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            val packageName = context.packageName
            hasNotificationAccess = listenerSetting != null && listenerSetting.contains(packageName)

            if (hasNotificationAccess && !wasAccessGranted) {
                CustomNotificationListener.instance?.triggerRefresh()
            }
            wasAccessGranted = hasNotificationAccess

            delay(1000L)
        }
    }

    var isDrawerOpen by remember { mutableStateOf(false) }
    var isNotificationsOpen by remember { mutableStateOf(false) }
    var isSettingsMode by remember { mutableStateOf(false) }
    var selectedSettingsCategory by remember { mutableStateOf<String?>(null) }

    var dockTransparency by remember {
        mutableStateOf(sharedPreferences.getFloat("dock_transparency", 0.55f))
    }
    var dockScale by remember {
        mutableStateOf(sharedPreferences.getFloat("dock_scale", 1f))
    }
    var dockAppCornerRounding by remember {
        mutableStateOf(sharedPreferences.getFloat("dock_app_corner_rounding", 16f))
    }
    var isDarkMode by remember {
        mutableStateOf(sharedPreferences.getBoolean("is_dark_mode", true))
    }
    var drawerTransparency by remember {
        mutableStateOf(sharedPreferences.getFloat("drawer_transparency", 0.85f))
    }
    var customWallpaperUri by remember {
        mutableStateOf(sharedPreferences.getString("custom_wallpaper_uri", null))
    }

    var dockBackgroundUri by remember {
        mutableStateOf(sharedPreferences.getString("dock_background_uri", null))
    }
    var drawerBackgroundUri by remember {
        mutableStateOf(sharedPreferences.getString("drawer_background_uri", null))
    }

    var accentColorIndex by remember {
        mutableStateOf(sharedPreferences.getInt("accent_color_index", 0))
    }
    var cornerRounding by remember {
        mutableStateOf(sharedPreferences.getFloat("corner_rounding", 16f))
    }
    var blurIntensity by remember {
        mutableStateOf(sharedPreferences.getFloat("blur_intensity", 0f))
    }
    var use24HourFormat by remember {
        mutableStateOf(sharedPreferences.getBoolean("use_24_hour_format", false))
    }
    var showDate by remember {
        mutableStateOf(sharedPreferences.getBoolean("show_date", true))
    }
    var clockShadowIntensity by remember {
        mutableStateOf(sharedPreferences.getFloat("clock_shadow_intensity", 0.5f))
    }
    var hideAppLabels by remember {
        mutableStateOf(sharedPreferences.getBoolean("hide_app_labels", false))
    }
    var drawerColumns by remember {
        mutableStateOf(sharedPreferences.getInt("drawer_columns", 4))
    }
    var doubleTapToSleep by remember {
        mutableStateOf(sharedPreferences.getBoolean("double_tap_to_sleep", false))
    }
    var hapticFeedbackEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("haptic_feedback", true))
    }
    var hiddenPackageNames by remember {
        mutableStateOf(
            try {
                sharedPreferences.getString("hidden_packages", "")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            } catch (e: Exception) { emptySet() }
        )
    }

    val accentColors = listOf(
        Color(0xFF4A90E2),
        Color(0xFF9B51E0),
        Color(0xFF27AE60),
        Color(0xFFF2994A)
    )
    val activeAccentColor = accentColors[accentColorIndex.coerceIn(0, accentColors.size - 1)]

    var wallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var dockBackgroundBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var drawerBackgroundBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(customWallpaperUri) {
        if (customWallpaperUri != null) {
            sharedPreferences.edit().putString("custom_wallpaper_uri", customWallpaperUri).apply()
        } else {
            sharedPreferences.edit().remove("custom_wallpaper_uri").apply()
        }
        wallpaperBitmap = withContext(Dispatchers.IO) {
            if (customWallpaperUri != null) {
                try {
                    val uri = Uri.parse(customWallpaperUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bitmap?.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else {
                null
            }
        }
    }

    LaunchedEffect(dockBackgroundUri) {
        if (dockBackgroundUri != null) {
            sharedPreferences.edit().putString("dock_background_uri", dockBackgroundUri).apply()
        } else {
            sharedPreferences.edit().remove("dock_background_uri").apply()
        }
        dockBackgroundBitmap = withContext(Dispatchers.IO) {
            if (dockBackgroundUri != null) {
                try {
                    val uri = Uri.parse(dockBackgroundUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bitmap?.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else {
                null
            }
        }
    }

    LaunchedEffect(drawerBackgroundUri) {
        if (drawerBackgroundUri != null) {
            sharedPreferences.edit().putString("drawer_background_uri", drawerBackgroundUri).apply()
        } else {
            sharedPreferences.edit().remove("drawer_background_uri").apply()
        }
        drawerBackgroundBitmap = withContext(Dispatchers.IO) {
            if (drawerBackgroundUri != null) {
                try {
                    val uri = Uri.parse(drawerBackgroundUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bitmap?.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else {
                null
            }
        }
    }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            customWallpaperUri = it.toString()
        }
    }

    val dockBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            dockBackgroundUri = it.toString()
        }
    }

    val drawerBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            drawerBackgroundUri = it.toString()
        }
    }

    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xCCFFFFFF) else Color(0xCC000000)
    val hintColor = if (isDarkMode) Color(0x99FFFFFF) else Color(0x99000000)
    val surfaceColor = if (isDarkMode) Color(0x22FFFFFF) else Color(0x22000000)
    val surfaceColorStrong = if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000)

    LaunchedEffect(dockTransparency) { sharedPreferences.edit().putFloat("dock_transparency", dockTransparency).apply() }
    LaunchedEffect(dockScale) { sharedPreferences.edit().putFloat("dock_scale", dockScale).apply() }
    LaunchedEffect(dockAppCornerRounding) { sharedPreferences.edit().putFloat("dock_app_corner_rounding", dockAppCornerRounding).apply() }
    LaunchedEffect(isDarkMode) { sharedPreferences.edit().putBoolean("is_dark_mode", isDarkMode).apply() }
    LaunchedEffect(drawerTransparency) { sharedPreferences.edit().putFloat("drawer_transparency", drawerTransparency).apply() }
    LaunchedEffect(accentColorIndex) { sharedPreferences.edit().putInt("accent_color_index", accentColorIndex).apply() }
    LaunchedEffect(cornerRounding) { sharedPreferences.edit().putFloat("corner_rounding", cornerRounding).apply() }
    LaunchedEffect(blurIntensity) { sharedPreferences.edit().putFloat("blur_intensity", blurIntensity).apply() }
    LaunchedEffect(use24HourFormat) { sharedPreferences.edit().putBoolean("use_24_hour_format", use24HourFormat).apply() }
    LaunchedEffect(showDate) { sharedPreferences.edit().putBoolean("show_date", showDate).apply() }
    LaunchedEffect(clockShadowIntensity) { sharedPreferences.edit().putFloat("clock_shadow_intensity", clockShadowIntensity).apply() }
    LaunchedEffect(hideAppLabels) { sharedPreferences.edit().putBoolean("hide_app_labels", hideAppLabels).apply() }
    LaunchedEffect(drawerColumns) { sharedPreferences.edit().putInt("drawer_columns", drawerColumns).apply() }
    LaunchedEffect(doubleTapToSleep) { sharedPreferences.edit().putBoolean("double_tap_to_sleep", doubleTapToSleep).apply() }
    LaunchedEffect(hapticFeedbackEnabled) { sharedPreferences.edit().putBoolean("haptic_feedback", hapticFeedbackEnabled).apply() }
    LaunchedEffect(hiddenPackageNames) { sharedPreferences.edit().putString("hidden_packages", hiddenPackageNames.joinToString(",")).apply() }

    LaunchedEffect(isNotificationsOpen) {
        if (isNotificationsOpen) {
            CustomNotificationListener.instance?.triggerRefresh()
        }
    }

    var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }

    suspend fun loadApps(): List<AppItem> = withContext(Dispatchers.Default) {
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
            val iconBitmap = try {
                packageManager.getApplicationIcon(packageName).toBitmap(width = 120, height = 120).asImageBitmap()
            } catch (e: Exception) { null }

            val category = when {
                packageName.contains("game", true) || packageName.contains("play", true) || packageName.contains("emulator", true) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME) -> "Games"
                packageName.contains("video", true) || packageName.contains("audio", true) || packageName.contains("music", true) || packageName.contains("gallery", true) || packageName.contains("media", true) || packageName.contains("photos", true) -> "Media"
                packageName.contains("calculator", true) || packageName.contains("docs", true) || packageName.contains("office", true) || packageName.contains("settings", true) || packageName.contains("work", true) || packageName.contains("notepad", true) || packageName.contains("notes", true) || packageName.contains("gmail", true) || packageName.contains("email", true) || packageName.contains("mail", true) || packageName.contains("vending", true) || packageName.contains("market", true) -> "Work"
                isSystemApp -> "System"
                else -> "All"
            }

            AppItem(finalLabel, packageName, iconBitmap, isSystemApp, category)
        }.sortedBy { it.label.lowercase() }

        apps
    }

    LaunchedEffect(Unit) {
        installedApps = loadApps()
    }

    LaunchedEffect(isDrawerOpen, isSettingsMode) {
        while (isDrawerOpen && !isSettingsMode) {
            delay(5000L)
            if (isDrawerOpen && !isSettingsMode) {
                installedApps = loadApps()
            }
        }
    }

    var sortOrder by remember { mutableStateOf("A-Z") }
    var appFilter by remember { mutableStateOf("User Only") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("All") }
    val tabs = listOf("All", "Games", "Media", "Work", "System")

    var clockYOffset by remember { mutableStateOf(sharedPreferences.getFloat("clock_y_offset", 100f)) }
    var clockFontSize by remember { mutableStateOf(sharedPreferences.getFloat("clock_font_size", 54f)) }
    var clockFontWeightIndex by remember { mutableIntStateOf(sharedPreferences.getInt("clock_font_weight_index", 3)) }
    var clockFontFamilyIndex by remember { mutableIntStateOf(sharedPreferences.getInt("clock_font_family_index", 0)) }
    var clockDarkText by remember { mutableStateOf(sharedPreferences.getBoolean("clock_dark_text", false)) }

    val configuration = LocalConfiguration.current
    val maxClockY = (configuration.screenHeightDp.toFloat() - (clockFontSize * 1.3f) - 160f)
        .coerceAtLeast(0f)
    val clampedClockY = clockYOffset.coerceIn(0f, maxClockY)

    LaunchedEffect(maxClockY) {
        if (clockYOffset > maxClockY) clockYOffset = maxClockY
    }

    val weightList = listOf(
        FontWeight.W100, FontWeight.W200, FontWeight.W300,
        FontWeight.W400, FontWeight.W500, FontWeight.W600,
        FontWeight.W700, FontWeight.W800, FontWeight.W900
    )
    val weightNames = listOf(
        "Thin", "Extra Light", "Light", "Normal",
        "Medium", "Semi Bold", "Bold", "Extra Bold", "Black"
    )

    val fontFamilyList = listOf(
        FontFamily.SansSerif,
        FontFamily.Serif,
        FontFamily.Monospace,
        FontFamily.Cursive
    )
    val fontFamilyNames = listOf("Sans Serif", "Serif", "Monospace", "Cursive")

    LaunchedEffect(clockYOffset, clockFontSize, clockFontWeightIndex, clockFontFamilyIndex, clockDarkText) {
        sharedPreferences.edit()
            .putFloat("clock_y_offset", clockYOffset)
            .putFloat("clock_font_size", clockFontSize)
            .putInt("clock_font_weight_index", clockFontWeightIndex)
            .putInt("clock_font_family_index", clockFontFamilyIndex)
            .putBoolean("clock_dark_text", clockDarkText)
            .apply()
    }

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
                    listOf("com.android.vending", "com.google.android.gm", "com.google.android.contacts", "com.google.android.calculator", "com.google.android.apps.photos")
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

    val dockApps = remember(dockPackageNames, installedApps) {
        dockPackageNames.mapNotNull { pkgName ->
            installedApps.find { it.packageName == pkgName } ?: run {
                val appInfo = try { packageManager.getApplicationInfo(pkgName, 0) } catch (e: Exception) { null }
                if (appInfo == null) {
                    null
                } else {
                    val label = try { packageManager.getApplicationLabel(appInfo).toString() } catch (e: Exception) { pkgName.substringAfterLast('.') }
                    val iconBitmap = try {
                        packageManager.getApplicationIcon(pkgName).toBitmap(width = 120, height = 120).asImageBitmap()
                    } catch (e: Exception) { null }
                    AppItem(label, pkgName, iconBitmap, false, "All")
                }
            }
        }
    }

    val baseShiftDp = if (isDrawerOpen) 100.dp else if (isNotificationsOpen) (-100).dp else 0.dp

    val wallpaperShift by animateDpAsState(
        targetValue = baseShiftDp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "wallpaperShift"
    )

    val foregroundShift by animateDpAsState(
        targetValue = if (isDrawerOpen) 160.dp else if (isNotificationsOpen) (-160).dp else 0.dp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "foregroundShift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!isDrawerOpen && !isNotificationsOpen) {
                    Modifier.pointerInput(doubleTapToSleep, hapticFeedbackEnabled) {
                        detectTapGestures(
                            onTap = {},
                            onDoubleTap = if (doubleTapToSleep) {
                                {
                                    if (hapticFeedbackEnabled) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                }
                            } else null
                        )
                    }
                } else Modifier
            )
    ) {
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
                        .clip(RoundedCornerShape(cornerRounding.dp))
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
                            colors = ButtonDefaults.buttonColors(containerColor = activeAccentColor)
                        ) {
                            Text(text = "Grant Permission", color = Color.White)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(wallpaperShift.roundToPx(), 0) }
            ) {
                val screenWidth = maxWidth

                val wallpaperModifier = Modifier
                    .fillMaxSize()
                    .then(if (blurIntensity > 0f) Modifier.blur(blurIntensity.dp) else Modifier)

                if (wallpaperBitmap != null) {
                    Image(
                        bitmap = wallpaperBitmap!!,
                        contentDescription = "Wallpaper Loop Filler Left",
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(screenWidth)
                            .offset(x = -screenWidth)
                            .graphicsLayer(scaleX = -1f),
                        contentScale = ContentScale.Crop
                    )
                    Image(
                        bitmap = wallpaperBitmap!!,
                        contentDescription = "Wallpaper",
                        modifier = wallpaperModifier,
                        contentScale = ContentScale.Crop
                    )
                    Image(
                        bitmap = wallpaperBitmap!!,
                        contentDescription = "Wallpaper Loop Filler Right",
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(screenWidth)
                            .offset(x = screenWidth)
                            .graphicsLayer(scaleX = -1f),
                        contentScale = ContentScale.Crop
                    )
                } else {
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
                        modifier = wallpaperModifier,
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
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(foregroundShift.roundToPx(), clampedClockY.dp.roundToPx()) },
                contentAlignment = Alignment.Center
            ) {
                TopCenterClock(
                    fontSize = clockFontSize.sp,
                    fontWeight = weightList[clockFontWeightIndex],
                    fontFamily = fontFamilyList[clockFontFamilyIndex],
                    use24Hour = use24HourFormat,
                    showDate = showDate,
                    clockShadowIntensity = clockShadowIntensity,
                    isDarkText = clockDarkText,
                    onLongClick = {
                        isDrawerOpen = true
                        isSettingsMode = true
                        selectedSettingsCategory = "Home & Clock"
                    }
                )
            }

            val count = dockApps.size

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationX = foregroundShift.toPx()
                        scaleX = dockScale
                        scaleY = dockScale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        clip = false
                    }
                    .padding(bottom = 20.dp)
                    .zIndex(50f),
                contentAlignment = Alignment.Center
            ) {
                val iconBaseSizeDp = 52.dp
                val iconSpacingDp = 14.dp
                val dockPaddingHorizontalDp = 20.dp

                val fixedDockWidth = (iconBaseSizeDp * count) + (iconSpacingDp * (count - 1).coerceAtLeast(0)) + (dockPaddingHorizontalDp * 2)
                val dockBaseColor = if (isDarkMode) Color.Black else Color.White

                Box(
                    modifier = Modifier
                        .width(fixedDockWidth)
                        .clip(RoundedCornerShape(cornerRounding.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = dockTransparency
                            }
                            .background(dockBaseColor, shape = RoundedCornerShape(cornerRounding.dp))
                            .then(
                                if (dockBackgroundBitmap != null) {
                                    Modifier.drawBehind {
                                        drawImage(
                                            image = dockBackgroundBitmap!!,
                                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                        )
                                    }
                                } else Modifier
                            )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(iconSpacingDp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = dockPaddingHorizontalDp, vertical = 12.dp)
                            .graphicsLayer { clip = false }
                    ) {
                        dockApps.forEachIndexed { index, app ->
                            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)

                            if (launchIntent != null) {
                                DockAppIcon(
                                    iconBitmap = app.iconBitmap,
                                    label = app.label,
                                    packageName = app.packageName,
                                    isInDock = true,
                                    cornerRounding = dockAppCornerRounding,
                                    onUninstall = { pkg ->
                                        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
                                    },
                                    onAddToDock = {},
                                    onRemoveFromDock = { dockPackageNames = dockPackageNames - app.packageName }
                                ) {
                                    if (hapticFeedbackEnabled) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    }
                                    context.startActivity(launchIntent)
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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

            val drawerOffsetX by animateDpAsState(
                targetValue = if (isDrawerOpen) 0.dp else (-380).dp,
                animationSpec = tween(durationMillis = if (isDrawerOpen) 400 else 300, easing = FastOutSlowInEasing),
                label = "drawerOffsetX"
            )
            val drawerAlpha by animateFloatAsState(
                targetValue = if (isDrawerOpen) 1f else 0f,
                animationSpec = tween(durationMillis = if (isDrawerOpen) 400 else 300),
                label = "drawerAlpha"
            )

            run {
                val drawerBaseColor = if (isDarkMode) Color.Black else Color.White
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(380.dp)
                        .graphicsLayer {
                            translationX = drawerOffsetX.toPx()
                            alpha = drawerAlpha
                            clip = true
                        }
                        .background(drawerBaseColor.copy(alpha = if (drawerBackgroundBitmap == null) drawerTransparency else 1f))
                        .then(
                            if (drawerBackgroundBitmap != null) {
                                Modifier.graphicsLayer(alpha = drawerTransparency).drawBehind {
                                    drawImage(
                                        image = drawerBackgroundBitmap!!,
                                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                    )
                                }
                            } else Modifier
                        )
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
                                    .clip(RoundedCornerShape(cornerRounding.dp))
                                    .background(surfaceColorStrong)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = if (!isSettingsMode) "Search apps..." else "Search settings...",
                                        color = hintColor,
                                        fontSize = 14.sp
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = textColor, fontSize = 14.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(cornerRounding.dp))
                                    .background(surfaceColorStrong)
                                    .clickable {
                                        isSettingsMode = !isSettingsMode
                                        selectedSettingsCategory = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Crossfade(targetState = isSettingsMode, animationSpec = tween(300), label = "settingsHomeToggle") { settings ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (settings) {
                                            HomeIcon(tint = textColor)
                                        } else {
                                            SettingsIcon(tint = textColor)
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = !isSettingsMode,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -20 },
                            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -20 }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 16.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tabs.forEach { tabName ->
                                    DrawerTab(text = tabName, isSelected = selectedTab == tabName, isDarkMode = isDarkMode, accentColor = activeAccentColor, cornerRounding = cornerRounding) { selectedTab = tabName }
                                }
                                Spacer(modifier = Modifier.width(20.dp))
                            }
                        }

                        val filteredApps = remember(installedApps, appFilter, selectedTab, searchQuery, sortOrder, hiddenPackageNames) {
                            installedApps.filter { app ->
                                if (hiddenPackageNames.contains(app.packageName)) return@filter false
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
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 24.dp)
                        ) {
                            Crossfade(
                                targetState = isSettingsMode,
                                animationSpec = tween(300),
                                label = "drawerContentCrossfade"
                            ) { settingsMode ->
                                if (settingsMode) {
                                    val allSettingsCategories = listOf(
                                        Pair("Theming & Appearance", listOf(
                                            Pair("Dark Mode", @Composable {
                                                SettingCard(title = "Dark Mode", surfaceColor = surfaceColor, textColor = textColor, cornerRounding = cornerRounding) {
                                                    Switch(checked = isDarkMode, onCheckedChange = { isDarkMode = it })
                                                }
                                            }),
                                            Pair("Accent Color", @Composable {
                                                SettingCard(title = "Accent Color", surfaceColor = surfaceColor, textColor = textColor, cornerRounding = cornerRounding) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        accentColors.forEachIndexed { idx, color ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .clip(RoundedCornerShape(12.dp))
                                                                    .background(color)
                                                                    .clickable { accentColorIndex = idx }
                                                                    .then(if (accentColorIndex == idx) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                                                            )
                                                        }
                                                    }
                                                }
                                            }),
                                            Pair("Drawer Transparency", @Composable {
                                                SettingSliderCard(title = "Drawer Transparency", value = drawerTransparency, valueRange = 0.1f..1f, percent = true, surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { drawerTransparency = it }
                                            }),
                                            Pair("Drawer Background Image", @Composable {
                                                SettingActionCard(title = "Choose Drawer Background", subtitle = if (drawerBackgroundUri != null) "Custom Active" else "Default", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { drawerBgPickerLauncher.launch(arrayOf("image/*")) }
                                            }),
                                            Pair("Reset Drawer Background", @Composable {
                                                SettingActionCard(title = "Reset Drawer Background", subtitle = "", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { drawerBackgroundUri = null }
                                            }),
                                            Pair("Corner Rounding", @Composable {
                                                SettingSliderCard(title = "Corner Rounding", value = cornerRounding, valueRange = 4f..32f, percent = false, suffix = " dp", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { cornerRounding = it }
                                            }),
                                            Pair("Blur Intensity", @Composable {
                                                SettingSliderCard(title = "Blur Intensity", value = blurIntensity, valueRange = 0f..25f, percent = false, suffix = " dp", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { blurIntensity = it }
                                            }),
                                            Pair("Choose Wallpaper", @Composable {
                                                SettingActionCard(title = "Choose Wallpaper", subtitle = if (customWallpaperUri != null) "Custom Active" else "Default", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { wallpaperPickerLauncher.launch(arrayOf("image/*")) }
                                            }),
                                            Pair("Reset Wallpaper", @Composable {
                                                SettingActionCard(title = "Reset Wallpaper", subtitle = "", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { customWallpaperUri = null }
                                            })
                                        )),
                                        Pair("Dock Options", listOf(
                                            Pair("Dock Opacity", @Composable {
                                                SettingSliderCard(title = "Dock Opacity", value = dockTransparency, valueRange = 0f..1f, percent = true, surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { dockTransparency = it }
                                            }),
                                            Pair("Dock Background Image", @Composable {
                                                SettingActionCard(title = "Choose Dock Background", subtitle = if (dockBackgroundUri != null) "Custom Active" else "Default", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { dockBgPickerLauncher.launch(arrayOf("image/*")) }
                                            }),
                                            Pair("Reset Dock Background", @Composable {
                                                SettingActionCard(title = "Reset Dock Background", subtitle = "", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { dockBackgroundUri = null }
                                            }),
                                            Pair("Dock Size", @Composable {
                                                SettingSliderCard(title = "Dock Size", value = dockScale, valueRange = 0.5f..1.5f, percent = true, surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { dockScale = it }
                                            }),
                                            Pair("Dock App Corner Rounding", @Composable {
                                                SettingSliderCard(title = "Dock App Corner Rounding", value = dockAppCornerRounding, valueRange = 4f..32f, percent = false, suffix = " dp", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { dockAppCornerRounding = it }
                                            })
                                        )),
                                        Pair("Home & Clock", listOf(
                                            Pair("24-Hour Format", @Composable {
                                                SettingCard(title = "24-Hour Clock Format", surfaceColor = surfaceColor, textColor = textColor, cornerRounding = cornerRounding) {
                                                    Switch(checked = use24HourFormat, onCheckedChange = { use24HourFormat = it })
                                                }
                                            }),
                                            Pair("Show Date", @Composable {
                                                SettingCard(title = "Show Date Under Clock", surfaceColor = surfaceColor, textColor = textColor, cornerRounding = cornerRounding) {
                                                    Switch(checked = showDate, onCheckedChange = { showDate = it })
                                                }
                                            }),
                                            Pair("Clock Dark Text", @Composable {
                                                SettingCard(title = "Clock Dark Text Mode", surfaceColor = surfaceColor, textColor = textColor, cornerRounding = cornerRounding) {
                                                    Switch(checked = clockDarkText, onCheckedChange = { clockDarkText = it })
                                                }
                                            }),
                                            Pair("Clock Y Position", @Composable {
                                                SettingSliderCard(title = "Clock Y Position", value = clockYOffset, valueRange = 0f..maxClockY, percent = false, suffix = " dp", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { clockYOffset = it }
                                            }),
                                            Pair("Clock Size", @Composable {
                                                SettingSliderCard(title = "Clock Size", value = clockFontSize, valueRange = 36f..196f, percent = false, suffix = " sp", surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { clockFontSize = it }
                                            }),
                                            Pair("Clock Thickness", @Composable {
                                                SettingSliderCard(title = "Clock Thickness: ${weightNames[clockFontWeightIndex]}", value = clockFontWeightIndex.toFloat(), valueRange = 0f..(weightList.size - 1).toFloat(), steps = weightList.size - 2, percent = false, surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { clockFontWeightIndex = it.toInt() }
                                            }),
                                            Pair("Clock Font", @Composable {
                                                SettingSliderCard(title = "Clock Font: ${fontFamilyNames[clockFontFamilyIndex]}", value = clockFontFamilyIndex.toFloat(), valueRange = 0f..(fontFamilyList.size - 1).toFloat(), steps = fontFamilyList.size - 2, percent = false, surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { clockFontFamilyIndex = it.toInt() }
                                            }),
                                            Pair("Clock Shadow Intensity", @Composable {
                                                SettingSliderCard(title = "Clock Shadow Intensity", value = clockShadowIntensity, valueRange = 0f..1f, percent = true, surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { clockShadowIntensity = it }
                                            }),
                                            Pair("Hide App Labels", @Composable {
                                                SettingCard(title = "Hide App Labels in Drawer", surfaceColor = surfaceColor, textColor = textColor, cornerRounding = cornerRounding) {
                                                    Switch(checked = hideAppLabels, onCheckedChange = { hideAppLabels = it })
                                                }
                                            }),
                                            Pair("Drawer Grid Columns", @Composable {
                                                SettingSliderCard(title = "Drawer Columns: $drawerColumns", value = drawerColumns.toFloat(), valueRange = 3f..6f, steps = 2, percent = false, surfaceColor = surfaceColor, textColor = textColor, secondaryTextColor = secondaryTextColor, cornerRounding = cornerRounding) { drawerColumns = it.toInt() }
                                            })
                                        )),
                                        Pair("Gestures & Feedback", listOf(
                                            Pair("Double-Tap to Sleep", @Composable {
                                                SettingCard(title = "Double-Tap to Sleep", surfaceColor = surfaceColor, textColor = textColor, cornerRounding = cornerRounding) {
                                                    Switch(checked = doubleTapToSleep, onCheckedChange = { doubleTapToSleep = it })
                                                }
                                            }),
                                            Pair("Haptic Feedback", @Composable {
                                                SettingCard(title = "Haptic Feedback", surfaceColor = surfaceColor, textColor = textColor, cornerRounding = cornerRounding) {
                                                    Switch(checked = hapticFeedbackEnabled, onCheckedChange = { hapticFeedbackEnabled = it })
                                                }
                                            })
                                        )),
                                        Pair("Privacy & Apps", listOf(
                                            Pair("Hidden Apps", @Composable {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(cornerRounding.dp))
                                                        .background(surfaceColor)
                                                        .padding(16.dp)
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Text(text = "Hidden Apps (${hiddenPackageNames.size})", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                                        if (hiddenPackageNames.isEmpty()) {
                                                            Text(text = "Long-press any app in the drawer to hide it.", color = secondaryTextColor, fontSize = 12.sp)
                                                        } else {
                                                            Button(
                                                                onClick = { hiddenPackageNames = emptySet() },
                                                                colors = ButtonDefaults.buttonColors(containerColor = activeAccentColor)
                                                            ) {
                                                                Text(text = "Unhide All Apps", color = Color.White, fontSize = 12.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            })
                                        ))
                                    )

                                    val isSearching = searchQuery.isNotBlank()

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 16.dp)
                                    ) {
                                        if (isSearching) {
                                            val matchingItems = remember(allSettingsCategories, searchQuery) {
                                                allSettingsCategories.flatMap { it.second }.filter { containsAllCharsIgnoreCase(it.first, searchQuery) }
                                            }
                                            if (matchingItems.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "No settings found",
                                                        color = secondaryTextColor,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            } else {
                                                LazyColumn(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(matchingItems, key = { it.first }) { setting ->
                                                        setting.second()
                                                    }
                                                }
                                            }
                                        } else {
                                            AnimatedContent(
                                                targetState = selectedSettingsCategory,
                                                transitionSpec = {
                                                    if (targetState != null) {
                                                        (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { it } + fadeIn(animationSpec = tween(350)))
                                                            .togetherWith(slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { -it } + fadeOut(animationSpec = tween(350)))
                                                    } else {
                                                        (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { -it } + fadeIn(animationSpec = tween(350)))
                                                            .togetherWith(slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { it } + fadeOut(animationSpec = tween(350)))
                                                    }
                                                },
                                                label = "SettingsSubpageAnimation"
                                            ) { currentCategory ->
                                                if (currentCategory == null) {
                                                    LazyColumn(
                                                        modifier = Modifier.fillMaxSize(),
                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        items(allSettingsCategories, key = { it.first }) { category ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clip(RoundedCornerShape(cornerRounding.dp))
                                                                    .background(surfaceColor)
                                                                    .clickable { selectedSettingsCategory = category.first }
                                                                    .padding(16.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = category.first,
                                                                        color = textColor,
                                                                        fontSize = 15.sp,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                    Text(
                                                                        text = "→",
                                                                        color = secondaryTextColor,
                                                                        fontSize = 16.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Column(modifier = Modifier.fillMaxSize()) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(bottom = 14.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .clip(RoundedCornerShape(cornerRounding.dp))
                                                                    .background(surfaceColorStrong)
                                                                    .clickable { selectedSettingsCategory = null },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                BackArrowIcon(tint = textColor)
                                                            }
                                                            Text(
                                                                text = currentCategory,
                                                                color = textColor,
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        val categoryPair = allSettingsCategories.find { it.first == currentCategory }
                                                        if (categoryPair != null) {
                                                            LazyColumn(
                                                                modifier = Modifier.fillMaxSize(),
                                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                                            ) {
                                                                items(categoryPair.second, key = { it.first }) { setting ->
                                                                    setting.second()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(drawerColumns),
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(filteredApps, key = { it.packageName }) { app ->
                                            val isInDock = dockPackageNames.contains(app.packageName)
                                            DrawerAppItem(
                                                app = app,
                                                isInDock = isInDock,
                                                isDarkMode = isDarkMode,
                                                hideLabels = hideAppLabels,
                                                cornerRounding = cornerRounding,
                                                onClick = {
                                                    val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                                                    if (launchIntent != null) {
                                                        if (hapticFeedbackEnabled) {
                                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                                        }
                                                        context.startActivity(launchIntent)
                                                        isDrawerOpen = false
                                                    }
                                                },
                                                onUninstall = { pkg -> context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) },
                                                onHideApp = { pkg -> hiddenPackageNames = hiddenPackageNames + pkg },
                                                onAddToDock = { if (!dockPackageNames.contains(app.packageName)) dockPackageNames = dockPackageNames + app.packageName },
                                                onRemoveFromDock = { dockPackageNames = dockPackageNames - app.packageName }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(400)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                val notifPanelBg = if (isDarkMode) Color(0x9918181C) else Color(0x99FFFFFF)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(380.dp)
                        .background(notifPanelBg)
                        .graphicsLayer { clip = true }
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
                                color = textColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            BatteryIndicator(isDarkMode = isDarkMode)
                        }

                        val notifications = MainActivity.activeNotificationsState.value

                        if (notifications.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No notifications",
                                    color = hintColor,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(notifications, key = { it.key }) { notif ->
                                    NotificationCard(notif = notif, isDarkMode = isDarkMode, cornerRounding = cornerRounding)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingCard(title: String, surfaceColor: Color, textColor: Color, cornerRounding: Float, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRounding.dp))
            .background(surfaceColor)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun SettingSliderCard(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    percent: Boolean,
    suffix: String = "",
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    cornerRounding: Float,
    onValueChange: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRounding.dp))
            .background(surfaceColor)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val displayVal = if (percent) "${(value * 100).toInt()}%" else "${value.toInt()}$suffix"
                Text(text = displayVal, color = secondaryTextColor, fontSize = 13.sp)
            }
            Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
        }
    }
}

@Composable
fun SettingActionCard(
    title: String,
    subtitle: String,
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    cornerRounding: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRounding.dp))
            .background(surfaceColor)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = secondaryTextColor, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SettingsIcon(tint: Color = Color.White) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = 4.dp.toPx()
        val strokeWidth = 1.5.dp.toPx()

        drawCircle(color = tint, radius = radius, center = center, style = Stroke(width = strokeWidth))
        drawCircle(color = tint, radius = 1.25.dp.toPx(), center = center)

        val teethCount = 8
        for (i in 0 until teethCount) {
            val angle = i * (360f / teethCount) * (Math.PI / 180f)
            val x1 = center.x + (radius - 0.5f.dp.toPx()) * cos(angle).toFloat()
            val y1 = center.y + (radius - 0.5f.dp.toPx()) * sin(angle).toFloat()
            val x2 = center.x + (radius + 2.dp.toPx()) * cos(angle).toFloat()
            val y2 = center.y + (radius + 2.dp.toPx()) * sin(angle).toFloat()
            drawLine(color = tint, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
fun HomeIcon(tint: Color = Color.White) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width / 2f, 2f)
            lineTo(size.width - 3.dp.toPx(), size.height * 0.4f)
            lineTo(size.width - 4.dp.toPx(), size.height - 2.dp.toPx())
            lineTo(4.dp.toPx(), size.height - 2.dp.toPx())
            lineTo(4.dp.toPx(), size.height * 0.4f)
            close()
        }
        drawPath(path = path, color = tint, style = stroke)

        val doorPath = Path().apply {
            moveTo(size.width * 0.38f, size.height - 2.dp.toPx())
            lineTo(size.width * 0.38f, size.height * 0.58f)
            lineTo(size.width * 0.62f, size.height * 0.58f)
            lineTo(size.width * 0.62f, size.height - 2.dp.toPx())
        }
        drawPath(path = doorPath, color = tint, style = stroke)
    }
}

@Composable
fun BackArrowIcon(tint: Color = Color.White) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.width * 0.7f, 2.dp.toPx())
            lineTo(size.width * 0.2f, size.height / 2f)
            lineTo(size.width * 0.7f, size.height - 2.dp.toPx())
        }
        drawPath(path = path, color = tint, style = stroke)
    }
}

@Composable
fun BatteryIndicator(isDarkMode: Boolean) {
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

    val contentColor = if (isDarkMode) Color.White else Color.Black

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

            drawRoundRect(
                color = contentColor,
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = 1.5.dp.toPx())
            )

            drawRect(
                color = contentColor,
                topLeft = Offset(bodyWidth, bodyHeight / 2f - 2.dp.toPx()),
                size = Size(2.dp.toPx(), 4.dp.toPx())
            )

            val padding = 2.dp.toPx()
            val fillMaxWidth = (bodyWidth - (padding * 2)) * (batteryLevel / 100f)
            if (fillMaxWidth > 0f) {
                drawRoundRect(
                    color = contentColor,
                    topLeft = Offset(padding, padding),
                    size = Size(fillMaxWidth, bodyHeight - (padding * 2)),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Fill
                )
            }
        }

        Text(
            text = "$batteryLevel%",
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun NotificationCard(notif: NotifItem, isDarkMode: Boolean, cornerRounding: Float) {
    val cardBg = if (isDarkMode) Color(0x22FFFFFF) else Color(0x22000000)
    val iconContainerBg = if (isDarkMode) Color(0x33000000) else Color(0x33FFFFFF)
    val appNameColor = if (isDarkMode) Color(0xCCFFFFFF) else Color(0xCC000000)
    val titleColor = if (isDarkMode) Color.White else Color.Black
    val textColor = if (isDarkMode) Color(0xDDFFFFFF) else Color(0xDD000000)
    val actionBg = if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000)
    val actionTextColor = if (isDarkMode) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRounding.dp))
            .background(cardBg)
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
                        .background(iconContainerBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (notif.appIconBitmap != null) {
                        Image(
                            bitmap = notif.appIconBitmap,
                            contentDescription = notif.appName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Text(
                    text = notif.appName,
                    color = appNameColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (notif.title.isNotBlank()) {
                Text(
                    text = notif.title,
                    color = titleColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (notif.text.isNotBlank()) {
                Text(
                    text = notif.text,
                    color = textColor,
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
                            colors = ButtonDefaults.buttonColors(containerColor = actionBg),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = actionItem.title,
                                color = actionTextColor,
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

class CustomNotificationListener : android.service.notification.NotificationListenerService() {

    companion object {
        var instance: CustomNotificationListener? = null
    }

    private val appInfoCache = mutableMapOf<String, Pair<String, ImageBitmap?>>()

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

                val (appName, appIconBitmap) = appInfoCache.getOrPut(pkg) {
                    val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
                    val name = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg.substringAfterLast('.')
                    val bitmap = appInfo?.let {
                        try { pm.getApplicationIcon(pkg).toBitmap(width = 60, height = 60).asImageBitmap() } catch (e: Exception) { null }
                    }
                    name to bitmap
                }

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
                    appIconBitmap = appIconBitmap,
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

@Composable
fun TopCenterClock(
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    fontFamily: FontFamily,
    use24Hour: Boolean,
    showDate: Boolean,
    clockShadowIntensity: Float,
    isDarkText: Boolean,
    onLongClick: () -> Unit
) {
    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }

    LaunchedEffect(use24Hour, showDate) {
        while (true) {
            val timePattern = if (use24Hour) "HH:mm" else "hh:mm"
            timeText = SimpleDateFormat(timePattern, Locale.getDefault()).format(Date())
            if (showDate) {
                dateText = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
            }
            delay(1000L)
        }
    }

    val textColor = if (isDarkText) Color(0xFF1A1A1A) else Color.White
    val secondaryTextColor = if (isDarkText) Color(0xCC000000) else Color(0xDDFFFFFF)
    val shadowColor = if (isDarkText) Color(0x33FFFFFF) else Color(0x33000000)

    if (timeText.isNotEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .combinedClickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (clockShadowIntensity > 0f) {
                    Text(
                        text = timeText,
                        color = shadowColor.copy(alpha = (clockShadowIntensity * 0.5f).coerceIn(0f, 1f)),
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier
                            .offset(x = 2.dp, y = 3.dp)
                            .blur((5 * clockShadowIntensity).dp.coerceAtLeast(0.1.dp))
                    )
                }
                Text(
                    text = timeText,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    letterSpacing = 1.5.sp
                )
            }
            if (showDate && dateText.isNotBlank()) {
                Text(
                    text = dateText,
                    color = secondaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

fun containsAllCharsIgnoreCase(target: String, query: String): Boolean {
    val targetClean = target.lowercase()
    val queryClean = query.lowercase().filter { !it.isWhitespace() }
    if (queryClean.isEmpty()) return true
    return queryClean.all { char -> targetClean.contains(char) }
}

@Composable
fun DrawerAppItem(
    app: AppItem,
    isInDock: Boolean,
    isDarkMode: Boolean,
    hideLabels: Boolean,
    cornerRounding: Float,
    onClick: () -> Unit,
    onUninstall: (String) -> Unit,
    onHideApp: (String) -> Unit,
    onAddToDock: () -> Unit,
    onRemoveFromDock: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val textColor = if (isDarkMode) Color.White else Color.Black
    val iconBgColor = if (isDarkMode) Color(0x33FFFFFF) else Color(0x33000000)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRounding.dp))
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(cornerRounding.dp)).background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                if (app.iconBitmap != null) {
                    Image(bitmap = app.iconBitmap, contentDescription = app.label, modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape((cornerRounding * 0.75f).dp)), contentScale = ContentScale.Crop)
                }
            }
            if (!hideLabels) {
                Text(text = app.label, color = textColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { showMenu = false; onUninstall(app.packageName) })
            DropdownMenuItem(text = { Text("Hide App") }, onClick = { showMenu = false; onHideApp(app.packageName) })
            if (!isInDock) {
                DropdownMenuItem(text = { Text("Add to dock") }, onClick = { showMenu = false; onAddToDock() })
            } else {
                DropdownMenuItem(text = { Text("Remove from dock") }, onClick = { showMenu = false; onRemoveFromDock() })
            }
        }
    }
}

@Composable
fun DrawerTab(text: String, isSelected: Boolean, isDarkMode: Boolean, accentColor: Color, cornerRounding: Float, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val surfaceColorHover = if (isDarkMode) Color(0x22FFFFFF) else Color(0x22000000)
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> accentColor
            isHovered -> surfaceColorHover
            else -> Color.Transparent
        },
        label = "tab_bg"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRounding.dp))
            .background(backgroundColor)
            .hoverable(interactionSource = interactionSource)
            .clickable(indication = null, interactionSource = interactionSource) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else (if (isDarkMode) Color(0xB3FFFFFF) else Color(0xB3000000)),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
fun DockAppIcon(
    iconBitmap: ImageBitmap?,
    label: String,
    packageName: String,
    isInDock: Boolean,
    cornerRounding: Float,
    onUninstall: (String) -> Unit,
    onAddToDock: () -> Unit,
    onRemoveFromDock: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hoveredByMouse by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer { clip = false }
            .hoverable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .wrapContentSize(unbounded = true)
                .offset(y = (-56).dp)
                .zIndex(200f),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = hoveredByMouse && !showMenu,
                enter = fadeIn(animationSpec = tween(durationMillis = 150)),
                exit = fadeOut(animationSpec = tween(durationMillis = 100))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer { clip = false }
                ) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = Color(0xFF222222),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Canvas(modifier = Modifier.size(8.dp, 4.dp)) {
                        val trianglePath = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        }
                        drawPath(path = trianglePath, color = Color(0xFFE8E8E8))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer { clip = false }
                .clip(RoundedCornerShape(cornerRounding.dp))
                .combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
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
