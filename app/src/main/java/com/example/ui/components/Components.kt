package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricSage
import com.example.ui.theme.IndigoSpeaker
import com.example.ui.theme.RoseSpeaker
import com.example.ui.theme.TealSpeaker
import kotlin.random.Random

@Composable
fun AudioWaveformVisualizer(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 36
) {
    val animatedAmp by animateFloatAsState(
        targetValue = if (isRecording) amplitude.coerceIn(0.08f, 1.0f) else 0.05f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "amplitude_animation"
    )

    // Pre-calculate randomized seed factors for pseudo-spectral shape
    val seedFactors = remember {
        List(barCount) { index ->
            val centerWeight = 1f - Math.abs((index - barCount / 2f) / (barCount / 2f))
            (0.3f + 0.7f * centerWeight) * (0.6f + 0.4f * Random.nextFloat())
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .testTag("waveform_visualizer")
    ) {
        val width = size.width
        val height = size.height
        val barWidth = (width / barCount) * 0.62f
        val spacing = (width - (barCount * barWidth)) / (barCount - 1)

        val brush = Brush.verticalGradient(
            colors = listOf(primaryColor, secondaryColor)
        )

        for (i in 0 until barCount) {
            val factor = seedFactors[i]
            val barHeight = (height * 0.85f * animatedAmp * factor)
                .coerceAtLeast(6f)
                .coerceAtMost(height)

            val x = i * (barWidth + spacing)
            val y = (height - barHeight) / 2f

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun SpeakerBadge(
    speakerName: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when {
        speakerName.contains("1") -> Pair(AcousticAmber.copy(alpha = 0.2f), AcousticAmber)
        speakerName.contains("2") -> Pair(ElectricSage.copy(alpha = 0.2f), ElectricSage)
        speakerName.contains("3") -> Pair(IndigoSpeaker.copy(alpha = 0.2f), IndigoSpeaker)
        speakerName.contains("4") -> Pair(TealSpeaker.copy(alpha = 0.2f), TealSpeaker)
        else -> Pair(RoseSpeaker.copy(alpha = 0.2f), RoseSpeaker)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier.testTag("speaker_badge_${speakerName.replace(" ", "_")}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(textColor, CircleShape)
            )
            Text(
                text = speakerName,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
fun TimecodeChip(
    timeMs: Long,
    modifier: Modifier = Modifier
) {
    val seconds = (timeMs / 1000) % 60
    val minutes = (timeMs / 1000) / 60
    val formatted = String.format("%02d:%02d", minutes, seconds)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.testTag("timecode_chip")
    ) {
        Text(
            text = formatted,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (status) {
        "RECORDING" -> Pair("MEREKAM", CrimsonAlert)
        "PAUSED" -> Pair("JEDA", AcousticAmber)
        "RECORDED" -> Pair("MENUNGGU TRANSKRIPSI", AcousticAmber)
        "TRANSCRIBING" -> Pair("MENTRANSKRIP", AcousticAmber)
        "ORGANIZING" -> Pair("MENATA DOKUMEN", ElectricSage)
        "COMPLETED" -> Pair("SELESAI", ElectricSage)
        "ERROR" -> Pair("GAGAL TRANSKRIPSI", CrimsonAlert)
        else -> Pair(status, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            if (status == "RECORDING") {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CrimsonAlert, CircleShape)
                )
                Text(
                    text = label,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp)
                )
            } else {
                Text(
                    text = label,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
