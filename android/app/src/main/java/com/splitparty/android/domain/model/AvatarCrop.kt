package com.splitparty.android.domain.model

/**
 * Square crop of a photo, in fractions of the source image: the square's centre
 * ([centerX] of the width, [centerY] of the height) and its [side] as a fraction of
 * the image's shorter edge. Clamped to the image when applied. The numbers for the
 * seeded people are shared with iOS (docs/design/visual-conventions.md) so both
 * phones show the same face framing.
 */
data class AvatarCrop(val centerX: Float, val centerY: Float, val side: Float) {
    companion object {
        /** The largest centred square: used for photos that have no tuned crop. */
        val Centered = AvatarCrop(0.5f, 0.5f, 1f)
    }
}
