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
}
