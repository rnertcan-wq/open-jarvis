package com.firsatavcisi.app.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firsatavcisi.app.notifications.NotificationHelper
import kotlin.math.roundToInt

class AlarmActivity : ComponentActivity() {
    private var dealId by mutableStateOf("")
    private var product by mutableStateOf("Yeni firsat")
    private var price by mutableStateOf("")
    private var discount by mutableStateOf("")
    private var url by mutableStateOf("")
    private var priority by mutableStateOf(1)
    private var queued by mutableIntStateOf(0)

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NotificationHelper.ACTION_ALARM_STOPPED) closeQuietly()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareWakeScreen()
        readExtras(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF42D6A4), background = Color(0xFF07111F), surface = Color(0xFF132238))) {
                AlarmScreen(
                    product = product,
                    price = price,
                    discount = discount,
                    priority = priority,
                    hasUrl = url.isNotBlank(),
                    queued = queued,
                    onDismiss = { dismissAlarm() },
                    onOpenDeal = { openDeal() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(NotificationHelper.ACTION_ALARM_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stopReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(stopReceiver) }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    private fun prepareWakeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.statusBarColor = android.graphics.Color.rgb(7, 17, 31)
        window.navigationBarColor = android.graphics.Color.rgb(7, 17, 31)
    }

    private fun readExtras(source: Intent?) {
        val newId = source?.getStringExtra(EXTRA_ID).orEmpty()
        if (newId.isNotBlank() && newId != dealId) queued = DealQueue.size()
        dealId = newId
        product = source?.getStringExtra(EXTRA_PRODUCT) ?: "Yeni firsat"
        price = source?.getStringExtra(EXTRA_PRICE).orEmpty()
        discount = source?.getStringExtra(EXTRA_DISCOUNT).orEmpty()
        priority = source?.getIntExtra(EXTRA_PRIORITY, 1) ?: 1
        url = source?.getStringExtra(EXTRA_URL).orEmpty()
    }

    private fun dismissAlarm() {
        AlertService.dismiss(this)
        closeQuietly()
    }

    private fun closeQuietly() {
        runCatching { finishAndRemoveTask() }
    }

    private fun openDeal() {
        if (url.isNotBlank()) runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_PRODUCT = "product"
        const val EXTRA_PRICE = "price"
        const val EXTRA_DISCOUNT = "discount"
        const val EXTRA_PRIORITY = "priority"
        const val EXTRA_URL = "url"
    }
}

@Composable
private fun AlarmScreen(
    product: String,
    price: String,
    discount: String,
    priority: Int,
    hasUrl: Boolean,
    queued: Int,
    onDismiss: () -> Unit,
    onOpenDeal: () -> Unit
) {
    BackHandler(enabled = true) { /* Alarm kapatilana kadar ekranda kalir. */ }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF07111F), Color(0xFF15263D), Color(0xFF07111F))))
            .padding(horizontal = 26.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(Color(0xFF42D6A4).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color(0xFF42D6A4), modifier = Modifier.size(38.dp))
            }
            Text("M.G WP AVCI", color = Color(0xFF42D6A4), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(if (priority == 2) "COK YUKSEK INDIRIM" else "YENI FIRSAT", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color(0xFFFFC46B), modifier = Modifier.size(34.dp))
            Text(product, color = Color.White, fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (price.isNotBlank()) Text(price, color = Color(0xFF42D6A4), fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
            if (discount.isNotBlank()) Text("%$discount indirim", color = Color(0xFFFFC46B), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (queued > 0) Text("$queued firsat daha sirada", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
            if (hasUrl) {
                Button(onClick = onOpenDeal, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42D6A4), contentColor = Color(0xFF07111F))) {
                    Text("Firsati ac", fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Alarmi kapatmak icin saga kaydir", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            SwipeToDismiss(onDismiss = onDismiss)
            Text("Kapatmazsan alarm kisa sure sonra kendiliginden susar.", color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SwipeToDismiss(onDismiss: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(41.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(6.dp)
    ) {
        val maxDrag = with(LocalDensity.current) { (maxWidth - 70.dp).toPx().coerceAtLeast(1f) }
        var offsetPx by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("KAPAT", color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .size(70.dp)
                .clip(CircleShape)
                .background(Color(0xFF42D6A4))
                .pointerInput(maxDrag) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            offsetPx = (offsetPx + amount).coerceIn(0f, maxDrag)
                        },
                        onDragEnd = {
                            if (offsetPx >= maxDrag * 0.72f) onDismiss() else offsetPx = 0f
                        },
                        onDragCancel = { offsetPx = 0f }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Alarmi kapatmak icin kaydir", tint = Color(0xFF07111F), modifier = Modifier.size(38.dp))
        }
    }
}
