package com.example.service

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Simple, stark, dark countdown overlay Composable containing zero graphics or quotes.
 */
@Composable
fun CountdownOverlayContent(
    packageName: String,
    initialSeconds: Int,
    onFinished: (Int) -> Unit,
    onGoHome: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(initialSeconds) }
    var showTimeSelection by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val pm = context.packageManager
    
    val appInfo = remember(packageName) {
        try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    
    val appLabel = remember(appInfo) {
        appInfo?.loadLabel(pm)?.toString() ?: packageName
    }
    
    val appIcon = remember(appInfo) {
        try {
            val drawable = appInfo?.loadIcon(pm)
            drawable?.let { 
                val bitmap = android.graphics.Bitmap.createBitmap(
                    it.intrinsicWidth.coerceAtLeast(1), 
                    it.intrinsicHeight.coerceAtLeast(1), 
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                it.setBounds(0, 0, canvas.width, canvas.height)
                it.draw(canvas)
                bitmap.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        showTimeSelection = true
    }

    // Breathing animation for the ring
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.98f)), // Near solid black
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight().padding(24.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            if (!showTimeSelection) {
                // App Context Header
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Text(
                    text = "Opening $appLabel",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6E1E5).copy(alpha = 0.8f)
                )
                Text(
                    text = "Take a mindful breath",
                    fontSize = 14.sp,
                    color = Color(0xFFE6E1E5).copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Breathing Ring & Timer
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(scale)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFFD0BCFF).copy(alpha = 0.1f))
                    )
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFFD0BCFF).copy(alpha = 0.05f))
                    )
                    Text(
                        text = secondsLeft.toString(),
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFD0BCFF)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = onGoHome,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE6E1E5)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6E1E5).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Nevermind, go home",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            } else {
                // Post-Countdown Options
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = "How long do you need in $appLabel?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6E1E5),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.weight(1f))

                val options = listOf(
                    "1 min" to 1,
                    "5 min" to 5,
                    "15 min" to 15,
                    "30 min" to 30
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    options.forEach { (label, minutes) ->
                        FilledTonalButton(
                            onClick = { onFinished(minutes) },
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF49454F),
                                contentColor = Color(0xFFD0BCFF)
                            )
                        ) {
                            Text(text = label, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    TextButton(onClick = onGoHome) {
                        Text(
                            text = "Nevermind, go home",
                            color = Color(0xFFE6E1E5).copy(alpha = 0.6f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
