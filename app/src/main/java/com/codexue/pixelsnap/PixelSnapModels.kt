package com.codexue.pixelsnap

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MediaKind(val mimeType: String, val extension: String) {
    Photo("image/jpeg", "jpg"),
    Video("video/mp4", "mp4"),
}

data class MediaSpec(
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
    val capturedAtMillis: Long,
)

internal data class VideoDisplayDimensions(
    val width: Int,
    val height: Int,
) {
    val aspectRatio: Float
        get() = width.toFloat() / height.toFloat()
}

object PixelSnapMedia {
    const val ALBUM_NAME = "PixelSnap"
    const val FILE_PREFIX = "PixelSnap_"
    const val CAMERA_RELATIVE_DIR = "DCIM/Camera"

    fun buildSpec(kind: MediaKind, capturedAtMillis: Long): MediaSpec {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(Date(capturedAtMillis))
        return MediaSpec(
            displayName = "$FILE_PREFIX$stamp.${kind.extension}",
            mimeType = kind.mimeType,
            relativePath = CAMERA_RELATIVE_DIR,
            capturedAtMillis = capturedAtMillis,
        )
    }

    fun formatElapsed(elapsedMillis: Long): String {
        val totalSeconds = (elapsedMillis.coerceAtLeast(0L) / 1000L).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
        } else {
            "%02d:%02d".format(Locale.US, minutes, seconds)
        }
    }

    fun formatCaptureDate(capturedAtMillis: Long): String =
        SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(capturedAtMillis))
}

internal fun videoDisplayDimensionsForPreview(
    encodedWidth: Int,
    encodedHeight: Int,
    rotationDegrees: Int,
): VideoDisplayDimensions? {
    if (encodedWidth <= 0 || encodedHeight <= 0) return null
    val normalizedRotation = normalizeVideoRotationDegrees(rotationDegrees)
    return if (normalizedRotation.isQuarterTurnDegrees() && encodedWidth >= encodedHeight) {
        VideoDisplayDimensions(width = encodedHeight, height = encodedWidth)
    } else {
        VideoDisplayDimensions(width = encodedWidth, height = encodedHeight)
    }
}

internal fun videoPreviewFrameRotationDegrees(
    frameWidth: Int,
    frameHeight: Int,
    rotationDegrees: Int,
): Int {
    val normalizedRotation = normalizeVideoRotationDegrees(rotationDegrees)
    return if (normalizedRotation.isQuarterTurnDegrees() && frameWidth < frameHeight) {
        0
    } else {
        normalizedRotation
    }
}

internal fun normalizeVideoRotationDegrees(rotationDegrees: Int?): Int {
    val normalized = (((rotationDegrees ?: 0) % 360) + 360) % 360
    return when (normalized) {
        90, 180, 270 -> normalized
        else -> 0
    }
}

private fun Int.isQuarterTurnDegrees(): Boolean =
    this == 90 || this == 270
