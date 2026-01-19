/**
 * NewCameraComponents.kt - 新版相机UI组件
 *
 * 基于新的设计稿实现的相机UI组件
 *
 * @author Jules
 * @since 3.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.domain.model.CameraMode
import com.qihao.filtercamera.domain.model.AspectRatio
import com.qihao.filtercamera.domain.model.FlashMode
import com.qihao.filtercamera.domain.model.HdrMode
import com.qihao.filtercamera.domain.model.TimerMode

// Primary color from the design
val primaryColor = Color(0xFFd8ae31)

@Composable
fun NewCameraTopBar(
    flashMode: FlashMode,
    hdrMode: HdrMode,
    timerMode: TimerMode,
    aspectRatio: AspectRatio,
    onFlashClick: () -> Unit,
    onHdrClick: () -> Unit,
    onTimerClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconButton(onClick = onFlashClick) {
                Icon(
                    imageVector = when (flashMode) {
                        FlashMode.ON -> Icons.Default.FlashOn
                        FlashMode.OFF -> Icons.Default.FlashOff
                        FlashMode.AUTO -> Icons.Default.FlashAuto
                        FlashMode.TORCH -> Icons.Default.Highlight
                    },
                    contentDescription = "Flash",
                    tint = if (flashMode == FlashMode.OFF) Color.White else primaryColor
                )
            }
            Button(
                onClick = onHdrClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hdrMode == HdrMode.ON) primaryColor else Color.Transparent,
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.border(1.dp, if (hdrMode == HdrMode.ON) primaryColor else Color.White, RoundedCornerShape(4.dp))
            ) {
                Text("HDR", color = if (hdrMode == HdrMode.ON) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onTimerClick) {
                Icon(Icons.Default.Timer, contentDescription = "Timer", tint = if (timerMode != TimerMode.OFF) primaryColor else Color.White)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Button(
                onClick = onAspectRatioClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                shape = CircleShape
            ) {
                Text(aspectRatio.displayName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}

@Composable
fun NewCameraBottomControls(
    galleryThumbnail: Bitmap?,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCameraClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gallery Preview
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable(onClick = onGalleryClick),
            contentAlignment = Alignment.Center
        ) {
            if (galleryThumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = galleryThumbnail.asImageBitmap(),
                    contentDescription = "Gallery preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Shutter Button
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .border(3.5.dp, Color.White, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onShutterClick,
                modifier = Modifier
                    .size(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {}
        }

        // Switch Camera Button
        IconButton(
            onClick = onSwitchCameraClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Icon(Icons.Default.Cached, contentDescription = "Switch Camera", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun CameraModeSelector(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit
) {
    val modes = CameraMode.getAllModes()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable { onModeSelected(mode) }
            ) {
                val isSelected = mode == currentMode
                Text(
                    text = mode.displayName,
                    color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(primaryColor, CircleShape)
                    )
                }
            }
        }
    }
}
