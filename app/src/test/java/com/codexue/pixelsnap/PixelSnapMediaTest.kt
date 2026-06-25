package com.codexue.pixelsnap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelSnapMediaTest {
    @Test
    fun buildSpecCreatesGalleryPhotoPathAndName() {
        val spec = PixelSnapMedia.buildSpec(MediaKind.Photo, 1_719_835_200_123L)

        assertEquals("image/jpeg", spec.mimeType)
        assertEquals("DCIM/Camera", spec.relativePath)
        assertTrue(spec.displayName.matches(Regex("""PixelSnap_\d{8}_\d{6}_\d{3}\.jpg""")))
    }

    @Test
    fun buildSpecCreatesGalleryVideoPathAndName() {
        val spec = PixelSnapMedia.buildSpec(MediaKind.Video, 1_719_835_200_123L)

        assertEquals("video/mp4", spec.mimeType)
        assertEquals("DCIM/Camera", spec.relativePath)
        assertTrue(spec.displayName.matches(Regex("""PixelSnap_\d{8}_\d{6}_\d{3}\.mp4""")))
    }

    @Test
    fun formatElapsedUsesMinuteAndHourForms() {
        assertEquals("00:00", PixelSnapMedia.formatElapsed(-500L))
        assertEquals("01:05", PixelSnapMedia.formatElapsed(65_400L))
        assertEquals("1:02:03", PixelSnapMedia.formatElapsed(3_723_900L))
    }

    @Test
    fun formatCaptureDateUsesWatermarkDateForm() {
        val formatted = PixelSnapMedia.formatCaptureDate(1_719_835_200_123L)

        assertTrue(formatted.matches(Regex("""\d{4}\.\d{2}\.\d{2}""")))
    }

    @Test
    fun videoDisplayDimensionsUsesPortraitShapeWhenLandscapeEncodedVideoHasRotation() {
        val dimensions = videoDisplayDimensionsForPreview(
            encodedWidth = 1920,
            encodedHeight = 1080,
            rotationDegrees = 90,
        )

        assertEquals(1080, dimensions?.width)
        assertEquals(1920, dimensions?.height)
        assertEquals(9f / 16f, dimensions?.aspectRatio ?: 0f, 0.001f)
    }

    @Test
    fun videoDisplayDimensionsKeepsAlreadyPortraitQuarterTurnMetadata() {
        val dimensions = videoDisplayDimensionsForPreview(
            encodedWidth = 1080,
            encodedHeight = 1920,
            rotationDegrees = 90,
        )

        assertEquals(1080, dimensions?.width)
        assertEquals(1920, dimensions?.height)
        assertEquals(9f / 16f, dimensions?.aspectRatio ?: 0f, 0.001f)
    }

    @Test
    fun videoPreviewFrameRotationAvoidsDoubleRotatingPortraitFrames() {
        assertEquals(90, videoPreviewFrameRotationDegrees(1920, 1080, 90))
        assertEquals(0, videoPreviewFrameRotationDegrees(1080, 1920, 90))
        assertEquals(180, videoPreviewFrameRotationDegrees(1080, 1920, 180))
    }
}
