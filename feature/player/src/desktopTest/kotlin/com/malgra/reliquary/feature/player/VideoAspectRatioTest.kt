package com.malgra.reliquary.feature.player

import com.malgra.reliquary.core.model.VideoType
import org.junit.Test
import kotlin.test.assertEquals

class VideoAspectRatioTest {

    @Test fun guessesFromTheTypeUntilThePictureIsKnown() {
        assertEquals(9f / 16f, videoAspectRatio(VideoSize.Unknown, VideoType.Shorts))
        assertEquals(16f / 9f, videoAspectRatio(VideoSize.Unknown, VideoType.Videos))
        assertEquals(16f / 9f, videoAspectRatio(VideoSize.Unknown, null))
    }

    // A Short can be square, and a regular video can be upright.
    @Test fun thePictureWinsOverTheType() {
        assertEquals(1f, videoAspectRatio(VideoSize(1080, 1080), VideoType.Shorts))
        assertEquals(0.5625f, videoAspectRatio(VideoSize(1080, 1920), VideoType.Videos))
    }

    @Test fun followsNonSquarePixels() {
        assertEquals(16f / 9f, videoAspectRatio(VideoSize(1440, 1080, pixelRatio = 4f / 3f), VideoType.Videos), 0.001f)
    }
}
