package com.firsatavcisi.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.firsatavcisi.app.alert.AlertService
import com.firsatavcisi.app.alert.DealQueue
import com.firsatavcisi.app.alert.DndHelper
import com.firsatavcisi.app.alert.SleepSchedule
import com.firsatavcisi.app.data.Deal
import com.firsatavcisi.app.data.DealStore
import com.firsatavcisi.app.data.Settings
import com.firsatavcisi.app.data.Settings.AlarmMode
import com.firsatavcisi.app.notifications.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF0B1220)
private val Panel = Color(0xFF141F32)
private val PanelLight = Color(0xFF1C2A40)
private val Mint = Color(0xFF42D6A4)
private val Amber = Color(0xFFFFC46B)
private val Muted = Color(0xFF9AA9BC)

class MainActivity : ComponentActivity() {
    var listenerEnabled by mutableStateOf(false)
        private set
    var notificationAllowed by mutableStateOf(true)
        private set
    var batteryUnrestricted by mutableStateOf(false)
        private set
    var settingsRefresh by mutableIntStateOf(0)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshState()
        SleepSchedule.schedule(this)
        setContent {
            FirsatAvcisiApp(
                listenerEnabled = listenerEnabled,
                notificationAllowed = notificationAllowed,
                batteryUnrestricted = batteryUnrestricted,
                settingsRefresh = settingsRefresh,
                onRefresh = { refreshState() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        listenerEnabled = AndroidSettings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        notificationAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
        batteryUnrestricted = getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(packageName) == true
        settingsRefresh++
    }
}

@Composable
private fun FirsatAvcisiApp(
    listenerEnabled: Boolean,
    notificationAllowed: Boolean,
    batteryUnrestricted: Boolean,
    settingsRefresh: Int,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var deals by remember { mutableStateOf(DealStore.history(context)) }
    var groups by remember { mutableStateOf(Settings.groups(context).joinToString(", ")) }
    var voiceEnabled by remember { mutableStateOf(DealStore.voiceEnabled(context)) }
    var sleepEnabled by remember { mutableStateOf(SleepSchedule.enabled(context)) }
    var sleepStart by remember { mutableStateOf(SleepSchedule.startTime(context)) }
    var sleepEnd by remember { mutableStateOf(SleepSchedule.endTime(context)) }

    var minDiscount by remember { mutableStateOf(Settings.minDiscount(context).toInt().toString()) }
    var cheapPrice by remember { mutableStateOf(Settings.cheapPrice(context).toInt().toString()) }
    var cheapDiscount by remember { mutableStateOf(Settings.cheapDiscount(context).toInt().toString()) }
    var maxPrice by remember { mutableStateOf(Settings.maxPrice(context).toInt().toString()) }
    var alarmSeconds by remember { mutableStateOf(Settings.alarmSeconds(context).toString()) }

    var forceVolume by remember { mutableStateOf(Settings.forceVolume(context)) }
    var ttsAlways by remember { mutableStateOf(Settings.ttsAlways(context)) }
    var ocrEnabled by remember { mutableStateOf(Settings.ocrEnabled(context)) }
    var vibration by remember { mutableStateOf(Settings.vibration(context)) }
    var alarmMode by remember { mutableStateOf(Settings.alarmMode(context)) }
    var dndBypass by remember { mutableStateOf(Settings.dndBypass(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(context, if (granted) "Bildirim izni verildi" else "Bildirim izni verilmedi", Toast.LENGTH_SHORT).show()
        onRefresh()
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, "Sesli komut için mikrofon izni gerekiyor", Toast.LENGTH_SHORT).show()
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            handleVoiceCommand(context, spoken, deals.firstOrNull())
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            secondary = Amber,
            background = Navy,
            surface = Panel,
            onPrimary = Navy,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Navy,
            bottomBar = {
                NavigationBar(
                    containerColor = Panel,
                    contentColor = Color.White,
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { deals = DealStore.history(context); selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Ana sayfa") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { deals = DealStore.history(context); selectedTab = 1 },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("Fırsatlar") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Ayarlar") }
                    )
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    listenerEnabled = listenerEnabled,
                    notificationAllowed = notificationAllowed,
                    deals = deals,
                    onEnableTracking = {
                        runCatching { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    },
                    onRefresh = {
                        onRefresh()
                        deals = DealStore.history(context)
                    },
                    onOpenLast = { deals.firstOrNull()?.url?.let { openUrl(context, it) } },
                    onVoice = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            launchSpeech(context, speechLauncher)
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else Toast.makeText(context, "Bildirim ayarlarını telefon ayarlarından kontrol et", Toast.LENGTH_SHORT).show()
                    },
                    onStopAlarm = {
                        AlertService.stop(context)
                        Toast.makeText(context, "Alarm kapatıldı", Toast.LENGTH_SHORT).show()
                    }
                )
                1 -> DealListScreen(
                    modifier = Modifier.padding(padding),
                    deals = deals,
                    onOpen = { it.url?.let { url -> openUrl(context, url) } }
                )
                else -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    groups = groups,
                    onGroupsChange = { groups = it },
                    onSaveGroups = {
                        Settings.setGroups(context, groups)
                        groups = Settings.groups(context).joinToString(", ")
                        Toast.makeText(context, if (groups.isBlank()) "Tüm sohbetler izlenecek" else "Gruplar kaydedildi", Toast.LENGTH_SHORT).show()
                    },
                    voiceEnabled = voiceEnabled,
                    onVoiceChange = {
                        voiceEnabled = it
                        DealStore.setVoiceEnabled(context, it)
                    },
                    listenerEnabled = listenerEnabled,
                    notificationAllowed = notificationAllowed,
                    batteryUnrestricted = batteryUnrestricted,
                    sleepEnabled = sleepEnabled,
                    sleepStart = sleepStart,
                    sleepEnd = sleepEnd,
                    sleepingNow = SleepSchedule.isSleeping(context),
                    settingsRefresh = settingsRefresh,
                    minDiscount = minDiscount,
                    cheapPrice = cheapPrice,
                    cheapDiscount = cheapDiscount,
                    maxPrice = maxPrice,
                    alarmSeconds = alarmSeconds,
                    forceVolume = forceVolume,
                    ttsAlways = ttsAlways,
                    ocrEnabled = ocrEnabled,
                    vibration = vibration,
                    alarmMode = alarmMode,
                    dndBypass = dndBypass,
                    dndAccessGranted = DndHelper.isAccessGranted(context),
                    dndActive = DndHelper.isActive(context),
                    onAlarmModeChange = {
                        alarmMode = it
                        Settings.setAlarmMode(context, it)
                    },
                    onDndBypassChange = {
                        dndBypass = it
                        Settings.setDndBypass(context, it)
                    },
                    onOpenDndSettings = {
                        runCatching { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                    },
                    onTestAlarm = {
                        runCatching {
                            AlertService.start(
                                context,
                                Deal(
                                    id = "test-${System.currentTimeMillis()}",
                                    productName = "Test fırsatı",
                                    currentPrice = 28.0,
                                    oldPrice = 999.0,
                                    discountPercent = 97.0,
                                    url = null,
                                    rawMessage = "test",
                                    groupName = "Test",
                                    createdAt = System.currentTimeMillis(),
                                    priority = 2
                                )
                            )
                        }
                        Toast.makeText(context, "Test alarmı: ${Settings.AlarmMode.label(Settings.alarmMode(context))}", Toast.LENGTH_SHORT).show()
                    },
                    onMinDiscountChange = {
                        minDiscount = it.filter(Char::isDigit).take(3)
                        minDiscount.toFloatOrNull()?.let { value -> Settings.setNumber(context, Settings.K_MIN_DISCOUNT, value.coerceIn(1f, 100f)) }
                    },
                    onCheapPriceChange = {
                        cheapPrice = it.filter(Char::isDigit).take(7)
                        cheapPrice.toFloatOrNull()?.let { value -> Settings.setNumber(context, Settings.K_CHEAP_PRICE, value) }
                    },
                    onCheapDiscountChange = {
                        cheapDiscount = it.filter(Char::isDigit).take(3)
                        cheapDiscount.toFloatOrNull()?.let { value -> Settings.setNumber(context, Settings.K_CHEAP_DISCOUNT, value.coerceIn(1f, 100f)) }
                    },
                    onMaxPriceChange = {
                        maxPrice = it.filter(Char::isDigit).take(8)
                        Settings.setNumber(context, Settings.K_MAX_PRICE, maxPrice.toFloatOrNull() ?: 0f)
                    },
                    onAlarmSecondsChange = {
                        alarmSeconds = it.filter(Char::isDigit).take(3)
                        Settings.setInt(context, Settings.K_ALARM_SECONDS, (alarmSeconds.toIntOrNull() ?: 90).coerceIn(0, 600))
                    },
                    onForceVolumeChange = { forceVolume = it; Settings.setBool(context, Settings.K_FORCE_VOLUME, it) },
                    onTtsAlwaysChange = { ttsAlways = it; Settings.setBool(context, Settings.K_TTS_ALWAYS, it) },
                    onOcrChange = { ocrEnabled = it; Settings.setBool(context, Settings.K_OCR, it) },
                    onVibrationChange = { vibration = it; Settings.setBool(context, Settings.K_VIBRATION, it) },
                    onSleepEnabledChange = { enabled ->
                        if (SleepSchedule.configure(context, enabled, sleepStart, sleepEnd)) {
                            sleepEnabled = enabled
                            Toast.makeText(context, if (enabled) "Uyku takvimi etkinleştirildi" else "Uyku takvimi kapatıldı", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Başlangıç ve bitiş saatleri farklı olmalı", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSleepStartChange = { value ->
                        if (SleepSchedule.configure(context, sleepEnabled, value, sleepEnd)) sleepStart = value
                        else Toast.makeText(context, "Başlangıç ve bitiş saatleri aynı olamaz", Toast.LENGTH_SHORT).show()
                    },
                    onSleepEndChange = { value ->
                        if (SleepSchedule.configure(context, sleepEnabled, sleepStart, value)) sleepEnd = value
                        else Toast.makeText(context, "Başlangıç ve bitiş saatleri aynı olamaz", Toast.LENGTH_SHORT).show()
                    },
                    onOpenExactAlarmSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                            }
                        }
                    },
                    onOpenFullScreenSettings = {
                        if (Build.VERSION.SDK_INT >= 34) {
                            runCatching {
                                context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                            }
                        }
                    },
                    onOpenListenerSettings = {
                        runCatching { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    },
                    onOpenAppNotificationSettings = {
                        runCatching {
                            context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    },
                    onOpenBatterySettings = {
                        runCatching {
                            context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    listenerEnabled: Boolean,
    notificationAllowed: Boolean,
    deals: List<Deal>,
    onEnableTracking: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLast: () -> Unit,
    onVoice: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStopAlarm: () -> Unit
) {
    val context = LocalContext.current
    val lastEventAt = remember { DealStore.lastEventAt(context) }
    val lastEventText = remember { DealStore.lastEventText(context) }
    val eventCount = remember { DealStore.eventCount(context) }
    val alarmSeconds = remember { Settings.alarmSeconds(context) }
    val maxPrice = remember { Settings.maxPrice(context) }
    val groups = remember { Settings.groups(context) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(Mint.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Mint, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("M.G WP AVCI", fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text("WhatsApp fırsat takipçisi", color = Muted, fontSize = 12.sp)
            }
            Surface(color = if (listenerEnabled && notificationAllowed) Mint.copy(alpha = 0.14f) else Amber.copy(alpha = 0.14f), shape = CircleShape) {
                Text(
                    if (listenerEnabled && notificationAllowed) "TAKİP AÇIK" else "KURULUM GEREKİYOR",
                    color = if (listenerEnabled && notificationAllowed) Mint else Amber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, tint = Mint, contentDescription = null, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Canlı takip sistemi", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Text(
                    when {
                        !listenerEnabled -> "WhatsApp mesajlarını okuyabilmek için bildirim erişimini etkinleştirmen gerekiyor."
                        !notificationAllowed -> "Bildirim izni kapalı. Alarm çalsa bile ekranda uyarı görünmez, izni aç."
                        else -> "Takip açık. Son WhatsApp olayı: ${formatAgo(lastEventAt)}"
                    },
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                if (lastEventText.isNotBlank()) {
                    Text("Son okunan mesaj: $lastEventText", color = Muted, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = if (listenerEnabled && notificationAllowed) onRefresh else if (!listenerEnabled) onEnableTracking else onRequestNotificationPermission,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (!listenerEnabled) "WhatsApp takibini etkinleştir"
                        else if (!notificationAllowed) "Bildirim iznini ver"
                        else "Durumu yenile",
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bildirim iznini kontrol et")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Yakalanan", deals.size.toString(), Icons.Default.History, Modifier.weight(1f))
            MetricCard("Acil fırsat", deals.count { it.priority >= 1 }.toString(), Icons.Default.NotificationsActive, Modifier.weight(1f))
        }

        Card(colors = CardDefaults.cardColors(containerColor = PanelLight), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("FIRSAT KURALLARI", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                RuleRow("≤${Settings.cheapPrice(context).toInt()} TL ve ≥%${Settings.cheapDiscount(context).toInt()}", "Çok yüksek öncelik alarmı", Mint)
                Divider(color = Color.White.copy(alpha = 0.08f))
                RuleRow("≥%${Settings.minDiscount(context).toInt()} indirim", "Arama benzeri ses + titreşim", Amber)
                Divider(color = Color.White.copy(alpha = 0.08f))
                RuleRow("Diğer fırsatlar", "Sessiz geçmiş kaydı", Muted)
                if (maxPrice > 0) {
                    Divider(color = Color.White.copy(alpha = 0.08f))
                    RuleRow(">${maxPrice.toInt()} TL", "Alarm çalınmaz, sadece kaydedilir", Muted)
                }
                Text(
                    buildString {
                        append("Alarm: ")
                        append(Settings.AlarmMode.label(Settings.alarmMode(context)))
                        append(" · Süre: ")
                        append(if (alarmSeconds <= 0) "kapatılana kadar" else "$alarmSeconds sn")
                        append(" · İzlenen: ")
                        append(if (groups.isEmpty()) "tüm sohbetler" else groups.joinToString(", "))
                        append(" · Okunan olay: $eventCount")
                    },
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onVoice,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PanelLight),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Sesli komut")
            }
            OutlinedButton(onClick = onStopAlarm, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text("Alarmı kapat${if (DealQueue.size() > 0) " (${DealQueue.size()})" else ""}")
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Son yakalanan fırsat", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null); Text("Yenile") }
                }
                val last = deals.firstOrNull()
                if (last == null) {
                    Text("Henüz fırsat yakalanmadı. Yeni bir WhatsApp mesajı bekleniyor.", color = Muted, fontSize = 13.sp)
                } else {
                    DealSummary(last, onClick = onOpenLast)
                }
            }
        }
        Text("Gizlilik: Mesaj analizi, görsel okuma ve geçmiş kayıtları bu telefonda tutulur. Harici sunucuya gönderim yapılmaz.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Mint, modifier = Modifier.size(21.dp))
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RuleRow(title: String, subtitle: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DealListScreen(modifier: Modifier, deals: List<Deal>, onOpen: (Deal) -> Unit) {
    Column(modifier = modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Yakalanan fırsatlar", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Son 100 fırsat bu cihazda saklanır.", color = Muted, fontSize = 12.sp)
        if (deals.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz kayıt yok", color = Muted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(deals, key = { it.id }) { deal ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(deal) },
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(deal.productName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                PriorityPill(deal.priority)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(deal.currentPrice?.let { Deal.formatPrice(it) } ?: "Fiyat doğrulanamadı", color = Mint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(9.dp))
                                deal.discountPercent?.let { Text("%${formatPercent(it)} indirim", color = Amber, fontSize = 12.sp) }
                            }
                            Text(
                                formatDate(deal.createdAt) + if (deal.source == "ocr") " · görselden okundu" else "",
                                color = Muted,
                                fontSize = 11.sp
                            )
                            if (!deal.url.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(deal.url, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun PriorityPill(priority: Int) {
    val color = when (priority) { 2 -> Mint; 1 -> Amber; else -> Muted }
    val text = when (priority) { 2 -> "ÇOK YÜKSEK"; 1 -> "ACİL"; else -> "NORMAL" }
    Surface(color = color.copy(alpha = 0.14f), shape = CircleShape) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

@Composable
private fun DealSummary(deal: Deal, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(deal.productName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            PriorityPill(deal.priority)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(deal.currentPrice?.let { Deal.formatPrice(it) } ?: "Fiyat doğrulanamadı", color = Mint, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            deal.discountPercent?.let { Text("  %${formatPercent(it)} indirim", color = Amber, fontSize = 12.sp) }
        }
        Text(formatDate(deal.createdAt), color = Muted, fontSize = 11.sp)
        if (!deal.url.isNullOrBlank()) {
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("Ürün bağlantısını aç")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    groups: String,
    onGroupsChange: (String) -> Unit,
    onSaveGroups: () -> Unit,
    voiceEnabled: Boolean,
    onVoiceChange: (Boolean) -> Unit,
    listenerEnabled: Boolean,
    notificationAllowed: Boolean,
    batteryUnrestricted: Boolean,
    sleepEnabled: Boolean,
    sleepStart: String,
    sleepEnd: String,
    sleepingNow: Boolean,
    settingsRefresh: Int,
    minDiscount: String,
    cheapPrice: String,
    cheapDiscount: String,
    maxPrice: String,
    alarmSeconds: String,
    forceVolume: Boolean,
    ttsAlways: Boolean,
    ocrEnabled: Boolean,
    vibration: Boolean,
    alarmMode: Int,
    dndBypass: Boolean,
    dndAccessGranted: Boolean,
    dndActive: Boolean,
    onMinDiscountChange: (String) -> Unit,
    onCheapPriceChange: (String) -> Unit,
    onCheapDiscountChange: (String) -> Unit,
    onMaxPriceChange: (String) -> Unit,
    onAlarmSecondsChange: (String) -> Unit,
    onForceVolumeChange: (Boolean) -> Unit,
    onTtsAlwaysChange: (Boolean) -> Unit,
    onOcrChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onAlarmModeChange: (Int) -> Unit,
    onDndBypassChange: (Boolean) -> Unit,
    onOpenDndSettings: () -> Unit,
    onTestAlarm: () -> Unit,
    onSleepEnabledChange: (Boolean) -> Unit,
    onSleepStartChange: (String) -> Unit,
    onSleepEndChange: (String) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val context = LocalContext.current
    val exactAlarmAllowed = remember(settingsRefresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val fullScreenAllowed = remember(settingsRefresh) {
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("Ayarlar", fontSize = 23.sp, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Kurulum durumu", fontWeight = FontWeight.Bold)
                SetupRow("Bildirim erişimi (WhatsApp okuma)", listenerEnabled, "Aç", onOpenListenerSettings)
                Divider(color = Color.White.copy(alpha = 0.08f))
                SetupRow("Uygulama bildirim izni", notificationAllowed, "Aç", onOpenAppNotificationSettings)
                Divider(color = Color.White.copy(alpha = 0.08f))
                SetupRow("Tam ekran alarm izni", fullScreenAllowed, "İzni aç", onOpenFullScreenSettings)
                Divider(color = Color.White.copy(alpha = 0.08f))
                SetupRow("Kesin zamanlı alarm izni", exactAlarmAllowed, "İzni aç", onOpenExactAlarmSettings)
                Divider(color = Color.White.copy(alpha = 0.08f))
                SetupRow("Pil optimizasyonu kapalı", batteryUnrestricted, "Ayarla", onOpenBatterySettings)
                if (!batteryUnrestricted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Tecno/HiOS: ayrıca otomatik başlatmaya izin ver.", color = Amber, fontSize = 11.sp)
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("WhatsApp grup takibi", fontWeight = FontWeight.Bold)
                Text("Birden fazla grubu virgülle ayırarak yaz. Boş bırakırsan tüm sohbetler izlenir.", color = Muted, fontSize = 12.sp)
                OutlinedTextField(
                    value = groups,
                    onValueChange = onGroupsChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    label = { Text("Grup adları") },
                    placeholder = { Text("Örn: Onual, Fırsat Avcıları") }
                )
                Button(onClick = onSaveGroups, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy)) {
                    Text("Grupları kaydet", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Fırsat eşikleri", fontWeight = FontWeight.Bold)
                Text("Kendi alışveriş alışkanlığına göre ayarla. Değişiklik anında kaydedilir.", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Min indirim %", minDiscount, onMinDiscountChange, Modifier.weight(1f))
                    NumberField("Ucuz fiyat TL", cheapPrice, onCheapPriceChange, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Ucuz indirim %", cheapDiscount, onCheapDiscountChange, Modifier.weight(1f))
                    NumberField("Üst fiyat TL", maxPrice, onMaxPriceChange, Modifier.weight(1f))
                }
                Text("Üst fiyat 0 ise sınır yok. Üst fiyatı aşan ürünler alarm çalmaz, sadece geçmişe yazılır.", color = Muted, fontSize = 11.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Alarm davranışı", fontWeight = FontWeight.Bold)
                Text("Alarm nasıl uyarsın?", color = Muted, fontSize = 12.sp)
                AlarmMode.all.forEach { mode ->
                    val selected = alarmMode == mode
                    Surface(
                        color = if (selected) Mint.copy(alpha = 0.16f) else PanelLight,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onAlarmModeChange(mode) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(16.dp)
                                    .background(if (selected) Mint else Color.Transparent, CircleShape)
                            )
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(AlarmMode.label(mode), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Mint else Color.White)
                                Text(AlarmMode.description(mode), color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onTestAlarm, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Alarmı şimdi dene")
                }
                Divider(color = Color.White.copy(alpha = 0.08f))
                NumberField("Alarm süresi (saniye, 0 = süresiz)", alarmSeconds, onAlarmSecondsChange, Modifier.fillMaxWidth())
                ToggleRow("Ses seviyesini yükselt", "Alarm çalarken ses seviyesi düşükse geçici olarak yükseltilir.", forceVolume, onForceVolumeChange)
                Divider(color = Color.White.copy(alpha = 0.08f))
                ToggleRow("Titreşim", "Ses + titreşim modunda titreşim çalışsın.", vibration, onVibrationChange)
                Divider(color = Color.White.copy(alpha = 0.08f))
                ToggleRow("Her zaman sesli anons", "Kapalıysa anons yalnızca Bluetooth bağlıyken okunur.", ttsAlways, onTtsAlwaysChange)
                Divider(color = Color.White.copy(alpha = 0.08f))
                ToggleRow("Görselden fiyat okuma (OCR)", "Mesajda fiyat yoksa bildirim görseli çevrimdışı okunur. İnternet gerekmez.", ocrEnabled, onOcrChange)
                Divider(color = Color.White.copy(alpha = 0.08f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bluetooth anonsu", fontWeight = FontWeight.SemiBold)
                        Text(if (voiceEnabled) "Açık" else "Kapalı", color = if (voiceEnabled) Mint else Muted, fontSize = 12.sp)
                    }
                    Switch(checked = voiceEnabled, onCheckedChange = onVoiceChange)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Rahatsız Etme (DND) aşma", fontWeight = FontWeight.Bold)
                        Text(
                            "Telefon Rahatsız Etme modundayken de alarmın sesi/titreşimi çalışsın. Bunun için Android'den Rahatsız Etme erişimi vermen gerekir.",
                            color = Muted, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                    Switch(checked = dndBypass, onCheckedChange = onDndBypassChange)
                }
                Surface(
                    color = when {
                        !dndAccessGranted -> Amber.copy(alpha = 0.14f)
                        dndActive -> Mint.copy(alpha = 0.14f)
                        else -> PanelLight
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        when {
                            !dndAccessGranted -> "Rahatsız Etme erişimi verilmedi. İzin verilmezse alarm DND sırasında sessiz kalır."
                            dndActive -> "Rahatsız Etme şu anda açık · alarm çalarken geçici olarak kaldırılacak, sonra geri açılacak."
                            else -> "Rahatsız Etme erişimi verildi · şu anda DND kapalı."
                        },
                        color = when {
                            !dndAccessGranted -> Amber
                            dndActive -> Mint
                            else -> Muted
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(11.dp)
                    )
                }
                if (!dndAccessGranted) {
                    OutlinedButton(onClick = onOpenDndSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Rahatsız Etme erişimini aç")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Uyku modu", fontWeight = FontWeight.Bold)
                        Text("Belirlediğin saatlerde alarm, ses, titreşim ve fırsat bildirimleri durur. Fırsatlar geçmişe kaydedilmeye devam eder.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Switch(checked = sleepEnabled, onCheckedChange = onSleepEnabledChange)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Başlangıç", color = Muted, fontSize = 11.sp)
                        TextButton(onClick = {
                            val parts = sleepStart.split(":")
                            TimePickerDialog(context, { _, hour, minute -> onSleepStartChange(String.format(Locale.US, "%02d:%02d", hour, minute)) }, parts[0].toInt(), parts[1].toInt(), true).show()
                        }) { Text(sleepStart, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    }
                    Text("→", color = Muted, fontSize = 20.sp)
                    Column(Modifier.weight(1f)) {
                        Text("Bitiş", color = Muted, fontSize = 11.sp)
                        TextButton(onClick = {
                            val parts = sleepEnd.split(":")
                            TimePickerDialog(context, { _, hour, minute -> onSleepEndChange(String.format(Locale.US, "%02d:%02d", hour, minute)) }, parts[0].toInt(), parts[1].toInt(), true).show()
                        }) { Text(sleepEnd, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Surface(color = if (sleepingNow) Mint.copy(alpha = 0.14f) else PanelLight, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        if (!sleepEnabled) "Uyku takvimi kapalı" else if (sleepingNow) "Şu anda uyku modu açık · fırsatlar sessizce kaydediliyor" else "Uyku modu şu anda kapalı · alarmlar açık",
                        color = if (sleepingNow) Mint else Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(11.dp)
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Mint)
                    Spacer(Modifier.width(8.dp))
                    Text("Gizlilik ve çalışma şekli", fontWeight = FontWeight.Bold)
                }
                Text(
                    "Uygulama yalnızca WhatsApp bildirimlerinde görünen içeriği ve bildirim görselini okur. İnternet izni yoktur; hiçbir mesaj veya görsel cihazdan çıkmaz. Bildirim önizlemesi gizlenirse uygulama mesajı göremez. Otomatik satın alma yapılmaz.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Mint)
                    Spacer(Modifier.width(9.dp))
                    Text("Araç modu için Bluetooth ses çıkışı kullanılır.", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SetupRow(label: String, ok: Boolean, actionText: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(if (ok) Mint else Amber, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(if (ok) "Hazır" else "Gerekli", color = if (ok) Mint else Amber, fontSize = 11.sp)
        }
        if (!ok) OutlinedButton(onClick = onAction) { Text(actionText, fontSize = 12.sp) }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        label = { Text(label, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun launchSpeech(context: Context, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Komutunu söyle")
    }
    runCatching { launcher.launch(intent) }
        .onFailure { Toast.makeText(context, "Bu cihazda sesli komut hizmeti bulunamadı", Toast.LENGTH_SHORT).show() }
}

private fun handleVoiceCommand(context: Context, spoken: String, lastDeal: Deal?) {
    val command = spoken.lowercase(Locale("tr", "TR"))
    when {
        "alarmı kapat" in command || "alarmı durdur" in command || "sustur" in command -> {
            AlertService.dismiss(context)
            Toast.makeText(context, "Alarm kapatıldı", Toast.LENGTH_SHORT).show()
        }
        "fırsatı aç" in command || "linki aç" in command || "ürünü aç" in command -> {
            val url = lastDeal?.url
            if (url != null) openUrl(context, url) else Toast.makeText(context, "Açılacak fırsat bağlantısı yok", Toast.LENGTH_SHORT).show()
        }
        "tekrar oku" in command || "tekrar et" in command -> {
            val deal = lastDeal
            if (deal != null) speakOnce(context, "Son fırsat: ${deal.voiceSummary()}.")
            else Toast.makeText(context, "Okunacak fırsat yok", Toast.LENGTH_SHORT).show()
        }
        "geç" in command || "atla" in command -> Toast.makeText(context, "Son fırsat geçildi", Toast.LENGTH_SHORT).show()
        else -> Toast.makeText(context, "Komut anlaşılamadı: $spoken", Toast.LENGTH_SHORT).show()
    }
}

private fun speakOnce(context: Context, text: String) {
    var engine: TextToSpeech? = null
    engine = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            engine?.language = Locale("tr", "TR")
            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "firsat-repeat")
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show() }
}

private fun formatPercent(value: Double): String = String.format(Locale("tr", "TR"), "%.1f", value).removeSuffix(",0")

private fun formatDate(timestamp: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date(timestamp))

private fun formatAgo(timestamp: Long): String {
    if (timestamp <= 0L) return "henüz mesaj okunmadı"
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000L
    return when {
        minutes < 1 -> "az önce"
        minutes < 60 -> "$minutes dakika önce"
        minutes < 1440 -> "${minutes / 60} saat önce"
        else -> formatDate(timestamp)
    }
}
