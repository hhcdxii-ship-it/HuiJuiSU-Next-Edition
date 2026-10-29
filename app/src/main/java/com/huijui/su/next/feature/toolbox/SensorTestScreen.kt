package com.huijui.su.next.feature.toolbox

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.su.next.core.ui.component.GlassCard
import com.huijui.su.next.core.ui.layout.HuijuiLayout
import com.huijui.su.next.core.utils.AppSettings

@Composable
fun SensorTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // ===== 传感器状态 =====
    var accelX by remember { mutableFloatStateOf(0f) }
    var accelY by remember { mutableFloatStateOf(0f) }
    var accelZ by remember { mutableFloatStateOf(0f) }

    var gyroX by remember { mutableFloatStateOf(0f) }
    var gyroY by remember { mutableFloatStateOf(0f) }
    var gyroZ by remember { mutableFloatStateOf(0f) }

    var lightLux by remember { mutableFloatStateOf(0f) }
    var proximityCm by remember { mutableFloatStateOf(0f) }

    // 传感器可用性
    val hasAccel = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null }
    val hasGyro = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null }
    val hasLight = remember { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null }
    val hasProximity = remember { sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null }

    // ===== 生命周期管理 =====
    DisposableEffect(Unit) {
        val listeners = mutableListOf<Pair<SensorEventListener, Sensor>>()

        fun register(type: Int, listener: SensorEventListener) {
            sensorManager.getDefaultSensor(type)?.let { sensor ->
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                listeners.add(listener to sensor)
            }
        }

        val accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        register(Sensor.TYPE_ACCELEROMETER, accelListener)

        val gyroListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        register(Sensor.TYPE_GYROSCOPE, gyroListener)

        val lightListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                lightLux = event.values[0]
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        register(Sensor.TYPE_LIGHT, lightListener)

        val proximityListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                proximityCm = event.values[0]
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        register(Sensor.TYPE_PROXIMITY, proximityListener)

        onDispose {
            listeners.forEach { (listener, _) ->
                sensorManager.unregisterListener(listener)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = HuijuiLayout.ScreenHorizontal)) {
            Spacer(modifier = Modifier.height(HuijuiLayout.DetailTopSpacer))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onBack() }
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White)
                Text("返回", color = Color.White, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("传感器测试", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "实时采样 · 摇一摇 / 遮挡测试",
                color = Color.White.copy(0.6f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(HuijuiLayout.CardSpacingNormal),
                contentPadding = HuijuiLayout.ScreenContentPadding
            ) {
                // 加速度
                item {
                    SensorAxisCard(
                        icon = Icons.Default.Speed,
                        title = "加速度",
                        color = Color(0xFFEF4444),
                        available = hasAccel,
                        xLabel = "X", yLabel = "Y", zLabel = "Z",
                        x = accelX, y = accelY, z = accelZ,
                        unit = "m/s²",
                        range = 20f,
                        hint = "摇一摇手机，数值应变化"
                    )
                }

                // 陀螺仪
                item {
                    SensorAxisCard(
                        icon = Icons.Default.ScreenRotation,
                        title = "陀螺仪",
                        color = Color(0xFF64B5F6),
                        available = hasGyro,
                        xLabel = "X", yLabel = "Y", zLabel = "Z",
                        x = gyroX, y = gyroY, z = gyroZ,
                        unit = "rad/s",
                        range = 5f,
                        hint = "旋转手机，数值应变化"
                    )
                }

                // 光线
                item {
                    SensorLightCard(
                        icon = Icons.Default.Brightness6,
                        title = "光线",
                        color = Color(0xFFFFC107),
                        available = hasLight,
                        lux = lightLux,
                        hint = "用手遮住屏幕上方，数值应下降"
                    )
                }

                // 距离
                item {
                    SensorProximityCard(
                        icon = Icons.Default.NearMe,
                        title = "距离",
                        color = Color(0xFF81C784),
                        available = hasProximity,
                        cm = proximityCm,
                        hint = "手贴近屏幕上方，数值应变小"
                    )
                }
            }
        }
    }
}

// ==========================================
// 三轴传感器卡片（加速度/陀螺仪）
// ==========================================
@Composable
private fun SensorAxisCard(
    icon: ImageVector,
    title: String,
    color: Color,
    available: Boolean,
    xLabel: String, yLabel: String, zLabel: String,
    x: Float, y: Float, z: Float,
    unit: String,
    range: Float,
    hint: String
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(HuijuiLayout.IconRadiusSmall))
                        .background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (!available) {
                        Text("设备不支持此传感器", color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                }
            }

            if (available) {
                Spacer(modifier = Modifier.height(12.dp))
                AxisRow(xLabel, x, color, unit, range)
                Spacer(modifier = Modifier.height(8.dp))
                AxisRow(yLabel, y, color, unit, range)
                Spacer(modifier = Modifier.height(8.dp))
                AxisRow(zLabel, z, color, unit, range)

                Spacer(modifier = Modifier.height(10.dp))
                Text(hint, color = Color.White.copy(0.4f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AxisRow(label: String, value: Float, color: Color, unit: String, range: Float) {
    val ratio = (kotlin.math.abs(value) / range).coerceIn(0f, 1f)
    val animated by animateFloatAsState(ratio, spring(dampingRatio = 0.7f, stiffness = 300f), label = "axis_$label")

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Color.White.copy(0.6f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(16.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "%+.2f".format(value) + " $unit",
            color = Color.White.copy(0.85f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(96.dp)
        )
    }
}

// ==========================================
// 光线传感器卡片
// ==========================================
@Composable
private fun SensorLightCard(
    icon: ImageVector,
    title: String,
    color: Color,
    available: Boolean,
    lux: Float,
    hint: String
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(HuijuiLayout.IconRadiusSmall))
                        .background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (!available) {
                        Text("设备不支持此传感器", color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                }
                if (available) {
                    Text(
                        "%.0f lux".format(lux),
                        color = color,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (available) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((lux / 2000f).coerceIn(0f, 1f))
                            .height(8.dp)
                            .background(color)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(hint, color = Color.White.copy(0.4f), fontSize = 11.sp)
            }
        }
    }
}

// ==========================================
// 距离传感器卡片
// ==========================================
@Composable
private fun SensorProximityCard(
    icon: ImageVector,
    title: String,
    color: Color,
    available: Boolean,
    cm: Float,
    hint: String
) {
    val isNear = cm < 5f

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(HuijuiLayout.IconRadiusSmall))
                        .background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (!available) {
                        Text("设备不支持此传感器", color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                }
                if (available) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%.1f cm".format(cm),
                            color = color,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            if (isNear) "近" else "远",
                            color = if (isNear) Color(0xFFEF4444) else Color(0xFF81C784),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (available) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(hint, color = Color.White.copy(0.4f), fontSize = 11.sp)
            }
        }
    }
}
